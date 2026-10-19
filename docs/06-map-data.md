# 06. Map Data and Content Pipeline

## Sources

| Data | Source | License | Use |
|------|--------|---------|-----|
| Coastline, neighbors, seas, lakes | Natural Earth (1:10m) | Public domain | Frame layer, background |
| Detailed boundaries, rivers, roads, places, multilingual names | OpenStreetMap via Geofabrik regional extract | ODbL 1.0 (attribution + share-alike on the derived database) | Most geometry |
| Official locality list, codes, Hebrew names, population, district | Israel Central Bureau of Statistics (CBS) locality file, published on data.gov.il | Israeli government open data terms (verify) | Canonical list of localities and their attributes |
| Multilingual names, Wikipedia links, extra facts | Wikidata | CC0 | Hebrew names and aliases, cross-checking, fact research |
| Israel's outline, natural regions, districts, Kinneret and Dead Sea shores | Israel Central Bureau of Statistics, official natural regions (אזורים טבעיים) 2018, public ArcGIS layer; Judea and Samaria regions (codes 711 to 815) from the Ministry of Agriculture's 2025 layer, which uses the same codes | Israeli government data | Land outline, Tier 1 regions and districts, region outlines |
| City and town points | CBS locality file 2023 (data.gov.il): official point, natural region and district of every locality | Israeli government open data | All Israeli cities and towns |
| Palestinian cities | GeoNames; OpenStreetMap for Jericho (GeoNames is 1.4 km off there) | CC BY 4.0; ODbL | Hebron, Bethlehem, Jericho, Shechem, Ramallah |
| Jordan River | OpenStreetMap relation 2246907 (main stream) | ODbL 1.0 | Frame item and map layer |

**Status (Phase 1b):** `tools/fetch_sources.py` downloads the official natural regions,
the CBS locality file and the Jordan River into `content/`, where they are committed so
the build is reproducible and offline. Natural Earth is used only for the neighboring
countries, Gaza and the Jordanian half of the Dead Sea. Checked against known border
points (Taba, Kerem Shalom), the official outline is within about 20 m; Natural Earth's
was 150 to 600 m off there and up to a few kilometers along the Egyptian and Jordanian
borders.

How the map is built:
- **Land:** the union of all official natural regions (Israel, the Golan, Judea and
  Samaria), with the gaps where the CBS and Ministry layers meet closed.
- **Natural regions:** `content/regions.yaml` groups the about 60 official regions into
  the game's 19 (for example the Sharon is the Hadera region plus western, eastern and
  southern Sharon). Every code is used exactly once, or the build fails. One
  correction: the Judea and Samaria layer follows Israeli council lines, which put
  Jericho's town area in "Judea"; its OpenStreetMap area is moved to the Jordan Valley
  (`overrides` in `content/regions.yaml`).
- **Districts:** every official natural region lies in one district and its code starts
  with the district's number, so the districts are unions of natural regions.
- **Checks:** every Israeli city must lie in the natural region and the district the CBS
  locality file gives it, and within 300 m of the land.
- **Neighbors:** land that Natural Earth gives Israel beyond the official outline goes to
  whatever lies next to it: the neighbor country, Gaza or the sea.
- Regions and districts are each simplified together (150 m), so neighbors keep one
  shared border.

Licensing notes:
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
