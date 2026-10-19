#!/usr/bin/env python3
"""Builds the app's map and place assets from open data.

Inputs:
  content/places.yaml                    hand-curated curriculum (what is taught)
  content/regions.yaml                   which official natural regions make up each game region
  content/natural_regions.geojson        official natural regions (CBS; Ministry of Agriculture for
                                         Judea and Samaria), refreshed by tools/fetch_natural_regions.py
  content/districts.geojson              district boundaries from OpenStreetMap (ODbL),
                                         refreshed by tools/fetch_districts.py
  Natural Earth 10m GeoJSON (public domain), downloaded into tools/raw/
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

import json
import sys
import urllib.request
from pathlib import Path

import geonamescache
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
RAW = ROOT / "tools" / "raw"
ASSETS = ROOT / "app" / "src" / "main" / "assets"

NE_BASE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/"
NE_LAYERS = [
    "ne_10m_admin_0_countries",
    "ne_10m_admin_0_disputed_areas",
    "ne_10m_lakes",
    "ne_10m_rivers_lake_centerlines",
]

# Map window in lon/lat. Everything is clipped to it.
WINDOW = box(32.6, 28.4, 37.2, 34.2)
NEIGHBORS = ["LBN", "SYR", "JOR", "EGY", "SAU"]

# Simplification tolerance in meters, per layer.
SIMPLIFY = {
    "land": 40, "neighbors": 120, "lakes": 30, "rivers": 60, "seas": 120, "green_line": 40, "districts": 60, "regions": 150,
}

# Land left outside every region or district, in square meters, before the build fails.
MAX_UNASSIGNED_M2 = 1e6

TO_ITM = Transformer.from_crs("EPSG:4326", "EPSG:2039", always_xy=True)


def fetch_natural_earth() -> dict[str, list[dict]]:
    RAW.mkdir(parents=True, exist_ok=True)
    out = {}
    for name in NE_LAYERS:
        path = RAW / f"{name}.geojson"
        if not path.exists():
            print(f"downloading {name}")
            urllib.request.urlretrieve(NE_BASE + name + ".geojson", path)
        out[name] = json.loads(path.read_text())["features"]
    return out


def project(geom):
    return shapely.transform(geom, lambda xy: list(zip(*TO_ITM.transform(xy[:, 0], xy[:, 1]))))


def clean(geom, tolerance):
    """Projects to ITM, simplifies, and drops slivers."""
    geom = project(geom).simplify(tolerance, preserve_topology=True)
    if geom.geom_type in ("Polygon", "MultiPolygon"):
        geom = geom.buffer(0)
    return geom


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


def by_prop(features, key, value):
    return [shape(f["geometry"]) for f in features if f["properties"].get(key) == value]


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


def fill(land_area, shapes: dict) -> tuple[dict, object]:
    """Splits the land among official areas whose outer edges differ from the map's.

    The official borders and the Natural Earth coast and borders disagree by up to a
    few hundred meters (more along the Egyptian border), so after the exact split the
    leftover strips go to the nearest area: areas grow step by step into what is left.
    Returns the areas and the land still left over.
    """
    exact, rest = partition(land_area, shapes)
    grown = dict(exact)
    for step in (100, 250, 500, 1_000, 2_000, 4_000, 8_000):
        if rest.is_empty:
            break
        taken, rest = partition(rest, {k: grown[k].buffer(step) for k in grown}, min_part=0)
        grown = {k: ops.unary_union([grown[k], taken[k]]) for k in grown}
    return grown, rest


def natural_regions(land_area) -> tuple[dict, object, list[str]]:
    """The game's natural regions: unions of the official natural regions (by code)."""
    groups = yaml.safe_load((ROOT / "content" / "regions.yaml").read_text())["regions"]
    features = json.loads((ROOT / "content" / "natural_regions.geojson").read_text())["features"]
    official = {f["properties"]["code"]: shape(f["geometry"]) for f in features}
    used = [c for codes in groups.values() for c in codes]
    errors = [f"natural region code {c} is used twice in regions.yaml" for c in set(used) if used.count(c) > 1]
    errors += [f"natural region code {c} is not in regions.yaml" for c in official if c not in used]
    errors += [f"regions.yaml: unknown natural region code {c}" for c in used if c not in official]
    shapes = {k: project(ops.unary_union([official[c] for c in codes if c in official])) for k, codes in groups.items()}
    pieces, rest = fill(land_area, shapes)
    # Simplified together, so neighbors keep exactly one shared border.
    keys = list(pieces)
    simplified = shapely.coverage_simplify([pieces[k] for k in keys], SIMPLIFY["regions"])
    return {k: g.buffer(0) for k, g in zip(keys, simplified)}, rest, errors


def districts(land_area, west_bank_itm) -> tuple[dict, object]:
    """The six districts, clipped to the land, plus the Judea and Samaria Area."""
    features = json.loads((ROOT / "content" / "districts.geojson").read_text())["features"]
    osm = {f["properties"]["district"]: clean(shape(f["geometry"]), SIMPLIFY["districts"]) for f in features}
    # The Judea and Samaria Area is what the de facto land adds beyond Israel's districts.
    osm["judea_samaria"] = west_bank_itm.difference(ops.unary_union(list(osm.values())))
    return fill(land_area, osm)


def outlines(pieces: dict, land_area) -> list:
    """Borders shared by neighboring areas, without the coast and the outer borders."""
    edges = ops.unary_union([g.boundary for g in pieces.values()])
    inner = edges.difference(land_area.boundary.buffer(150))
    return ops.linemerge(lines(inner)) if not inner.is_empty else inner


def main() -> int:
    ne = fetch_natural_earth()
    countries = ne["ne_10m_admin_0_countries"]
    disputed = ne["ne_10m_admin_0_disputed_areas"]

    isr = ops.unary_union(by_prop(countries, "ADM0_A3", "ISR"))
    psx = ops.unary_union(by_prop(countries, "ADM0_A3", "PSX"))
    gaza_area = ops.unary_union(by_prop(disputed, "NAME", "Gaza"))

    # Main map = de facto lines of control: Israel plus the West Bank as one land area.
    # Gaza is drawn separately. The Green Line is an optional overlay (decision in doc 10).
    gaza = psx.intersection(gaza_area.buffer(0.01))
    west_bank = psx.difference(gaza)
    land_ll = ops.unary_union([isr, west_bank]).intersection(WINDOW)

    land = clean(land_ll, SIMPLIFY["land"])
    gaza_itm = clean(gaza.intersection(WINDOW), SIMPLIFY["land"])
    green_line = clean(west_bank, SIMPLIFY["green_line"]).boundary.intersection(
        clean(isr, SIMPLIFY["green_line"]).buffer(300)
    )
    green_line = ops.linemerge(lines(green_line)) if not green_line.is_empty else green_line

    neighbor_geoms = {}
    for a3 in NEIGHBORS:
        g = ops.unary_union(by_prop(countries, "ADM0_A3", a3)).intersection(WINDOW).difference(land_ll)
        neighbor_geoms[a3] = clean(g, SIMPLIFY["neighbors"])

    all_land_ll = ops.unary_union([land_ll, gaza] + [
        ops.unary_union(by_prop(countries, "ADM0_A3", a3)) for a3 in NEIGHBORS
    ]).intersection(WINDOW)
    water_ll = WINDOW.difference(all_land_ll)

    lakes = ne["ne_10m_lakes"]
    rivers = ne["ne_10m_rivers_lake_centerlines"]
    lake_geoms = {
        n: ops.unary_union(by_prop(lakes, "name", n)).intersection(WINDOW)
        for n in ("Sea of Galilee", "Dead Sea")
    }
    river_geoms = {"Jordan": ops.unary_union(by_prop(rivers, "name", "Jordan")).intersection(WINDOW)}

    # Regions and districts split the land; the lakes are not part of any of them.
    lakes_itm = ops.unary_union([clean(g, SIMPLIFY["lakes"]) for g in lake_geoms.values()])
    land_area = land.difference(lakes_itm)
    region_geoms, region_rest, region_errors = natural_regions(land_area)
    district_geoms, district_rest = districts(land_area, clean(west_bank.intersection(WINDOW), SIMPLIFY["land"]))

    gn = geonamescache.GeonamesCache(min_city_population=15000).get_cities()

    # Labels for areas sit inside the part of the area near Israel, so they stay on screen.
    near_israel = project(ops.unary_union([land_ll, gaza])).buffer(40_000).envelope

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
        if kind in ("geonames", "point"):
            if kind == "geonames":
                city = gn.get(arg)
                if city is None:
                    errors.append(f"{p['id']}: geonames id {arg} not found")
                    continue
                lon, lat = city["longitude"], city["latitude"]
            else:
                lon, lat = map(float, arg.split(","))
            pt = project(Point(lon, lat))
            geometry["point"] = [round(pt.x), round(pt.y)]
            label = pt
            # Natural Earth's border is coarse: towns right on it (Metula, the Arava) can
            # fall up to about a kilometer outside the drawn land.
            if not land.buffer(1_500).contains(pt):
                errors.append(f"{p['id']}: point is outside the land area")
            # The natural region it lies in (nearest one for points on the coast).
            region = min(region_geoms, key=lambda k: region_geoms[k].distance(pt))
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
            seed = Point(*map(float, arg.split(",")))
            parts = [g for g in polygons(water_ll) if g.contains(seed)]
            if not parts:
                errors.append(f"{p['id']}: seed {arg} is not in open water")
                continue
            geometry["polygons"] = encode_polygons(clean(parts[0], SIMPLIFY["seas"]))
            label = project(seed)
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
    israel_view = project(ops.unary_union([land_ll, gaza]))
    minx, miny, maxx, maxy = israel_view.bounds
    window_itm = project(WINDOW)
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
        "attribution": "Natural Earth; GeoNames (CC BY 4.0); © OpenStreetMap contributors (ODbL)",
    }

    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "map.json").write_text(json.dumps(map_json, separators=(",", ":")))
    (ASSETS / "places.json").write_text(
        json.dumps({"version": 1, "places": places}, ensure_ascii=False, separators=(",", ":"))
    )
    for f in ("map.json", "places.json"):
        print(f"{f}: {(ASSETS / f).stat().st_size // 1024} KB")
    print(f"{len(places)} places")
    return 0


if __name__ == "__main__":
    sys.exit(main())
