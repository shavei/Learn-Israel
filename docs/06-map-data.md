# 06. Map Data and Content Pipeline

## Sources

| Data | Source | License | Use |
|------|--------|---------|-----|
| Coastline, neighbors, seas, lakes | Natural Earth (1:10m) | Public domain | Frame layer, background |
| Detailed boundaries, rivers, roads, places, multilingual names | OpenStreetMap via Geofabrik regional extract | ODbL 1.0 (attribution + share-alike on the derived database) | Most geometry |
| Official locality list, codes, Hebrew/English names, population, district | Israel Central Bureau of Statistics (CBS) locality file, published on data.gov.il | Israeli government open data terms (verify) | Canonical list of localities and their attributes |
| Multilingual names, Wikipedia links, extra facts | Wikidata | CC0 | Names in he/en/ar, cross-checking, fact research |
| Administrative districts and natural regions | CBS geographic files and OSM relations | As above | Tier 1 polygons |

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

Hebrew and English names have many spellings (Kfar Saba / Kfar Sava, Rishon LeZion /
Rishon Lezion, Be'er Sheva / Beersheba). For each place store:

- canonical names per language,
- accepted alternates (from OSM `name:*`, `alt_name`, Wikidata aliases, hand list),
- a normalized form (lowercase, strip niqqud, apostrophes, hyphens, "the").

Match on normalized forms, with a small edit-distance tolerance for long names.

## Human review

Every item goes through a checklist before release: correct location, correct names
in each language, fact accurate and neutral, appropriate tier. The pipeline's HTML
report is the review tool.
