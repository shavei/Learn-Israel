#!/usr/bin/env python3
"""Builds the app's map and place assets from open data.

Inputs:
  content/places.yaml                    hand-curated curriculum (what is taught)
  Natural Earth 10m GeoJSON (public domain), downloaded into tools/raw/
  GeoNames cities via the geonamescache package (CC BY 4.0)

Outputs (app/src/main/assets/):
  map.json     background layers (neighbors, land, Gaza, lakes, rivers, Green Line)
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
SIMPLIFY = {"land": 40, "neighbors": 120, "lakes": 30, "rivers": 60, "seas": 120, "green_line": 40}

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
    errors = []
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
        if kind == "geonames":
            city = gn.get(arg)
            if city is None:
                errors.append(f"{p['id']}: geonames id {arg} not found")
                continue
            pt = project(Point(city["longitude"], city["latitude"]))
            geometry["point"] = [round(pt.x), round(pt.y)]
            label = pt
            if not land.buffer(500).contains(pt):
                errors.append(f"{p['id']}: point is outside the land area")
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
        places.append(
            {
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
        )

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
            {"id": "green_line", "lines": encode_lines(green_line)},
        ],
        "attribution": "Natural Earth; GeoNames (CC BY 4.0)",
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
