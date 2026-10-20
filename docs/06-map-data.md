# 06. Map Data and Content Pipeline

## Sources

| Data | Source | License | Use |
|------|--------|---------|-----|
| Detailed boundaries, rivers, roads, places, multilingual names | OpenStreetMap via Geofabrik regional extract | ODbL 1.0 (attribution + share-alike on the derived database) | Most geometry |
| Official locality list, codes, Hebrew names, population, district | Israel Central Bureau of Statistics (CBS) locality file, published on data.gov.il | Israeli government open data terms (verify) | Canonical list of localities and their attributes |
| Multilingual names, Wikipedia links, extra facts | Wikidata | CC0 | Hebrew names and aliases, cross-checking, fact research |
| Israel's outline, natural regions, districts, Kinneret and Dead Sea shores | Israel Central Bureau of Statistics, official natural regions (אזורים טבעיים) 2018, public ArcGIS layer; Judea and Samaria regions (codes 711 to 815) from the Ministry of Agriculture's 2025 layer, which uses the same codes | Israeli government data | Land outline, Tier 1 regions and districts, region outlines |
| City and town points | CBS locality file 2023 (data.gov.il): official point, natural region and district of every locality | Israeli government open data | All Israeli cities and towns |
| Palestinian cities | GeoNames; OpenStreetMap for Jericho (GeoNames is 1.4 km off there) | CC BY 4.0; ODbL | Hebron, Bethlehem, Jericho, Shechem, Ramallah |
| Jordan River | OpenStreetMap relation 2246907 (main stream) | ODbL 1.0 | Frame item and map layer |
| Coastlines, neighboring countries, the whole Dead Sea | OpenStreetMap: land polygons from osmdata.openstreetmap.de (built from the coastline), country relations (Lebanon 184843, Syria 184840, Jordan 184818, Egypt 1473947, Saudi Arabia 307584), Dead Sea relation 9351124 | ODbL 1.0 | Neighbors, seas, the Dead Sea's northern basin |
| Judean Desert's western edge | Israel Meteorological Service, mean annual rainfall 1991-2020 (400 m grid, published by the CBS as a public ArcGIS image service); the 250 mm line is traced from it | Israeli government data | Splitting the Judea regions between the Judean Mountains and the Judean Desert |
| Gaza Strip, Green Line | UN OCHA common operational dataset for the State of Palestine (HDX "cod-ab-pse"); the West Bank's western edge is the 1949 armistice line, with East Jerusalem and the Latrun no-man's-land on its eastern side | CC BY-IGO | Gaza, the optional Green Line layer |

**Status (Phase 1b):** `tools/fetch_sources.py` downloads the official natural regions,
the CBS locality file and the Jordan River into `content/`, where they are committed so
the build is reproducible and offline; Natural Earth is no longer used. Checked against
known border points (Taba, Kerem Shalom), the official outline is within about 20 m;
Natural Earth's was 150 to 600 m off there and up to a few kilometers along the
Egyptian and Jordanian borders.

How the map is built:
- **Land:** the union of all official natural regions (Israel, the Golan, Judea and
  Samaria), with the gaps where the CBS and Ministry layers meet closed.
- **Natural regions:** `content/regions.yaml` groups the about 60 official regions into
  the game's 19 (for example the Sharon is the Hadera region plus western, eastern and
  southern Sharon). Every code is used exactly once, or the build fails. Where the
  official grouping and the geographic definitions disagree, `content/regions.yaml`
  moves areas, each with its source:
  - the Judean Desert takes the parts of the Judea regions (812, 813, 815) east of the
    250 mm rainfall line, its western edge in Hebrew Wikipedia's definition (traced from
    the IMS 1991-2020 normals; checked against Jerusalem 523 mm, Tel Aviv 522 mm,
    Be'er Sheva 192 mm and Eilat 23 mm). Ma'ale Adumim (260 to 280 mm, just above the
    line) also joins it: both Wikipedias, and definitions of the desert as the slopes
    east of the watershed (CET youth encyclopedia, English Wikipedia), place it there;
  - Herzliya, Ramat HaSharon, Rosh HaAyin and Kafr Qasim (their OSM municipal areas)
    move from Gush Dan to the Sharon, which begins at the Yarkon; Hebrew Wikipedia
    places each of them in the Sharon;
  - Jericho's town area moves to the Jordan Valley (the Judea and Samaria layer follows
    Israeli council lines, which put it in "Judea").
  The build checks each city against its CBS natural region, allowing for these moves.
- **Districts:** every official natural region lies in one district and its code starts
  with the district's number, so the districts are unions of natural regions.
- **Checks:** every Israeli city must lie in the natural region and the district the CBS
  locality file gives it, and within 300 m of the land.
- **Neighbors and Gaza:** OSM's land, split by the OSM country boundaries and the UN
  OCHA Gaza boundary. Where OSM's land and Israel's official outline disagree, the strip
  goes to whatever lies next to it: a neighbor, Gaza or the sea.
- **Lakes:** the official Kinneret; the Dead Sea is OSM's northern basin (both halves)
  plus the official southern ponds.
- **Green Line:** the UN OCHA West Bank boundary where it faces Israel.
- Regions and districts are each simplified together (150 m), so neighbors keep one
  shared border.

- **Terrain (relief and topographic styles):** the Copernicus DEM GLO-90 (90 m),
  sampled onto two grids on the Israeli TM projection: 300 m over the map window
  (box-averaged to 270 m first) and 100 m over Israel's home view with 12 km to spare
  (`content/elevation_coarse.bin`, `content/elevation_detail.bin`, described by
  `content/elevation.json`; each row stored as differences from the cell before, which
  compresses three times better). The app draws the relief from them on the phone,
  and the contour lines as tiles for what is on screen, with the interval set by the
  zoom (core/content `Contours`). `tools/build_content.py` places the heights written along the
  index contours (`contour_labels.json`), traced on the detail grid after the same
  smoothing the app applies, so each label sits on its line.

Licensing notes:
- The Copernicus DEM is free to use with attribution: "Copernicus DEM (c) DLR e.V.
  2010-2014 and (c) Airbus Defence and Space GmbH 2014-2018, provided under COPERNICUS
  by the European Union and ESA". The map credit names it.
- ODbL requires visible attribution ("© OpenStreetMap contributors") in the app, for
  example on the About screen and a small map credit. If we distribute the derived
  geometry database, it must be offered under ODbL. The app code itself is not
  affected.
- Record each source's exact version / download date in `tools/sources.lock`.

## Pipeline (in `tools/`, Python, not shipped)

```
1. fetch       Download pinned versions of each source into tools/raw/ (gitignored)
2. extract     osmium / ogr2ogr: pull needed features (admin boundaries, place=*, 
               waterway=river, highway=motorway|trunk, natural=peak, tourism/historic)
3. join        Match OSM features to CBS localities by code / name; attach Wikidata ids
4. curate      Apply content/places.yaml (hand-edited): which items are in the game,
               tier, unit, anchors, facts, confusables, overrides
5. simplify    mapshaper: 2 to 3 detail levels, topology preserved between regions
6. project     Reproject to EPSG:2039 (or local TM), round to integer meters
7. validate    Checks below
8. package     Write app/src/main/assets/geometry.bin, places.json, i18n/*.json
9. report      HTML preview of every item on a map for human review
```

Hand-curated content (`content/places.yaml`, facts, translations) lives in the repo
and is the source of truth for *what* is taught. Raw downloads are never committed.

## Validation checks

- Every place in `places.yaml` has geometry, he and en names, a type, tier, unit.
- Every `anchor`, `prereq`, and `confusable` id exists.
- No cycles in prerequisites.
- Point items lie inside the land polygon (except sea items).
- Polygon validity (no self intersections after simplification) and shared borders
  between adjacent regions stay shared.
- Asset size budget (fail if geometry.bin exceeds the target).
- Name collisions flagged for the typing-mode answer matcher.

## Name matching for typed answers

Hebrew names have spelling variants (full vs defective spelling, with or without
niqqud, abbreviations): כפר סבא / כפ"ס, באר שבע / ב"ש, קריית / קרית. For each place
store:

- the canonical Hebrew name,
- accepted alternates (from OSM `name:*`, `alt_name`, Wikidata aliases, hand list),
- a normalized form (strip niqqud, geresh/gershayim, hyphens, spaces; unify yod/vav
  spelling variants).

Match on normalized forms, with a small edit-distance tolerance for long names.

## Human review

Every item goes through a checklist before release: correct location, correct Hebrew
names, fact accurate and neutral, appropriate tier. The pipeline's HTML
report is the review tool.
