#!/usr/bin/env python3
"""Downloads Israel's six district boundaries from OpenStreetMap into content/districts.geojson.

The result is committed, so tools/build_content.py stays reproducible and offline
while OSM keeps changing. Rerun this only to refresh the boundaries, then rebuild.

Source: OpenStreetMap relations (boundary=administrative, admin_level=4), fetched
with the main OSM API. Data (c) OpenStreetMap contributors, ODbL 1.0.

Usage:  python3 tools/fetch_districts.py
"""

from __future__ import annotations

import json
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path

from shapely import ops
from shapely.geometry import LineString, MultiPolygon, Polygon, mapping

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "content" / "districts.geojson"

# OSM relation ids of the districts (mehozot).
DISTRICTS = {
    "north": 1375620,
    "haifa": 1400966,
    "center": 1400928,
    "tel_aviv": 1400916,
    "jerusalem": 1384720,
    "south": 1473952,
}

# About 50 m at this latitude: plenty for a phone map, and keeps the file small.
SIMPLIFY_DEG = 0.0005
API = "https://api.openstreetmap.org/api/0.6/relation/{}/full"
USER_AGENT = "Mapa content pipeline (github.com/shavei/Learn-Israel)"


def fetch(relation_id: int) -> ET.Element:
    req = urllib.request.Request(API.format(relation_id), headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as r:
        return ET.fromstring(r.read())


def assemble(osm: ET.Element, relation_id: int) -> MultiPolygon:
    """Joins the relation's outer ways into polygons (inner ways become holes)."""
    nodes = {n.get("id"): (float(n.get("lon")), float(n.get("lat"))) for n in osm.iter("node")}
    ways = {w.get("id"): [nodes[nd.get("ref")] for nd in w.iter("nd")] for w in osm.iter("way")}
    rel = next(r for r in osm.iter("relation") if r.get("id") == str(relation_id))
    rings = {"outer": [], "inner": []}
    for m in rel.iter("member"):
        if m.get("type") == "way" and m.get("role") in rings:
            rings[m.get("role")].append(LineString(ways[m.get("ref")]))
    outer = ops.unary_union(list(ops.polygonize(rings["outer"])))
    inner = ops.unary_union(list(ops.polygonize(rings["inner"]))) if rings["inner"] else None
    area = outer.difference(inner) if inner is not None else outer
    return area if isinstance(area, MultiPolygon) else MultiPolygon([area])


def rounded(geom):
    def r(coords):
        return [[round(x, 5), round(y, 5)] for x, y in coords]

    polys = geom.geoms if isinstance(geom, MultiPolygon) else [geom]
    return {
        "type": "MultiPolygon",
        "coordinates": [[r(p.exterior.coords)] + [r(i.coords) for i in p.interiors] for p in polys],
    }


def main() -> int:
    features = []
    for key, rid in DISTRICTS.items():
        print(f"fetching {key} (relation {rid})")
        area = assemble(fetch(rid), rid)
        if area.is_empty:
            print(f"{key}: relation {rid} has no closed outer ring", file=sys.stderr)
            return 1
        area = area.simplify(SIMPLIFY_DEG, preserve_topology=True)
        if isinstance(area, Polygon):
            area = MultiPolygon([area])
        features.append({
            "type": "Feature",
            "properties": {"district": key, "osm_relation": rid},
            "geometry": rounded(area),
        })
        time.sleep(1)
    OUT.write_text(json.dumps({
        "type": "FeatureCollection",
        "source": "OpenStreetMap contributors, ODbL 1.0",
        "fetched": date.today().isoformat(),
        "features": features,
    }, separators=(",", ":")) + "\n")
    print(f"{OUT.relative_to(ROOT)}: {OUT.stat().st_size // 1024} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
