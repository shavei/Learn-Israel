"""Draws the app icon from the game's own map: the master icon.svg and the Android drawables.

The icon is drawn as the Survey of Israel's map "Israel 1948-1978" was printed: ochre land with
brown relief, near white neighbors, a flat cyan sea, and red roads. The land, the lakes, the
relief and the seven highways all come from the game's own assets, so the icon shows the land
players learn. On it is the game's moment: a tap at Be'er Sheva, the dotted line to the true
place, and the place, Jerusalem, in the blue of that map's title.
Coordinates are on the 108 unit adaptive icon canvas, north up, with the land 62 units tall so
it fits the 66 unit circle every launcher mask keeps.
Run: python3 make_icon.py (needs shapely, as tools/requirements.txt), then node render.js.
"""
import json
from pathlib import Path

import numpy as np
import shapely
from shapely.geometry import LineString, MultiLineString, Polygon, box
from shapely.ops import unary_union

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res/drawable"
CANVAS, HEIGHT = 108, 62.0

# The colors of the Survey of Israel's 1978 map: ochre land shaded brown on the hills, the
# neighbors near white, a flat cyan sea, deeper lakes, red roads, and the title's blue.
SEA, LAND, SHADE, DEEP, NEIGHBOR, COAST, LAKE, ROAD, INK, MARK = (
    "#A9D3E6", "#F2CC8A", "#DFAE6C", "#C9925A", "#F6F2E7", "#9C6B3C", "#5E9FCB", "#C8352B", "#211E19", "#1E5A82")
JERUSALEM, BEERSHEBA = (220976, 632344), (179500, 573657)  # their points in places.json
RING, RING_WIDTH, CENTER, TAP, DOT = 2.8, 1.5, 1.2, 1.5, 0.55

layers = {l["id"]: l for l in json.loads((ROOT / "app/src/main/assets/map.json").read_text())["layers"]}
land_ring = layers["land"]["polygons"][0][0]
ys = land_ring[1::2]
xs = land_ring[0::2]
scale = HEIGHT / (max(ys) - min(ys))
mid = ((max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2)


def at(p):
    return CANVAS / 2 + (p[0] - mid[0]) * scale, CANVAS / 2 - (p[1] - mid[1]) * scale


def polygon(rings):
    pts = [[at(p) for p in zip(r[0::2], r[1::2])] for r in rings]
    return Polygon(pts[0], pts[1:]).buffer(0)


def shapes(layer):
    return unary_union([polygon(p) for p in layers[layer]["polygons"]])


frame = box(-2, -2, CANVAS + 2, CANVAS + 2)
land = shapes("land")
gaza = shapes("gaza")
lakes = shapes("lakes")
# Past the map window the game paints land and then sea over it, so the ground is land less that sea.
ground = unary_union([land, gaza, shapes("neighbors"), shapes("beyond_land")]).difference(shapes("beyond_sea").buffer(0.3))
# Drop the slivers left where the window meets the land, and any specks.
ground = unary_union([g for g in getattr(ground, "geoms", [ground]) if g.area > 1]).buffer(0.3).buffer(-0.3)
ground = ground.intersection(frame)
sea = frame.difference(ground)


def d(geom, tolerance=0.2, to=lambda p: p):
    """SVG path data of polygons or lines."""
    geom = geom.simplify(tolerance)
    out = []
    for g in getattr(geom, "geoms", [geom]):
        if g.is_empty:
            continue
        if isinstance(g, Polygon):
            for r in [g.exterior, *g.interiors]:
                out.append("M" + "L".join("%.2f,%.2f" % to(p) for p in r.coords[:-1]) + "Z")
        elif isinstance(g, (LineString, MultiLineString)):
            for line in getattr(g, "geoms", [g]):
                out.append("M" + "L".join("%.2f,%.2f" % to(p) for p in line.coords))
    return "".join(out)


def circle(c, r):
    x, y = c
    return f"M{x - r:.2f},{y:.2f}a{r},{r} 0,1 1,{2 * r},0a{r},{r} 0,1 1,{-2 * r},0Z"


def relief():
    """Shaded slopes as two tones, lit from the northwest as printed relief maps are, at 1 km."""
    meta = json.loads((ROOT / "app/src/main/assets/map.json").read_text())["elevation"][0]
    raw = np.fromfile(ROOT / "app/src/main/assets/elevation_israel.bin", "<i2")
    z = np.cumsum(raw.reshape(meta["height"], meta["width"]).astype(np.int32), axis=1)
    k = 10  # 100 m cells into 1 km
    h, w = z.shape[0] // k * k, z.shape[1] // k * k
    z = z[:h, :w].reshape(h // k, k, w // k, k).mean(axis=(1, 3))
    for _ in range(2):  # soften to the scale an icon can show
        z = (z + np.roll(z, 1, 0) + np.roll(z, -1, 0) + np.roll(z, 1, 1) + np.roll(z, -1, 1)) / 5
    cell = meta["cell"] * k
    dzdy, dzdx = np.gradient(z * 2.5, cell)  # rows run south, so -dzdy points north
    slope, aspect = np.arctan(np.hypot(dzdx, dzdy)), np.arctan2(-dzdy, dzdx)
    sun, light = np.radians(45), np.radians(135)  # 45 degrees up, from the northwest
    shade = np.sin(sun) * np.cos(slope) + np.cos(sun) * np.sin(slope) * np.cos(light - aspect)

    def cells(mask):
        boxes = []
        for r, row in enumerate(mask):
            c = 0
            while c < len(row):
                if row[c]:
                    e = c
                    while e < len(row) and row[e]:
                        e += 1
                    x0, x1 = meta["minX"] + c * cell, meta["minX"] + e * cell
                    y0, y1 = meta["maxY"] - r * cell, meta["maxY"] - (r + 1) * cell
                    boxes.append(box(*at((x0, y0)), *at((x1, y1))))
                    c = e
                else:
                    c += 1
        geom = unary_union(boxes).buffer(0.25, quad_segs=2).buffer(-0.25, quad_segs=2)
        return unary_union([g for g in getattr(geom, "geoms", [geom]) if g.area > 0.15]).intersection(land)

    inside = z != 0
    return cells(inside & (shade < np.quantile(shade[inside], 0.28))), cells(inside & (shade < np.quantile(shade[inside], 0.09)))


def roads():
    """The seven highways the game teaches, as the map's red lines."""
    places = json.loads((ROOT / "app/src/main/assets/places.json").read_text())["places"]
    lines = [LineString([at(p) for p in zip(l[0::2], l[1::2])])
             for p in places if p["id"].startswith("road.") for l in p["geometry"]["lines"] if len(l) >= 4]
    return shapely.line_merge(unary_union(lines))


place, tap = at(JERUSALEM), at(BEERSHEBA)
length = ((place[0] - tap[0]) ** 2 + (place[1] - tap[1]) ** 2) ** 0.5
ux, uy = (place[0] - tap[0]) / length, (place[1] - tap[1]) / length
dots = [(tap[0] + ux * t, tap[1] + uy * t) for t in (2.8, 4.6, 6.4, 8.2, 10.0, 11.8) if t < length - RING - RING_WIDTH]

# Each layer as (attributes, path data), shared by the SVG and the vector drawables.
shade, deep = relief()
background = [
    ({"fill": SEA}, "M0,0h108v108h-108z"),
    ({"fill": NEIGHBOR}, d(ground.difference(land))),
    ({"stroke": "#C9C2B2", "stroke-width": 0.35, "stroke-linejoin": "round"}, d(ground.boundary.intersection(frame.buffer(-0.5)))),
]
foreground = [
    ({"fill": LAND}, d(land)),
    ({"fill": SHADE}, d(shade, 0.15)),
    ({"fill": DEEP}, d(deep, 0.15)),
    ({"stroke": ROAD, "stroke-width": 0.5, "stroke-linecap": "round", "stroke-linejoin": "round"}, d(roads(), 0.15)),
    ({"stroke": COAST, "stroke-width": 0.6, "stroke-linejoin": "round"}, d(land)),
    ({"fill": LAKE}, d(lakes.intersection(land.buffer(1)))),
    ({"fill": INK}, "".join(circle(p, DOT) for p in dots) + circle(tap, TAP)),
    ({"fill": LAND, "stroke": MARK, "stroke-width": RING_WIDTH}, circle(place, RING)),
    ({"fill": MARK}, circle(place, CENTER)),
]

ATTR = {"fill": "fillColor", "stroke": "strokeColor", "stroke-width": "strokeWidth",
        "stroke-linecap": "strokeLineCap", "stroke-linejoin": "strokeLineJoin", "fill-rule": "fillType"}


def svg_paths(items):
    return "\n".join(
        "  <path " + " ".join(f'{k}="{v}"' for k, v in ({"fill": "none"} | a).items()) + f' d="{p}"/>'
        for a, p in items)


(Path(__file__).parent / "icon.svg").write_text(f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">
  <!-- Generated by make_icon.py. Background layer: the sea and the neighbors. -->
{svg_paths(background)}
  <!-- Foreground layer: the land, its relief and roads, a tap at Be'er Sheva, the dotted line, and Jerusalem. -->
{svg_paths(foreground)}
</svg>
""")


def vector(comment, items, size=108):
    def attrs(a):
        out = {ATTR[k]: ("evenOdd" if v == "evenodd" else v) for k, v in a.items()}
        return "\n".join(f'        android:{k}="{v}"' for k, v in out.items())

    body = "\n".join(f'    <path\n{attrs(a)}\n        android:pathData="{p}" />' for a, p in items)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Generated by marketing/brand/make_icon.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{size}"
    android:viewportHeight="{size}">
{body}
</vector>
"""


(RES / "ic_launcher_background.xml").write_text(vector(
    "The sea and the neighbors, as on the Survey of Israel's printed map.", background))
(RES / "ic_launcher_foreground.xml").write_text(vector(
    "Israel with its relief and highways, a tap at Be'er Sheva, the dotted line, and Jerusalem.", foreground))
# One color for themed icons: the land, with the tap, the line and the place cut out of it
# (even-odd: the ring is a hole, the space inside it land again, and its center a hole).
holes = [circle(p, DOT) for p in dots] + [circle(tap, TAP), circle(place, RING + RING_WIDTH / 2),
                                          circle(place, RING - RING_WIDTH / 2), circle(place, CENTER)]
(RES / "ic_launcher_monochrome.xml").write_text(vector(
    "Themed icon (Android 13+): the land with the tap, the line and the place cut out.",
    [({"fill": "#FF000000", "fill-rule": "evenodd"}, d(land, 0.25) + "".join(holes))]))
# Notifications are white on transparent: the land, 22dp tall, with Jerusalem cut out.
small = 22 / HEIGHT
to24 = lambda p: (12 + (p[0] - 54) * small, 12 + (p[1] - 54) * small)
(RES / "ic_notification.xml").write_text(vector(
    "The app icon's land, white on transparent as Android requires for notification icons.",
    [({"fill": "#FFFFFFFF", "fill-rule": "evenodd"}, d(land, 0.6, to24) + circle(to24(place), 1.0))], size=24))
