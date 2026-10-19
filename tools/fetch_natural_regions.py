#!/usr/bin/env python3
"""Downloads the official natural regions into content/natural_regions.geojson.

The result is committed, so tools/build_content.py stays reproducible and offline.
Rerun this only to refresh the boundaries, then rebuild.

Sources (public ArcGIS layers of Israeli government bodies):
  - Israel Central Bureau of Statistics (CBS), GIS unit: "גבולות אזורים טבעיים בישראל",
    the official natural regions (אזורים טבעיים), updated to 2018. It covers Israel
    and the Golan; the CBS shows Judea and Samaria as one polygon.
  - Ministry of Agriculture: "2025 אזורים טבעיים", which uses the same CBS codes and
    adds the natural regions of Judea and Samaria (codes 711 to 815). Only those are
    taken from it.
Lakes (CBS code 99) are left out; the map's own lakes are used instead.

Usage:  python3 tools/fetch_natural_regions.py
"""

from __future__ import annotations

import json
import sys
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path

import shapely
from shapely.geometry import MultiPolygon, Polygon, shape

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "content" / "natural_regions.geojson"

CBS = (
    "https://services8.arcgis.com/JcXY3lLZni6BK4El/arcgis/rest/services/"
    "אזורים_טבעיים_2018/FeatureServer/0"
)
AGRICULTURE = (
    "https://services3.arcgis.com/Fqk0gVrfcnumlR5m/arcgis/rest/services/"
    "2025_אזורים_טבעיים_FTD_/FeatureServer/0"
)
LAKE_CODE = 99
JUDEA_SAMARIA_CODES = range(700, 900)

# About 30 m at this latitude. The simplification keeps borders shared between neighbors.
SIMPLIFY_DEG = 0.0003


def query(layer: str, name_field: str) -> list[tuple[int, str, object]]:
    params = urllib.parse.urlencode(
        {"where": "1=1", "outFields": f"CODE,{name_field}", "outSR": 4326, "f": "geojson"}
    )
    url = urllib.parse.quote(layer, safe=":/") + "/query?" + params
    with urllib.request.urlopen(url, timeout=120) as r:
        features = json.loads(r.read())["features"]
    return [(f["properties"]["CODE"], f["properties"][name_field], shape(f["geometry"]).buffer(0)) for f in features]


def merged(rows) -> dict[int, tuple[str, object]]:
    """One geometry per code (a few regions come in several pieces)."""
    out: dict[int, tuple[str, object]] = {}
    for code, name, geom in rows:
        if code in out:
            geom = out[code][1].union(geom)
        out[code] = (name, geom)
    return out


def rounded(geom) -> dict:
    def r(coords):
        return [[round(x, 5), round(y, 5)] for x, y in coords]

    polys = geom.geoms if isinstance(geom, MultiPolygon) else [geom]
    return {
        "type": "MultiPolygon",
        "coordinates": [[r(p.exterior.coords)] + [r(i.coords) for i in p.interiors] for p in polys],
    }


def main() -> int:
    print("fetching CBS natural regions")
    regions = {c: (n, g, "CBS 2018") for c, (n, g) in merged(query(CBS, "HEB_NAME")).items() if c != LAKE_CODE}
    print("fetching Judea and Samaria natural regions (Ministry of Agriculture)")
    for c, (n, g) in merged(query(AGRICULTURE, "אזור_טיבעי")).items():
        if c in JUDEA_SAMARIA_CODES:
            regions[c] = (n, g, "Ministry of Agriculture 2025")
    if len(regions) < 55:
        print(f"expected about 59 regions, got {len(regions)}", file=sys.stderr)
        return 1

    codes = sorted(regions)
    simplified = shapely.coverage_simplify([regions[c][1] for c in codes], SIMPLIFY_DEG)
    features = []
    for code, geom in zip(codes, simplified):
        name, _, source = regions[code]
        if isinstance(geom, Polygon):
            geom = MultiPolygon([geom])
        features.append({
            "type": "Feature",
            "properties": {"code": code, "name": name, "source": source},
            "geometry": rounded(geom),
        })
    OUT.write_text(json.dumps({
        "type": "FeatureCollection",
        "source": "Israel Central Bureau of Statistics (natural regions 2018); "
                  "Ministry of Agriculture (Judea and Samaria natural regions, 2025)",
        "fetched": date.today().isoformat(),
        "features": features,
    }, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"{OUT.relative_to(ROOT)}: {len(features)} regions, {OUT.stat().st_size // 1024} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
