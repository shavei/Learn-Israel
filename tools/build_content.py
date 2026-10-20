#!/usr/bin/env python3
"""Builds the app's map and place assets from open data.

Inputs:
  content/places.yaml                    hand-curated curriculum (what is taught)
  content/regions.yaml                   which official natural regions make up each game region
  content/natural_regions.geojson        official natural regions (CBS; Ministry of Agriculture for
                                         Judea and Samaria): the land's outline, the lakes'
                                         Israeli shores, the regions and the districts
  content/localities.csv                 CBS locality file: official city points, by locality symbol
  content/jordan_river.geojson           the Jordan River from OpenStreetMap (ODbL)
  content/region_overrides.geojson       OSM areas moved between regions (see regions.yaml)
  content/isohyets.geojson               rainfall lines for climate splits (see regions.yaml)
  content/surroundings.geojson           OSM land (from the coastline), the neighboring
                                         countries and the Dead Sea (ODbL)
  content/palestine.geojson              Gaza and the West Bank, UN OCHA (CC BY-IGO); the
                                         West Bank's western edge is the Green Line
  (the content files above are refreshed by tools/fetch_sources.py)
  GeoNames cities via the geonamescache package (CC BY 4.0)

Outputs (app/src/main/assets/):
  map.json     background layers (neighbors, land, Gaza, lakes, rivers, region
               outlines, Green Line)
  places.json  learnable places with Hebrew text and geometry

All coordinates are projected to the Israeli Transverse Mercator grid (EPSG:2039)
and rounded to whole meters, so the app can measure distances with plain geometry.

Usage:  pip install -r tools/requirements.txt && python3 tools/build_content.py
"""

from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

import geonamescache
import numpy as np
import shapely
import yaml
from pyproj import Transformer
from shapely import ops
from shapely.geometry import (
    GeometryCollection,
    LineString,
    MultiLineString,
    MultiPolygon,
    Point,
    Polygon,
    box,
    shape,
)

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"

# Map window in lon/lat. Everything is clipped to it.
WINDOW = box(32.6, 28.4, 37.2, 34.2)
NEIGHBORS = ["LBN", "SYR", "JOR", "EGY", "SAU"]

# Simplification tolerance in meters, per layer.
SIMPLIFY = {
    "land": 40, "neighbors": 120, "lakes": 30, "rivers": 60, "seas": 120, "green_line": 40, "regions": 150,
}

LAKE_CODE = 99  # the Kinneret and the Dead Sea in the official natural regions
JUDEA_SAMARIA_CODES = range(700, 900)
# District keys and the first digit of their natural region codes (and CBS district numbers).
DISTRICTS = {
    "jerusalem": (1,), "north": (2,), "haifa": (3,), "center": (4,), "tel_aviv": (5,), "south": (6,),
    "judea_samaria": (7, 8),
}
DISTRICT_OF_CBS_NUMBER = {1: "jerusalem", 2: "north", 3: "haifa", 4: "center", 5: "tel_aviv", 6: "south", 7: "judea_samaria"}

# Land left outside every region or district, in square meters, before the build fails.
MAX_UNASSIGNED_M2 = 1e6

TO_ITM = Transformer.from_crs("EPSG:4326", "EPSG:2039", always_xy=True)


def project(geom):
    return shapely.transform(geom, lambda xy: list(zip(*TO_ITM.transform(xy[:, 0], xy[:, 1]))))


def clean(geom, tolerance):
    """Projects to ITM, simplifies, and drops slivers."""
    geom = project(geom).simplify(tolerance, preserve_topology=True)
    if geom.geom_type in ("Polygon", "MultiPolygon"):
        geom = geom.buffer(0)
    return geom


def smooth(geom_itm, tolerance):
    """Simplifies a geometry already on the Israeli TM grid, and drops slivers."""
    return geom_itm.simplify(tolerance, preserve_topology=True).buffer(0)


def polygons(geom) -> list[Polygon]:
    if geom.is_empty:
        return []
    if isinstance(geom, Polygon):
        return [geom]
    if isinstance(geom, (MultiPolygon, GeometryCollection)):
        return [p for g in geom.geoms for p in polygons(g)]
    return []


def lines(geom) -> list[LineString]:
    if geom.is_empty:
        return []
    if isinstance(geom, LineString):
        return [geom]
    if isinstance(geom, (MultiLineString, GeometryCollection)):
        return [l for g in geom.geoms for l in lines(g)]
    return []


def flat(coords) -> list[int]:
    return [round(v) for xy in coords for v in xy[:2]]


def encode_polygons(geom, min_area=2e5) -> list[list[list[int]]]:
    """[[outer ring, hole, ...], ...] with rings as flat [x0, y0, x1, y1, ...]."""
    return [
        [flat(p.exterior.coords)] + [flat(i.coords) for i in p.interiors if Polygon(i).area >= min_area]
        for p in polygons(geom)
        if p.area >= min_area
    ]


def encode_lines(geom) -> list[list[int]]:
    return [flat(l.coords) for l in lines(geom) if len(l.coords) >= 2]


def partition(area, shapes: dict, min_part=2e5) -> tuple[dict, object]:
    """Splits [area] among the shapes in order: each takes what earlier ones left.

    Parts smaller than [min_part] square meters are slivers where a shape grazes a
    neighbor; they are left for later shapes. Returns the pieces and whatever no
    shape covered.
    """
    rest = area
    pieces = {}
    for key, s in shapes.items():
        piece = rest.intersection(s)
        pieces[key] = ops.unary_union([p for p in polygons(piece) if p.area >= min_part])
        rest = rest.difference(pieces[key])
    return pieces, rest


def fill(land_area, shapes: dict, start: dict | None = None) -> tuple[dict, object]:
    """Splits an area among shapes that cover it with small gaps.

    After the exact split, the leftover strips (where two official layers meet, or
    where simplification shifted an edge) go to the nearest shape: the shapes grow step
    by step into what is left.
    Returns the areas and the land still left over. With [start], the areas grow from
    those shapes instead (for filling gaps between areas that lie outside [land_area]).
    """
    exact, rest = partition(land_area, shapes)
    grown = {k: ops.unary_union([exact[k], start[k]]) for k in exact} if start else dict(exact)
    for step in (100, 250, 500, 1_000, 2_000, 4_000, 8_000):
        if rest.is_empty:
            break
        taken, rest = partition(rest, {k: grown[k].buffer(step) for k in grown}, min_part=0)
        grown = {k: ops.unary_union([grown[k], taken[k]]) for k in grown}
    return grown, rest


def official_regions() -> dict[int, tuple[object, str]]:
    """The official natural regions by code, in lon/lat, with their names."""
    features = json.loads((ROOT / "content" / "natural_regions.geojson").read_text())["features"]
    return {f["properties"]["code"]: (shape(f["geometry"]), f["properties"]["name"]) for f in features}


def close_gaps(geom):
    """Closes the slivers and enclaves where two official layers meet."""
    geom = geom.buffer(0.0003).buffer(-0.0003)
    return ops.unary_union([Polygon(p.exterior) for p in polygons(geom)])


def armistice_line(west_bank, jordan, dead_sea):
    """The Green Line: the West Bank's edge (UN OCHA) where it faces Israel.

    Its eastern edge, along the Jordan River and the Dead Sea, is left out.
    """
    line = west_bank.boundary.difference(jordan.buffer(0.01)).difference(dead_sea.buffer(0.01))
    line = clean(line, SIMPLIFY["green_line"])
    return ops.linemerge(lines(line)) if not line.is_empty else line


def natural_regions(land_area, official) -> tuple[dict, object, list[str]]:
    """The game's natural regions: unions of the official natural regions (by code)."""
    groups = yaml.safe_load((ROOT / "content" / "regions.yaml").read_text())["regions"]
    used = [c for codes in groups.values() for c in codes]
    errors = [f"natural region code {c} is used twice in regions.yaml" for c in set(used) if used.count(c) > 1]
    errors += [f"natural region code {c} is not in regions.yaml" for c in official if c not in used and c != LAKE_CODE]
    errors += [f"regions.yaml: unknown natural region code {c}" for c in used if c not in official]
    shapes = {
        k: project(ops.unary_union([official[c][0] for c in codes if c in official])) for k, codes in groups.items()
    }
    for target, area in region_moves(official):
        shapes = {k: g.union(area) if k == target else g.difference(area) for k, g in shapes.items()}
    pieces, rest = fill(land_area, shapes)
    return simplify_together(pieces), rest, errors


def region_moves(official) -> list[tuple[str, object]]:
    """Areas that move to another game region than their official code says, on the
    Israeli TM grid: the climate splits and the OSM overrides of regions.yaml. Later
    moves win, so the overrides come last.
    """
    config = yaml.safe_load((ROOT / "content" / "regions.yaml").read_text())
    moves = []
    isohyets = json.loads((ROOT / "content" / "isohyets.geojson").read_text())["features"]
    lines_by_mm = {f["properties"]["rainfall_mm"]: shape(f["geometry"]) for f in isohyets}
    for target, split in config.get("climate_splits", {}).items():
        dry = drier_side(lines_by_mm[split["isohyet_mm"]])
        source = ops.unary_union([official[c][0] for c in split["from_codes"]])
        moves.append((target, project(source.intersection(dry))))
    for f in json.loads((ROOT / "content" / "region_overrides.geojson").read_text())["features"]:
        moves.append((f["properties"]["region"], project(shape(f["geometry"]))))
    return moves


def drier_side(isohyet):
    """The part of the map window on the dry side of a rainfall line (toward the Dead Sea).

    The line is extended straight out from both ends past the window's edge, so it
    cuts the window in two.
    """
    coords = list(isohyet.coords)

    def extend(a, b):  # from b, away from a
        dx, dy = b[0] - a[0], b[1] - a[1]
        n = (dx * dx + dy * dy) ** 0.5
        return (b[0] + dx / n * 10, b[1] + dy / n * 10)

    line = LineString([extend(coords[1], coords[0])] + coords + [extend(coords[-2], coords[-1])])
    sides = list(ops.polygonize(ops.unary_union([WINDOW.boundary, line.intersection(WINDOW)])))
    dead_sea = Point(35.5, 31.5)
    return next(p for p in sides if p.contains(dead_sea))


def simplify_together(pieces: dict) -> dict:
    """Simplifies areas that tile the land as one coverage, so neighbors keep one shared border."""
    keys = list(pieces)
    simplified = shapely.coverage_simplify([pieces[k] for k in keys], SIMPLIFY["regions"])
    return {k: g.buffer(0) for k, g in zip(keys, simplified)}


def districts(land_area, official) -> tuple[dict, object]:
    """The six districts and the Judea and Samaria Area.

    Every official natural region lies in one district, and its code starts with the
    district's number, so the districts are unions of natural regions.
    """
    shapes = {}
    for key, digits in DISTRICTS.items():
        shapes[key] = project(ops.unary_union([g for c, (g, _) in official.items() if c // 100 in digits]))
    pieces, rest = fill(land_area, shapes)
    return simplify_together(pieces), rest


def outlines(pieces: dict, land_area) -> list:
    """Borders shared by neighboring areas, without the coast and the outer borders."""
    edges = ops.unary_union([g.boundary for g in pieces.values()])
    inner = edges.difference(land_area.boundary.buffer(150))
    return ops.linemerge(lines(inner)) if not inner.is_empty else inner


# Contour lines: every CONTOUR_INTERVAL meters, every CONTOUR_INDEX meters an index contour.
CONTOUR_INTERVAL = 100
CONTOUR_INDEX = 500
CONTOUR_MIN_LENGTH = 1500  # meters; shorter loops are noise at this grid size
CONTOUR_SIMPLIFY = 30


def blurred(z):
    """A light binomial blur, so contours on a 300 m grid read as smooth cartographic lines."""

    k = np.array([1, 4, 6, 4, 1], float) / 16
    p = np.pad(z, ((2, 2), (0, 0)), mode="edge")
    z = sum(k[i] * p[i:i + z.shape[0]] for i in range(5))
    p = np.pad(z, ((0, 0), (2, 2)), mode="edge")
    return sum(k[i] * p[:, i:i + z.shape[1]] for i in range(5))


# Marching squares: corner bits are top-left 8, top-right 4, bottom-right 2, bottom-left 1.
# Cell edges: 0 top, 1 bottom, 2 left, 3 right. Saddles (5, 10) are resolved by the cell's mean.
_SEGMENTS = {
    1: [(2, 1)], 2: [(1, 3)], 3: [(2, 3)], 4: [(0, 3)], 6: [(0, 1)], 7: [(2, 0)],
    8: [(2, 0)], 9: [(0, 1)], 11: [(0, 3)], 12: [(2, 3)], 13: [(1, 3)], 14: [(2, 1)],
}


def contour_lines(z, level) -> list[list[tuple[float, float]]]:
    """Lines where grid z crosses level, in grid coordinates (column, row)."""

    above = z > level
    case = above[:-1, :-1] * 8 + above[:-1, 1:] * 4 + above[1:, 1:] * 2 + above[1:, :-1] * 1
    rows, cols = np.nonzero((case != 0) & (case != 15))
    segments = []
    for r, c, kind in zip(rows.tolist(), cols.tolist(), case[rows, cols].tolist()):
        pairs = _SEGMENTS.get(kind)
        if pairs is None:
            high = (z[r, c] + z[r, c + 1] + z[r + 1, c] + z[r + 1, c + 1]) / 4 > level
            if kind == 5:
                pairs = [(2, 1), (0, 3)] if high else [(2, 0), (1, 3)]
            else:
                pairs = [(2, 0), (1, 3)] if high else [(0, 3), (2, 1)]
        edges = (("h", r, c), ("h", r + 1, c), ("v", r, c), ("v", r, c + 1))
        segments += [(edges[a], edges[b]) for a, b in pairs]

    at_edge: dict = {}
    for i, (a, b) in enumerate(segments):
        at_edge.setdefault(a, []).append(i)
        at_edge.setdefault(b, []).append(i)
    used = [False] * len(segments)
    chains = []
    for i, (a, b) in enumerate(segments):
        if used[i]:
            continue
        used[i] = True
        chain = [a, b]
        for _ in range(2):  # grow from one end, then turn around and grow from the other
            while True:
                nxt = [j for j in at_edge[chain[-1]] if not used[j]]
                if not nxt:
                    break
                used[nxt[0]] = True
                p, q = segments[nxt[0]]
                chain.append(q if p == chain[-1] else p)
            chain.reverse()
        chains.append(chain)

    def point(edge):
        kind, r, c = edge
        if kind == "h":
            z0, z1 = z[r, c], z[r, c + 1]
            return c + (level - z0) / (z1 - z0), r
        z0, z1 = z[r, c], z[r + 1, c]
        return c, r + (level - z0) / (z1 - z0)

    return [[point(e) for e in chain] for chain in chains]


def chaikin(coords, closed):
    """One round of corner cutting: rounds the marching squares' corners."""
    out = [coords[0]] if not closed else []
    pairs = list(zip(coords, coords[1:]))
    for (x0, y0), (x1, y1) in pairs:
        out += [(0.75 * x0 + 0.25 * x1, 0.75 * y0 + 0.25 * y1), (0.25 * x0 + 0.75 * x1, 0.25 * y0 + 0.75 * y1)]
    out += [out[0]] if closed else [coords[-1]]
    return out


def contours(meta) -> dict:
    """Contour lines from the elevation grid, in ITM meters, for the topographic map style."""

    z = np.fromfile(ROOT / "content" / "elevation.bin", "<i2").astype(np.float64)
    z = blurred(z.reshape(meta["height"], meta["width"]))
    cell, minx, maxy = meta["cell"], meta["minX"], meta["maxY"]
    first = int(np.ceil(z.min() / CONTOUR_INTERVAL)) * CONTOUR_INTERVAL
    levels = []
    for level in range(first, int(z.max()) + 1, CONTOUR_INTERVAL):
        if level == 0:
            continue  # the coastline is drawn from the land outline
        lines = []
        for line in contour_lines(z, level):
            closed = line[0] == line[-1]
            coords = [(minx + (x + 0.5) * cell, maxy - (y + 0.5) * cell) for x, y in line]
            geom = LineString(coords)
            if geom.length < CONTOUR_MIN_LENGTH:
                continue
            for _ in range(2):
                coords = chaikin(coords, closed)
            lines.append(flat(LineString(coords).simplify(CONTOUR_SIMPLIFY).coords))
        levels.append({"elevation": level, "lines": lines})
    return {"version": 1, "interval": CONTOUR_INTERVAL, "index": CONTOUR_INDEX, "levels": levels}


def main() -> int:
    official = official_regions()
    around = json.loads((ROOT / "content" / "surroundings.geojson").read_text())["features"]
    osm_land = ops.unary_union([shape(f["geometry"]) for f in around if f["properties"]["kind"] == "land"])
    countries = {f["properties"]["country"]: shape(f["geometry"]) for f in around if f["properties"]["kind"] == "country"}
    osm_dead_sea = ops.unary_union([shape(f["geometry"]) for f in around if f["properties"]["kind"] == "dead_sea"])
    palestine = json.loads((ROOT / "content" / "palestine.geojson").read_text())["features"]
    ocha = {f["properties"]["area"]: shape(f["geometry"]) for f in palestine}

    # Main map = de facto lines of control: Israel plus Judea and Samaria as one land
    # area, with the official outline (the natural regions follow Israel's official
    # borders and coast). Gaza is drawn separately. The Green Line is an optional
    # overlay (decision in doc 10).
    land_ll = close_gaps(ops.unary_union([g for g, _ in official.values()])).intersection(WINDOW)
    land = clean(land_ll, SIMPLIFY["land"])

    # Lakes: the official Kinneret; the Dead Sea's northern basin from OSM (both
    # halves) with the official southern ponds.
    official_lakes = [p for p in polygons(official[LAKE_CODE][0]) if p.area > 1e-4]
    lake_geoms = {
        "Sea of Galilee": ops.unary_union([p for p in official_lakes if p.centroid.y > 32.5]),
        "Dead Sea": ops.unary_union([p for p in official_lakes if p.centroid.y < 32.5] + [osm_dead_sea]),
    }
    lakes_itm = ops.unary_union([clean(g, SIMPLIFY["lakes"]) for g in lake_geoms.values()])

    # Around Israel: OSM's land (built from the coastline), split among the neighbors
    # by their OSM boundaries, and Gaza by the UN OCHA boundary. Where OSM's land and
    # Israel's official outline disagree, the strip goes to whatever lies next to it:
    # a neighbor, Gaza or the sea.
    outside = {a3: countries[a3].intersection(osm_land) for a3 in NEIGHBORS}
    outside["gaza"] = ocha["gaza"].intersection(osm_land)
    outside["sea"] = WINDOW.difference(osm_land)
    outside = {k: project(g.intersection(WINDOW).difference(land_ll)) for k, g in outside.items()}
    claimed = ops.unary_union([land_ll, ocha["gaza"]] + list(countries.values()))
    orphans = project(osm_land.intersection(WINDOW).difference(claimed))
    outside, _ = fill(orphans, outside, start=outside)
    water = outside.pop("sea")
    gaza_itm = smooth(outside.pop("gaza"), SIMPLIFY["land"])
    neighbor_geoms = {a3: smooth(g, SIMPLIFY["neighbors"]) for a3, g in outside.items()}

    green_line = armistice_line(ocha["west_bank"], countries["JOR"], osm_dead_sea)

    rivers = json.loads((ROOT / "content" / "jordan_river.geojson").read_text())["features"]
    river_geoms = {"Jordan": ops.unary_union([shape(f["geometry"]) for f in rivers]).intersection(WINDOW)}

    # Regions and districts split the land; the lakes are not part of any of them.
    land_area = land.difference(lakes_itm)
    region_geoms, region_rest, region_errors = natural_regions(land_area, official)
    district_geoms, district_rest = districts(land_area, official)

    gn = geonamescache.GeonamesCache(min_city_population=15000).get_cities()
    with (ROOT / "content" / "localities.csv").open(encoding="utf-8") as f:
        localities = {row["symbol"]: row for row in csv.DictReader(f)}
    groups = yaml.safe_load((ROOT / "content" / "regions.yaml").read_text())["regions"]
    region_of_code = {code: key for key, codes in groups.items() for code in codes}
    moves = region_moves(official)

    # Labels for areas sit inside the part of the area near Israel, so they stay on screen.
    near_israel = project(ops.unary_union([land_ll, ocha["gaza"]])).buffer(40_000).envelope

    def area_label(geom_itm):
        part = geom_itm.intersection(near_israel)
        part = max(polygons(part) or polygons(geom_itm), key=lambda g: g.area)
        return ops.polylabel(part, tolerance=200)

    def line_label(geom_itm):
        longest = max(lines(geom_itm), key=lambda l: l.length)
        return longest.interpolate(0.5, normalized=True)

    # ---------- places ----------
    curriculum = yaml.safe_load((ROOT / "content" / "places.yaml").read_text())["places"]
    places = []
    errors = list(region_errors)
    for what, rest in (("natural regions", region_rest), ("districts", district_rest)):
        if rest.area > MAX_UNASSIGNED_M2:
            spots = ", ".join(f"{p.centroid.x:.0f},{p.centroid.y:.0f}" for p in polygons(rest) if p.area > 1e5)
            errors.append(f"{what} leave {rest.area / 1e6:.1f} km2 of land uncovered, around ITM {spots}")
    ids = [p["id"] for p in curriculum]
    # Confusable pairs are symmetric: listing B under A also lists A under B.
    confusables: dict[str, set[str]] = {}
    for p in curriculum:
        for other in p.get("confusables", []):
            if other not in ids:
                errors.append(f"{p['id']}: unknown confusable {other}")
            confusables.setdefault(p["id"], set()).add(other)
            confusables.setdefault(other, set()).add(p["id"])
    for p in curriculum:
        kind, _, arg = p["geometry"].partition(":")
        geometry = {}
        region = None
        if kind in ("cbs", "geonames", "point"):
            if kind == "cbs":
                loc = localities.get(arg)
                if loc is None:
                    errors.append(f"{p['id']}: no CBS locality with symbol {arg}")
                    continue
                pt = Point(int(loc["x"]), int(loc["y"]))  # already on the Israeli TM grid
            elif kind == "geonames":
                city = gn.get(arg)
                if city is None:
                    errors.append(f"{p['id']}: geonames id {arg} not found")
                    continue
                lon, lat = city["longitude"], city["latitude"]
            else:
                lon, lat = map(float, arg.split(","))
            if kind != "cbs":
                pt = project(Point(lon, lat))
            geometry["point"] = [round(pt.x), round(pt.y)]
            label = pt
            if not land.buffer(300).contains(pt):
                errors.append(f"{p['id']}: point is outside the land area")
            # The natural region it lies in (nearest one for points on the coast).
            region = min(region_geoms, key=lambda k: region_geoms[k].distance(pt))
            # The CBS assigns each locality a natural region; the map must agree.
            code = int(loc["natural_region"]) if kind == "cbs" and loc["natural_region"] else None
            expected = next((t for t, area in reversed(moves) if area.contains(pt)), region_of_code.get(code))
            if code is not None and region != expected:
                errors.append(f"{p['id']}: lies in {region}, but the CBS puts it in natural region {code}")
            if kind == "cbs" and loc["district"]:
                district = DISTRICT_OF_CBS_NUMBER.get(int(loc["district"]))
                if district is None or not district_geoms[district].buffer(300).contains(pt):
                    errors.append(f"{p['id']}: not in the {district} district, where the CBS puts it")
        elif kind in ("region", "district"):
            geoms = region_geoms if kind == "region" else district_geoms
            if arg not in geoms or geoms[arg].is_empty:
                errors.append(f"{p['id']}: no {kind} named {arg}")
                continue
            geometry["polygons"] = encode_polygons(geoms[arg])
            label = area_label(geoms[arg])
        elif kind == "country":
            geometry["polygons"] = encode_polygons(neighbor_geoms[arg])
            label = area_label(neighbor_geoms[arg])
        elif kind == "sea":
            seed = project(Point(*map(float, arg.split(","))))
            parts = [g for g in polygons(water) if g.contains(seed)]
            if not parts:
                errors.append(f"{p['id']}: seed {arg} is not in open water")
                continue
            geometry["polygons"] = encode_polygons(smooth(parts[0], SIMPLIFY["seas"]))
            label = seed
        elif kind == "lake":
            lake = clean(lake_geoms[arg], SIMPLIFY["lakes"])
            geometry["polygons"] = encode_polygons(lake, min_area=1e4)
            label = area_label(lake)
        elif kind == "river":
            river = clean(river_geoms[arg], SIMPLIFY["rivers"])
            geometry["lines"] = encode_lines(river)
            label = line_label(river)
        else:
            errors.append(f"{p['id']}: unknown geometry source {p['geometry']}")
            continue
        if not any(geometry.values()):
            errors.append(f"{p['id']}: empty geometry")
        place = {
            "id": p["id"],
            "type": p["type"],
            "unit": p["unit"],
            "importance": p["importance"],
            "name": p["name"],
            "hint": p["hint"],
            "fact": p["fact"],
            "geometry": geometry,
            "label": [round(label.x), round(label.y)],
            "aliases": p.get("aliases", []),
            "confusables": sorted(confusables.get(p["id"], set())),
        }
        if region is not None:
            place["region"] = region
        places.append(place)

    if len(ids) != len(set(ids)):
        errors.append("duplicate place ids")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1

    # ---------- map layers ----------
    israel_view = project(ops.unary_union([land_ll, ocha["gaza"]]))
    minx, miny, maxx, maxy = israel_view.bounds
    window_itm = project(WINDOW)
    elevation = json.loads((ROOT / "content" / "elevation.json").read_text())
    if [elevation["minX"], elevation["maxY"]] != [round(window_itm.bounds[0]), round(window_itm.bounds[3])]:
        print("content/elevation.json does not match the map window: rerun tools/fetch_sources.py", file=sys.stderr)
        return 1
    map_json = {
        "version": 1,
        "crs": "EPSG:2039",
        "window": [round(v) for v in window_itm.bounds],
        "home": [round(minx), round(miny), round(maxx), round(maxy)],
        "layers": [
            {"id": "neighbors", "polygons": [r for g in neighbor_geoms.values() for r in encode_polygons(g)]},
            {"id": "gaza", "polygons": encode_polygons(gaza_itm)},
            {"id": "land", "polygons": encode_polygons(land)},
            {"id": "rivers", "lines": encode_lines(clean(river_geoms["Jordan"], SIMPLIFY["rivers"]))},
            {"id": "lakes", "polygons": [
                r for g in lake_geoms.values() for r in encode_polygons(clean(g, SIMPLIFY["lakes"]), min_area=1e4)
            ]},
            {"id": "regions", "lines": encode_lines(outlines(region_geoms, land_area))},
            {"id": "green_line", "lines": encode_lines(green_line)},
        ],
        # Ground height for the relief style, in elevation.bin (copied from content/).
        "elevation": {k: elevation[k] for k in ("minX", "maxY", "cell", "width", "height")},
        "attribution": "Israel Central Bureau of Statistics; Ministry of Agriculture; UN OCHA; "
                       "GeoNames (CC BY 4.0); © OpenStreetMap contributors (ODbL); "
                       "Copernicus DEM © DLR e.V., © Airbus Defence and Space GmbH, "
                       "provided under COPERNICUS by the European Union and ESA",
    }

    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "map.json").write_text(json.dumps(map_json, separators=(",", ":")))
    (ASSETS / "places.json").write_text(
        json.dumps({"version": 1, "places": places}, ensure_ascii=False, separators=(",", ":"))
    )
    (ASSETS / "elevation.bin").write_bytes((ROOT / "content" / "elevation.bin").read_bytes())
    (ASSETS / "contours.json").write_text(json.dumps(contours(elevation), separators=(",", ":")))
    for f in ("map.json", "places.json", "elevation.bin", "contours.json"):
        print(f"{f}: {(ASSETS / f).stat().st_size // 1024} KB")
    print(f"{len(places)} places")
    return 0


if __name__ == "__main__":
    sys.exit(main())
