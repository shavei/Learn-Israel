#!/usr/bin/env python3
"""Downloads the map's official and open sources into content/.

The results are committed, so tools/build_content.py stays reproducible and offline
while the sources keep changing. Rerun this only to refresh them, then rebuild.

  content/natural_regions.geojson   Israel Central Bureau of Statistics (CBS), GIS unit:
                                    "גבולות אזורים טבעיים בישראל", the official natural
                                    regions (אזורים טבעיים), 2018, with the Kinneret and
                                    the Dead Sea (code 99). They nest in the official
                                    districts and subdistricts and follow Israel's
                                    official outline. The CBS shows Judea and Samaria as
                                    one polygon, so its natural regions (codes 711 to 815)
                                    come from the Ministry of Agriculture's "2025 אזורים
                                    טבעיים" layer, which uses the same CBS codes.
  content/localities.csv            CBS locality file 2023 (data.gov.il): each locality's
                                    official point on the Israeli TM grid, with its
                                    natural region and district.
  content/jordan_river.geojson      The Jordan River's main stream from OpenStreetMap
                                    (relation 2246907), (c) OpenStreetMap contributors, ODbL.
  content/region_overrides.geojson  The OpenStreetMap areas listed under "overrides" in
                                    content/regions.yaml (ODbL).
  content/surroundings.geojson      Everything around Israel, from OpenStreetMap (ODbL): the
                                    land (osmdata.openstreetmap.de land polygons, built from
                                    the coastline), the neighboring countries' boundaries and
                                    the Dead Sea. Clipped to the map window.
  content/palestine.geojson         The Gaza Strip and the West Bank from the UN OCHA common
                                    operational dataset (HDX "cod-ab-pse", CC BY-IGO). The West
                                    Bank's western edge is the 1949 armistice line (Green Line).

The land polygons come as a 900 MB download; pyshp reads them (in requirements.txt).

Usage:  python3 tools/fetch_sources.py
"""

from __future__ import annotations

import csv
import json
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path

import shapely
from shapely import ops
from shapely.geometry import LineString, MultiPolygon, Polygon, shape

ROOT = Path(__file__).resolve().parent.parent
CONTENT = ROOT / "content"
USER_AGENT = "Mapa content pipeline (github.com/shavei/Learn-Israel)"

CBS_REGIONS = (
    "https://services8.arcgis.com/JcXY3lLZni6BK4El/arcgis/rest/services/"
    "אזורים_טבעיים_2018/FeatureServer/0"
)
AGRICULTURE_REGIONS = (
    "https://services3.arcgis.com/Fqk0gVrfcnumlR5m/arcgis/rest/services/"
    "2025_אזורים_טבעיים_FTD_/FeatureServer/0"
)
JUDEA_SAMARIA_CODES = range(700, 900)
LOCALITIES_API = "https://data.gov.il/api/3/action/datastore_search"
LOCALITIES_RESOURCE = "d47a54ff-87f0-44b3-b33a-f284c0c38e5a"  # קובץ היישובים 2023
JORDAN_RELATION = 2246907
DEAD_SEA_RELATION = 9351124
COUNTRY_RELATIONS = {"LBN": 184843, "SYR": 184840, "JOR": 184818, "EGY": 1473947, "SAU": 307584}
LAND_POLYGONS = "https://osmdata.openstreetmap.de/download/land-polygons-split-4326.zip"
OCHA_PALESTINE = (
    "https://data.humdata.org/dataset/2caf8373-816f-458c-9913-71bddb9cab7c/resource/"
    "ca372385-4c79-4378-abf1-cb506fb98023/download/pse_admin_boundaries.geojson.zip"
)
# The map window in lon/lat (as in build_content.py).
WINDOW = (32.6, 28.4, 37.2, 34.2)

# About 20 m. Simplified as one coverage, so neighbors keep one shared border.
SIMPLIFY_DEG = 0.0002


def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=180) as r:
        return r.read()


def arcgis(layer: str, name_field: str) -> dict[int, tuple[str, object]]:
    """One geometry per code (a few regions come in several pieces)."""
    params = urllib.parse.urlencode({"where": "1=1", "outFields": f"CODE,{name_field}", "outSR": 4326, "f": "geojson"})
    out: dict[int, tuple[str, object]] = {}
    for f in json.loads(get(urllib.parse.quote(layer, safe=":/") + "/query?" + params))["features"]:
        code, name, geom = f["properties"]["CODE"], f["properties"][name_field], shape(f["geometry"]).buffer(0)
        if code in out:
            geom = out[code][1].union(geom)
        out[code] = (name, geom)
    return out


def rounded(geom) -> dict:
    def r(coords):
        return [[round(x, 6), round(y, 6)] for x, y in coords]

    if geom.geom_type == "LineString":
        return {"type": "LineString", "coordinates": r(geom.coords)}
    if geom.geom_type == "MultiLineString":
        return {"type": "MultiLineString", "coordinates": [r(g.coords) for g in geom.geoms]}
    polys = geom.geoms if isinstance(geom, MultiPolygon) else [geom]
    return {
        "type": "MultiPolygon",
        "coordinates": [[r(p.exterior.coords)] + [r(i.coords) for i in p.interiors] for p in polys],
    }


def collection(features: list, source: str) -> str:
    return json.dumps(
        {"type": "FeatureCollection", "source": source, "fetched": date.today().isoformat(), "features": features},
        ensure_ascii=False, separators=(",", ":"),
    ) + "\n"


def natural_regions() -> None:
    print("fetching CBS natural regions")
    regions = {c: (n, g, "CBS 2018") for c, (n, g) in arcgis(CBS_REGIONS, "HEB_NAME").items()}
    print("fetching Judea and Samaria natural regions (Ministry of Agriculture)")
    for c, (n, g) in arcgis(AGRICULTURE_REGIONS, "אזור_טיבעי").items():
        if c in JUDEA_SAMARIA_CODES:
            regions[c] = (n, g, "Ministry of Agriculture 2025")
    if len(regions) < 56:
        sys.exit(f"expected about 60 natural regions, got {len(regions)}")
    codes = sorted(regions)
    simplified = shapely.coverage_simplify([regions[c][1] for c in codes], SIMPLIFY_DEG)
    features = [
        {
            "type": "Feature",
            "properties": {"code": c, "name": regions[c][0], "source": regions[c][2]},
            "geometry": rounded(MultiPolygon([g]) if isinstance(g, Polygon) else g),
        }
        for c, g in zip(codes, simplified)
    ]
    out = CONTENT / "natural_regions.geojson"
    out.write_text(collection(features, "Israel Central Bureau of Statistics, natural regions 2018; "
                                        "Ministry of Agriculture, Judea and Samaria natural regions 2025"))
    print(f"  {out.relative_to(ROOT)}: {len(features)} regions, {out.stat().st_size // 1024} KB")


def localities() -> None:
    print("fetching the CBS locality file")
    url = LOCALITIES_API + "?" + urllib.parse.urlencode({"resource_id": LOCALITIES_RESOURCE, "limit": 5000})
    rows = []
    for rec in json.loads(get(url))["result"]["records"]:
        coords = str(rec["קואורדינטות"] or "")
        if len(coords) != 12:
            continue  # a few records (tribes without a fixed place) have no coordinates
        rows.append({
            "symbol": rec["סמל יישוב"],
            "name": rec["שם יישוב"].strip(),
            # Twelve digits: x then y, in meters on the Israeli TM grid.
            "x": int(coords[:6]),
            "y": int(coords[6:]),
            "natural_region": rec["אזור טבעי"] or "",
            "district": rec["סמל מחוז"] or "",
        })
    rows.sort(key=lambda r: r["symbol"])
    out = CONTENT / "localities.csv"
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    print(f"  {out.relative_to(ROOT)}: {len(rows)} localities")


def osm_relation(relation_id: int) -> tuple[dict, dict]:
    """The relation's ways by role, as lines in lon/lat, and its tags."""
    osm = ET.fromstring(get(f"https://api.openstreetmap.org/api/0.6/relation/{relation_id}/full"))
    nodes = {n.get("id"): (float(n.get("lon")), float(n.get("lat"))) for n in osm.iter("node")}
    ways = {w.get("id"): [nodes[nd.get("ref")] for nd in w.iter("nd")] for w in osm.iter("way")}
    rel = next(r for r in osm.iter("relation") if r.get("id") == str(relation_id))
    roles: dict[str, list] = {}
    for m in rel.iter("member"):
        if m.get("type") == "way":
            roles.setdefault(m.get("role"), []).append(LineString(ways[m.get("ref")]))
    return roles, {t.get("k"): t.get("v") for t in rel.iter("tag")}


def jordan_river() -> None:
    print(f"fetching the Jordan River (OSM relation {JORDAN_RELATION})")
    main = osm_relation(JORDAN_RELATION)[0]["main_stream"]
    river = ops.linemerge(main).simplify(SIMPLIFY_DEG / 2)
    out = CONTENT / "jordan_river.geojson"
    out.write_text(collection([{"type": "Feature", "properties": {"osm_relation": JORDAN_RELATION},
                                "geometry": rounded(river)}], "OpenStreetMap contributors, ODbL 1.0"))
    print(f"  {out.relative_to(ROOT)}: {out.stat().st_size // 1024} KB")


def osm_area(relation_id: int):
    roles, tags = osm_relation(relation_id)
    outer = ops.unary_union(list(ops.polygonize(roles.get("outer", []))))
    inner = roles.get("inner")
    return (outer.difference(ops.unary_union(list(ops.polygonize(inner)))) if inner else outer), tags


def surroundings() -> None:
    import tempfile
    import zipfile

    import shapefile
    from shapely.geometry import box

    window = box(*WINDOW)
    features = []
    print("fetching the OSM land polygons (about 900 MB)")
    with tempfile.TemporaryDirectory() as tmp:
        zip_path = Path(tmp) / "land.zip"
        req = urllib.request.Request(LAND_POLYGONS, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=3600) as r, zip_path.open("wb") as f:
            while chunk := r.read(1 << 20):
                f.write(chunk)
        with zipfile.ZipFile(zip_path) as z:
            z.extractall(tmp)
        shp = next(Path(tmp).glob("*/land_polygons.shp"))
        parts = [shape(s.__geo_interface__).intersection(window) for s in shapefile.Reader(str(shp)).iterShapes(bbox=list(WINDOW))]
    land = ops.unary_union([p for p in parts if not p.is_empty]).simplify(SIMPLIFY_DEG / 2)
    features.append({"type": "Feature", "properties": {"kind": "land"}, "geometry": rounded(land)})
    for a3, rid in COUNTRY_RELATIONS.items():
        print(f"fetching {a3} (OSM relation {rid})")
        area, _ = osm_area(rid)
        features.append({"type": "Feature", "properties": {"kind": "country", "country": a3, "osm_relation": rid},
                         "geometry": rounded(area.intersection(window).simplify(SIMPLIFY_DEG / 2))})
    print(f"fetching the Dead Sea (OSM relation {DEAD_SEA_RELATION})")
    dead_sea, _ = osm_area(DEAD_SEA_RELATION)
    features.append({"type": "Feature", "properties": {"kind": "dead_sea", "osm_relation": DEAD_SEA_RELATION},
                     "geometry": rounded(dead_sea.simplify(SIMPLIFY_DEG / 2))})
    out = CONTENT / "surroundings.geojson"
    out.write_text(collection(features, "OpenStreetMap contributors, ODbL 1.0 (land polygons: osmdata.openstreetmap.de)"))
    print(f"  {out.relative_to(ROOT)}: {out.stat().st_size // 1024} KB")


def palestine() -> None:
    import io
    import zipfile

    print("fetching the UN OCHA boundaries of Gaza and the West Bank")
    with zipfile.ZipFile(io.BytesIO(get(OCHA_PALESTINE))) as z:
        name = next(n for n in z.namelist() if n.endswith("admin1.geojson"))
        admin1 = json.loads(z.read(name))["features"]
    features = []
    for f in admin1:
        key = {"Gaza Strip": "gaza", "West Bank": "west_bank"}[f["properties"]["adm1_name"]]
        geom = shape(f["geometry"]).buffer(0).simplify(SIMPLIFY_DEG / 2)
        features.append({"type": "Feature", "properties": {"area": key, "pcode": f["properties"]["adm1_pcode"]},
                         "geometry": rounded(geom)})
    out = CONTENT / "palestine.geojson"
    out.write_text(collection(features, "UN OCHA, State of Palestine COD-AB (HDX cod-ab-pse), CC BY-IGO"))
    print(f"  {out.relative_to(ROOT)}: {out.stat().st_size // 1024} KB")


def region_overrides() -> None:
    import yaml

    overrides = yaml.safe_load((CONTENT / "regions.yaml").read_text()).get("overrides", {})
    features = []
    for region, relations in overrides.items():
        for rid in relations:
            print(f"fetching OSM relation {rid} for {region}")
            roles, tags = osm_relation(rid)
            area = ops.unary_union(list(ops.polygonize(roles.get("outer", []))))
            if area.is_empty:
                sys.exit(f"OSM relation {rid} has no closed outer ring")
            features.append({
                "type": "Feature",
                "properties": {"osm_relation": rid, "name": tags.get("name"), "region": region},
                "geometry": rounded(area.simplify(SIMPLIFY_DEG / 2)),
            })
    out = CONTENT / "region_overrides.geojson"
    out.write_text(collection(features, "OpenStreetMap contributors, ODbL 1.0"))
    print(f"  {out.relative_to(ROOT)}: {len(features)} areas")


def main() -> int:
    natural_regions()
    localities()
    jordan_river()
    region_overrides()
    surroundings()
    palestine()
    return 0


if __name__ == "__main__":
    sys.exit(main())
