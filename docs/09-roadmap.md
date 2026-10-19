# 09. Roadmap

Phases are ordered by learning value first. Each ends with a concrete exit test.
Sizes are relative (S, M, L). The team is one developer working with Claude Code, so
scope is kept tight and every phase ships something usable.

## Phase 0: Foundations and prototype (M) - built

Goal: prove the core loop feels good and teaches.

- [x] Android project skeleton (modules, Compose, Room, CI)
- [x] Data pipeline v0: Natural Earth + GeoNames for the frame and 30 places
- [x] Map renderer v0: land, neighbors, Gaza, lakes, Jordan River, pan/zoom, hit testing
- [x] FSRS port with reference test vectors (matches py-fsrs 6.3.2)
- [x] Pin It mode with distance feedback
- [x] Minimal daily session (reviews + new items, short-term steps, extra practice)
- [ ] Playtest: 5 to 8 testers for one week

**Exit test:** 5 to 8 testers play daily for one week; average error on the 30 places
drops clearly and testers want to keep going.

What Phase 0 simplified, on purpose:
- One skill per place (`LOCATE`); `NAME` and `RELATE` arrive in Phase 1.
- Geometry comes from Natural Earth and GeoNames instead of OpenStreetMap; that is
  enough detail for the frame and cities. OSM comes with regions and roads.
- Manual dependency wiring instead of Hilt, and three pure Kotlin modules
  (`core:geo`, `core:content`, `core:learning`) plus `app`, instead of the full module
  split in doc 05. Split further when features grow.
- The Green Line layer is in the map data but has no settings switch yet (Phase 1).
- Pin It tolerances (3 km for cities) are first guesses, to tune in the playtest.

## Phase 1: MVP (L)

Goal: a complete, polished learning product for Tiers 0 to 3. Built as four smaller
releases, each playable on its own.

### 1a. Learning core - built
- [x] Skills per place: `LOCATE`, `NAME`, `RELATE`, each with its own FSRS card
- [x] Name It: multiple choice (confusables and nearby places as distractors), then
      typed once stable, with Hebrew spelling-variant matching and aliases
- [x] Which Way: further north/south/east/west, and closer to a third known city
- [x] Facet ladder: name unlocks at 2 days of LOCATE stability, relations at 3
- [x] Sibling rule: one skill per place per session
- [x] Hint ladder: two misses in a row bring a hint (text hint, or choices instead of
      typing); hinted answers count at most HARD; a success removes the hint
- [x] Confusable pairs asked back to back once both are known
- [x] New places per session adapt to recent accuracy (85 percent target)
- [x] Feedback names the city you tapped when you missed
- [x] Database migration keeps Phase 0 progress

### 1b. Content
- [ ] Tier 1: districts and natural regions (polygons)
- [ ] Tiers 2 and 3: about 100 more cities and towns, with aliases and confusables
- [ ] Map shows region outlines

### 1c. Player experience
- [ ] Onboarding with placement test
- [ ] Progress screen (region percentages, history of average error)
- [ ] Daily reminder, streak with freezes
- [ ] Settings: Green Line layer, session length
- [ ] Accessibility pass, dark mode

### 1d. Measurement
- [ ] Local learning stats and pre/post map test

**Exit test:** closed beta; retention at review between 85 and 92 percent; pre/post
test shows large gains; D7 retention benchmark agreed in advance.

## Phase 2: Depth and variety (L)

- [ ] Tiers 4 to 6 (nature, infrastructure, heritage)
- [ ] Map Builder (drag regions) and Trace It (rivers, roads)
- [ ] Road Trip story mode
- [ ] Blitz mode
- [ ] Achievements and ranks
- [ ] Learned confusable pairs
- [ ] Opt-in anonymous log upload; first global FSRS parameter fit

**Exit test:** players who finish Tier 3 continue into Tier 4+ at a healthy rate.

## Phase 3: Social and launch (M)

- [ ] Choose backend (Supabase or Firebase)
- [ ] Accounts (optional) and cloud sync
- [ ] Daily Challenge with shareable result
- [ ] Friends and weekly leagues
- [ ] Store listing, screenshots, video in Hebrew
- [ ] Public launch on Google Play

## Phase 4: Growth (open ended)

- [ ] Tier 7 deep cuts, downloadable content packs
- [ ] Teacher / classroom mode with dashboards
- [ ] Personal FSRS parameter optimization on device
- [ ] Photo mode (identify the place from a landscape photo)
- [ ] iOS (Kotlin Multiplatform for `:core:learning` and `:core:geo` makes this cheaper)

## Next steps

1. Install the APK from the README's download link on real phones and run the
   Phase 0 playtest.
2. Tune tolerances and session size from the playtest.
3. Phase 1b: regions and the rest of Tiers 2 to 3.
