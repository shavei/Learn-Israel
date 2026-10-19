# 06. Map Data and Content Pipeline

## Sources

| Data | Source | License | Use |
|------|--------|---------|-----|
| Coastline, neighbors, seas, lakes | Natural Earth (1:10m) | Public domain | Frame layer, background |
| Detailed boundaries, rivers, roads, places, multilingual names | OpenStreetMap via Geofabrik regional extract | ODbL 1.0 (attribution + share-alike on the derived database) | Most geometry |
| Official locality list, codes, Hebrew names, population, district | Israel Central Bureau of Statistics (CBS) locality file, published on data.gov.il | Israeli government open data terms (verify) | Canonical list of localities and their attributes |
| Multilingual names, Wikipedia links, extra facts | Wikidata | CC0 | Hebrew names and aliases, cross-checking, fact research |
| Administrative districts | OSM relations (admin_level 4), fetched with the main OSM API by `tools/fetch_districts.py` into `content/districts.geojson` | ODbL 1.0 | Tier 1 district polygons |
| Natural regions | Israel Central Bureau of Statistics, official natural regions (אזורים טבעיים) 2018, public ArcGIS layer; Judea and Samaria regions (codes 711 to 815) from the Ministry of Agriculture's 2025 layer. Fetched by `tools/fetch_natural_regions.py` into `content/natural_regions.geojson` | Israeli government data | Tier 1 region polygons, region outlines on the map |

**Status (Phase 1b):** the pipeline uses Natural Earth (frame), GeoNames (city points,
via `geonamescache`, plus a few explicit `point:` coordinates for towns too small for
it), committed OSM district boundaries, and the committed official natural regions. Geofabrik and Overpass
were unreachable from the build environment, so the district file is fetched once from
the main OSM API and committed; rerun `tools/fetch_districts.py` to refresh it.

How the natural regions are built: the CBS divides Israel into about 50 official
natural regions. `content/regions.yaml` groups them into the game's 19 regions (for
example the Sharon is the Hadera region plus western, eastern and southern Sharon).
The pipeline clips each group to the map's land and lakes. The official borders and the
Natural Earth coast and borders disagree by up to a few hundred meters, so the leftover
strips go to the nearest region, and the build fails if more than 1 km2 of land is
still in no region. The regions are then simplified together, so neighbors keep one
shared border. Districts get the same treatment, so the strips where OSM's boundary and the
Natural Earth coast disagree are filled. The Judea and Samaria Area is the de facto land
outside the six districts.

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
