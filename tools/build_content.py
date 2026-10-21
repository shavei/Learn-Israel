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
  content/osm_places.geojson             rivers, craters and highways of places.yaml, from OSM (ODbL)
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

# Beyond the window. The map data stops at WINDOW, whose edges are slanted on the Israeli
# grid, and the app paints what lies past them so the edge of the data never shows: land all
# round, except the open Mediterranean to the northwest, from where the Sinai coast meets the
# west edge to where the Lebanese coast meets the north edge. Both reach a little under the
# map data, so no seam shows.
OUTER = box(15.0, 15.0, 55.0, 45.0)
COAST_ON_WEST_EDGE = 31.058  # latitude
COAST_ON_NORTH_EDGE = 35.631  # longitude
EDGE_OVERLAP = 0.02  # degrees
# The coast north of the window, roughly (lon, lat): Lebanon, Syria and the Gulf of Iskenderun,
# then west along Turkey. Only its southern end is ever on screen.
COAST_NORTH_OF_WINDOW = [
    (35.73, 34.33), (35.82, 34.44), (35.97, 34.63), (35.88, 34.89), (35.94, 35.18), (35.92, 35.36),
    (35.77, 35.52), (35.85, 35.85), (35.95, 36.08), (36.17, 36.59), (35.3, 36.7), (34.6, 36.8), (33.9, 36.3),
]

# Simplification tolerance in meters, per layer.
SIMPLIFY = {
    "land": 40, "neighbors": 120, "lakes": 30, "rivers": 60, "seas": 120, "green_line": 40, "regions": 150,
    "roads": 100,
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


ROAD_STUB_M = 300  # a piece of a highway shorter than this, beside a longer piece, is a ramp or the other carriageway


def road_course(geom):
    """A highway's ways as few lines as possible. The ways split at every junction and run in
    two carriageways, which leaves hundreds of short pieces once simplified; the short ones
    lying beside a longer piece add nothing to grading or drawing, so they are dropped."""
    pieces = lines(ops.linemerge(lines(geom.simplify(SIMPLIFY["roads"]))))
    long = MultiLineString([p for p in pieces if p.length >= ROAD_STUB_M])
    return MultiLineString([p for p in pieces if p.length >= ROAD_STUB_M or long.distance(p) > SIMPLIFY["roads"]])


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


def beyond_window():
    """The land and the sea past the edges of the window, on the grid (see OUTER)."""
    minx, miny, maxx, maxy = WINDOW.bounds
    inner = box(minx + EDGE_OVERLAP, miny + EDGE_OVERLAP, maxx - EDGE_OVERLAP, maxy - EDGE_OVERLAP)
    sea = Polygon([
        (minx + EDGE_OVERLAP, COAST_ON_WEST_EDGE), (minx + EDGE_OVERLAP, maxy - EDGE_OVERLAP),
        (COAST_ON_NORTH_EDGE, maxy - EDGE_OVERLAP), (COAST_ON_NORTH_EDGE, maxy), *COAST_NORTH_OF_WINDOW,
        (OUTER.bounds[0], COAST_NORTH_OF_WINDOW[-1][1]), (OUTER.bounds[0], COAST_ON_WEST_EDGE),
    ])
    # Densified first, since lines of longitude and latitude curve on the grid: finely along
    # the window, where it shows, and coarsely far out of view.
    land = Polygon(shapely.segmentize(OUTER.exterior, 2.0).coords, [shapely.segmentize(inner.exterior, 0.25).coords])
    return project(land), project(shapely.segmentize(sea, 0.25))


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


def elevation_grid(meta) -> np.ndarray:
    """An elevation grid from content/: rows stored as differences from the cell before."""
    deltas = np.fromfile(ROOT / "content" / f"elevation_{meta['name']}.bin", "<i2").reshape(meta["height"], meta["width"])
    return np.cumsum(deltas.astype(np.int32), axis=1)


# The relief style shows Israel only: the elevation the app gets stops this far outside its
# land (so slopes at the coast and borders are shaded from real heights; the app clips
# terrain to the land).
TERRAIN_MARGIN = 2000


def israel_terrain(meta, land) -> tuple[bytes, dict]:
    """The elevation grid cut to Israel's land and margin, 0 beyond it (compresses to almost nothing)."""
    z = elevation_grid(meta)
    cell, minx, maxy = meta["cell"], meta["minX"], meta["maxY"]
    area = land.simplify(200).buffer(TERRAIN_MARGIN)
    bx0, by0, bx1, by1 = area.bounds
    c0, c1 = max(0, int((bx0 - minx) // cell)), min(meta["width"], int(np.ceil((bx1 - minx) / cell)))
    r0, r1 = max(0, int((maxy - by1) // cell)), min(meta["height"], int(np.ceil((maxy - by0) / cell)))
    z = z[r0:r1, c0:c1]
    xs = minx + (np.arange(c0, c1) + 0.5) * cell
    ys = maxy - (np.arange(r0, r1) + 0.5) * cell
    shapely.prepare(area)
    z = np.where(shapely.contains_xy(area, *np.meshgrid(xs, ys)), z, 0)
    deltas = np.diff(z, axis=1, prepend=0).astype("<i2")
    out = {"name": "israel", "minX": minx + c0 * cell, "maxY": maxy - r0 * cell, "cell": cell,
           "width": c1 - c0, "height": r1 - r0}
    return deltas.tobytes(), out


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

    osm_places = {
        f["properties"]["source"]: shape(f["geometry"])
        for f in json.loads((ROOT / "content" / "osm_places.geojson").read_text())["features"]
    }
    # Highways are taken by their number, which roads in the neighboring countries may share.
    israel_roads = project(ops.unary_union([land_ll, ocha["gaza"]])).buffer(1_000)

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
        elif kind in ("osm", "road"):
            source = osm_places.get(p["geometry"])
            if source is None:
                errors.append(f"{p['id']}: {p['geometry']} is not in content/osm_places.geojson (run tools/fetch_sources.py)")
                continue
            if kind == "road":
                course = road_course(project(source).intersection(israel_roads))
            else:
                course = clean(source.intersection(WINDOW), SIMPLIFY["rivers"])
            if course.geom_type in ("Polygon", "MultiPolygon"):
                geometry["polygons"] = encode_polygons(course)
                label = area_label(course)
            else:
                course = ops.linemerge(lines(course)) if lines(course) else course
                geometry["lines"] = encode_lines(course)
                label = line_label(course)
            # A stream or a crater counts toward the region at the middle of its course; a
            # highway crosses too many regions to count toward one.
            if kind == "osm":
                region = min(region_geoms, key=lambda k: region_geoms[k].distance(label))
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
        if "tolerance_km" in p:
            place["tolerance"] = round(p["tolerance_km"] * 1000)
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
    detail = next(g for g in json.loads((ROOT / "content" / "elevation.json").read_text())["grids"] if g["name"] == "detail")
    terrain_bytes, terrain = israel_terrain(detail, land)
    beyond_land, beyond_sea = beyond_window()
    map_json = {
        "version": 1,
        "crs": "EPSG:2039",
        "window": [round(v) for v in window_itm.bounds],
        "home": [round(minx), round(miny), round(maxx), round(maxy)],
        "layers": [
            {"id": "beyond_land", "polygons": encode_polygons(beyond_land)},
            {"id": "beyond_sea", "polygons": encode_polygons(beyond_sea)},
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
        # Ground height over Israel for the relief style, in elevation_israel.bin.
        "elevation": [terrain],
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
    (ASSETS / "elevation_israel.bin").write_bytes(terrain_bytes)
    for f in ("map.json", "places.json", "elevation_israel.bin"):
        print(f"{f}: {(ASSETS / f).stat().st_size // 1024} KB")
    print(f"{len(places)} places")
    return 0


if __name__ == "__main__":
    sys.exit(main())
