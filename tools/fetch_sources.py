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
  content/isohyets.geojson          The rainfall lines named under "climate_splits" in
                                    content/regions.yaml, traced from the Israel
                                    Meteorological Service's mean annual rainfall
                                    1991-2020 (400 m grid, published by the CBS as a
                                    public ArcGIS image service).
  content/elevation_detail.bin      Ground height in meters on a 100 m grid in the Israeli TM
  content/elevation.json            projection over Israel and its edges, from the Copernicus
                                    DEM GLO-90 (90 m). int16 meters, little endian, rows from
                                    north to south, each row stored as differences from the
                                    cell before it (compresses three times better);
                                    elevation.json gives the grid. tools/build_content.py cuts
                                    it to Israel's land for the relief map
                                    style. Copernicus DEM (c) DLR e.V. 2010-2014 and (c) Airbus
                                    Defence and Space GmbH 2014-2018, provided under COPERNICUS
                                    by the European Union and ESA; free to use with attribution.
  content/osm_places.geojson        The places of content/places.yaml drawn from OpenStreetMap
                                    (ODbL): rivers and craters by their OSM way or relation,
                                    and highways as every main road way with their number
                                    (from the Overpass API).
  content/palestine.geojson         The Gaza Strip and the West Bank from the UN OCHA common
                                    operational dataset (HDX "cod-ab-pse", CC BY-IGO). The West
                                    Bank's western edge is the 1949 armistice line (Green Line).

The land polygons come as a 900 MB download, the elevation tiles as 140 MB. This script needs tools/requirements-fetch.txt.

Usage:  python3 tools/fetch_sources.py
"""

from __future__ import annotations

import csv
import json
import sys
import urllib.error
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
IMS_RAINFALL = (
    "https://tiledimageservices8.arcgis.com/JcXY3lLZni6BK4El/arcgis/rest/services/Mean_1991_2020/ImageServer"
)
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


def isohyets() -> None:
    import lerc
    import matplotlib
    import numpy as np
    import yaml
    from pyproj import Transformer

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    splits = yaml.safe_load((CONTENT / "regions.yaml").read_text()).get("climate_splits", {})
    wanted = sorted({s["isohyet_mm"] for s in splits.values()})
    print(f"fetching the IMS rainfall normals 1991-2020 for the isohyets {wanted} mm")
    info = json.loads(get(IMS_RAINFALL + "?f=json"))
    tile = info["tileInfo"]
    ox, oy = tile["origin"]["x"], tile["origin"]["y"]
    px = tile["lods"][0]["resolution"]
    size = tile["rows"] * px
    ext = info["extent"]
    cols = range(int((ext["xmin"] - ox) // size), int((ext["xmax"] - ox) // size) + 1)
    rows = range(int((oy - ext["ymax"]) // size), int((oy - ext["ymin"]) // size) + 1)
    grid = np.vstack([
        np.hstack([np.asarray(lerc.decode(get(f"{IMS_RAINFALL}/tile/0/{r}/{c}"))[1], dtype=float) for c in cols])
        for r in rows
    ])
    # Cells outside Israel come as zeros.
    grid[grid <= 0] = np.nan
    xs = ox + cols[0] * size + px * (np.arange(grid.shape[1]) + 0.5)
    ys = oy - rows[0] * size - px * (np.arange(grid.shape[0]) + 0.5)
    to_ll = Transformer.from_crs("EPSG:2039", "EPSG:4326", always_xy=True)
    contours = plt.contour(xs, ys, np.ma.masked_invalid(grid), levels=wanted)
    features = []
    for mm, segments in zip(wanted, contours.allsegs):
        longest = max(segments, key=len)  # the main line; small closed loops are left out
        line = LineString([to_ll.transform(x, y) for x, y in longest]).simplify(SIMPLIFY_DEG / 2)
        features.append({"type": "Feature", "properties": {"rainfall_mm": mm}, "geometry": rounded(line)})
    out = CONTENT / "isohyets.geojson"
    out.write_text(collection(features, "Israel Meteorological Service, mean annual rainfall 1991-2020"))
    print(f"  {out.relative_to(ROOT)}: {len(features)} lines")


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


OVERPASS = [
    "https://overpass-api.de/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
]
# Israel and its edges in four bands, south to north (south, west, north, east, the
# Overpass order): small queries are answered when one big one times out.
ISRAEL_BANDS = ["29.4,34.2,30.6,35.95", "30.6,34.2,31.6,35.95", "31.6,34.2,32.5,35.95", "32.5,34.2,33.4,35.95"]
# Road classes that carry a highway's number; ramps and side roads are left out.
ROAD_CLASSES = "motorway|trunk|primary|secondary"
# Road queries are kept here once answered, so a rerun after a failure fetches only the rest.
# Delete the folder to fetch the roads again.
OSM_CACHE = ROOT / "tools" / "raw" / "osm_places"


def overpass(query: str) -> dict:
    """Runs an Overpass query on the first server that answers (they are often busy)."""
    import time

    data = urllib.parse.urlencode({"data": query}).encode()
    for attempt in range(4):
        for url in OVERPASS:
            req = urllib.request.Request(url, data=data, headers={"User-Agent": USER_AGENT})
            try:
                with urllib.request.urlopen(req, timeout=300) as r:
                    return json.loads(r.read())
            except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
                print(f"  {url}: {e}")
        time.sleep(30 * (attempt + 1))
    sys.exit("no Overpass server answered")


def osm_feature(ref: str):
    """An OSM way or relation as an area (closed ways, multipolygons, boundaries) or as lines
    (rivers: the relation's main stream, or all its ways when it has no roles), with its name."""
    kind, _, osm_id = ref.partition("/")
    osm = ET.fromstring(get(f"https://api.openstreetmap.org/api/0.6/{kind}/{osm_id}/full"))
    nodes = {n.get("id"): (float(n.get("lon")), float(n.get("lat"))) for n in osm.iter("node")}
    ways = {w.get("id"): [nodes[nd.get("ref")] for nd in w.iter("nd")] for w in osm.iter("way")}
    if kind == "way":
        coords = ways[osm_id]
        tags = {t.get("k"): t.get("v") for t in next(w for w in osm.iter("way") if w.get("id") == osm_id).iter("tag")}
        return (Polygon(coords) if coords[0] == coords[-1] else LineString(coords)), tags
    roles, tags = osm_relation(int(osm_id))
    if tags.get("type") in ("multipolygon", "boundary"):
        return osm_area(int(osm_id))
    main = roles.get("main_stream") or [l for ls in roles.values() for l in ls]
    return ops.linemerge(main), tags


def osm_places() -> None:
    """The geometry of places.yaml entries taken from OpenStreetMap: "osm:<way|relation>/<id>"
    (rivers, craters) and "road:<number>" (every way of the highway with that number)."""
    import yaml

    sources = [p["geometry"] for p in yaml.safe_load((CONTENT / "places.yaml").read_text())["places"]]
    features = []
    for source in sources:
        kind, _, arg = source.partition(":")
        if kind == "osm":
            print(f"fetching OSM {arg}")
            geom, tags = osm_feature(arg)
            name = tags.get("name")
        elif kind == "road":
            print(f"fetching road {arg}")
            found = []
            for band in ISRAEL_BANDS:
                cached = OSM_CACHE / f"road_{arg}_{band}.json"
                if not cached.exists():
                    result = overpass(f'[out:json][timeout:200];way["highway"~"^({ROAD_CLASSES})$"]["ref"="{arg}"]'
                                      f'({band});out geom;')
                    cached.parent.mkdir(parents=True, exist_ok=True)
                    cached.write_text(json.dumps(result["elements"]))
                found += [w for w in json.loads(cached.read_text()) if w["id"] not in {f["id"] for f in found}]
            if not found:
                sys.exit(f"no ways with ref {arg}")
            # Both carriageways of a divided road merge into one line once simplified.
            parts = [LineString([(p["lon"], p["lat"]) for p in w["geometry"]]) for w in found]
            geom = ops.linemerge(ops.unary_union(parts))
            name = f"road {arg}, {len(found)} ways"
        else:
            continue
        features.append({"type": "Feature", "properties": {"source": source, "name": name},
                         "geometry": rounded(geom.simplify(SIMPLIFY_DEG / 2))})
    out = CONTENT / "osm_places.geojson"
    out.write_text(collection(features, "OpenStreetMap contributors, ODbL 1.0"))
    print(f"  {out.relative_to(ROOT)}: {len(features)} places, {out.stat().st_size // 1024} KB")


COPERNICUS_DEM = "https://copernicus-dem-90m.s3.amazonaws.com/{name}/{name}.tif"
# The elevation grids: name, ITM extent (minx, miny, maxx, maxy), cell size in meters, and how
# many 90 m source cells are averaged per side before sampling. The detail grid covers Israel's
# home view (map.json "home") with 12 km to spare, sharper than a phone screen shows it at that
# zoom. The map styles show terrain over Israel only, so nothing is fetched for the neighbors.
ELEVATION_GRIDS = [
    ("detail", (113600, 366000, 296100, 816500), 100, 1),
]


def elevation() -> None:
    """Resamples the Copernicus DEM GLO-90 tiles onto the ITM elevation grids."""
    import tempfile

    import numpy as np
    import tifffile
    from pyproj import Transformer

    lats, lons = range(28, 35), range(32, 38)  # whole-degree tiles covering the window's corners
    n = 1200  # 3 arc seconds per cell below 50 degrees latitude
    mosaic = np.zeros((len(lats) * n, len(lons) * n), np.float32)  # tiles missing over the sea stay 0
    with tempfile.TemporaryDirectory() as tmp:
        for i, lat in enumerate(lats):
            for j, lon in enumerate(lons):
                name = f"Copernicus_DSM_COG_30_N{lat:02d}_00_E{lon:03d}_00_DEM"
                path = Path(tmp) / f"{name}.tif"
                try:
                    with urllib.request.urlopen(COPERNICUS_DEM.format(name=name), timeout=300) as r:
                        path.write_bytes(r.read())
                except urllib.error.HTTPError as e:
                    if e.code in (403, 404):
                        print(f"  no tile {name} (sea)")
                        continue
                    raise
                tile = tifffile.imread(path)
                if tile.shape != (n, n):
                    sys.exit(f"{name}: unexpected size {tile.shape}")
                row = (len(lats) - 1 - i) * n
                mosaic[row:row + n, j * n:(j + 1) * n] = tile
                print(f"  {name}")

    to_lonlat = Transformer.from_crs("EPSG:2039", "EPSG:4326", always_xy=True)
    north, west = lats[-1] + 1, lons[0]
    meta = {
        "source": "Copernicus DEM GLO-90 (c) DLR e.V. 2010-2014 and (c) Airbus Defence and Space GmbH 2014-2018, "
                  "provided under COPERNICUS by the European Union and ESA",
        "fetched": date.today().isoformat(),
        "crs": "EPSG:2039",
        "format": "int16 little endian meters, rows north to south, each row as differences from the cell before",
        "grids": [],
    }
    for name, (minx, miny, maxx, maxy), cell, k in ELEVATION_GRIDS:
        h, w = mosaic.shape[0] // k, mosaic.shape[1] // k
        source = mosaic[:h * k, :w * k].reshape(h, k, w, k).mean(axis=(1, 3))
        step = k / n  # degrees per source cell
        width, height = int(np.ceil((maxx - minx) / cell)), int(np.ceil((maxy - miny) / cell))
        xs = minx + (np.arange(width) + 0.5) * cell
        ys = maxy - (np.arange(height) + 0.5) * cell
        lon, lat = to_lonlat.transform(*np.meshgrid(xs, ys))
        # Bilinear sampling between source cell centers.
        fr, fc = (north - lat) / step - 0.5, (lon - west) / step - 0.5
        r0 = np.clip(np.floor(fr).astype(int), 0, h - 2)
        c0 = np.clip(np.floor(fc).astype(int), 0, w - 2)
        dr, dc = np.clip(fr - r0, 0, 1), np.clip(fc - c0, 0, 1)
        z = (source[r0, c0] * (1 - dr) * (1 - dc) + source[r0 + 1, c0] * dr * (1 - dc)
             + source[r0, c0 + 1] * (1 - dr) * dc + source[r0 + 1, c0 + 1] * dr * dc)
        z = np.round(z).astype(np.int32)
        deltas = np.diff(z, axis=1, prepend=0).astype("<i2")
        out = CONTENT / f"elevation_{name}.bin"
        out.write_bytes(deltas.tobytes())
        meta["grids"].append({"name": name, "minX": minx, "maxY": maxy, "cell": cell, "width": width, "height": height})
        print(f"  {out.relative_to(ROOT)}: {width} x {height}, {z.min()} to {z.max()} m")
    (CONTENT / "elevation.json").write_text(json.dumps(meta, indent=2) + "\n")


def main() -> int:
    natural_regions()
    localities()
    jordan_river()
    region_overrides()
    isohyets()
    surroundings()
    palestine()
    osm_places()
    elevation()
    return 0


if __name__ == "__main__":
    sys.exit(main())
