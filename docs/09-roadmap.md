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
- Pin It tolerances are first guesses, to tune in the playtest (Phase 1a made them
  forgiving for new places: 10 km for a new city, tightening to 4 km).

## Phase 1: MVP (L)

Goal: a complete, polished learning product for Tiers 0 to 3. Built as four smaller
releases, each playable on its own.

### 1a. Learning core - built
- [x] Skills per place: `LOCATE`, `NAME`, `RELATE`, each with its own FSRS card
- [x] Name It: multiple choice (confusables and nearby places as distractors), then
      typed once stable, with Hebrew spelling-variant matching and aliases; one
      typo on a longer name still counts as correct (feedback shows the spelling)
- [x] Which Way: further north/south/east/west, and closer to a third known city
- [x] Facet ladder: naming and relations unlock once finding the place has been
      stable for a week
- [x] Sibling rule: one skill per place per session
- [x] Hint ladder: two misses in a row bring a hint (text hint, or choices instead of
      typing); hinted answers count at most HARD; a success removes the hint
- [x] Confusable pairs asked back to back once both are known
- [x] New places per session adapt to recent accuracy (85 percent target)
- [x] Feedback names the city you tapped when you missed
- [x] Pin It tolerance widens for new places (10 km for a new city) and tightens to
      4 km as the place becomes well known
- [x] Database migration keeps Phase 0 progress

### 1b. Content - built
- [x] Tier 1: 19 natural regions and the 7 districts (6 districts plus the Judea and
      Samaria Area), as polygons. Natural regions are unions of the official CBS
      natural regions (Ministry of Agriculture regions for Judea and Samaria), and the
      districts are built from the same regions
- [x] Map outline, lakes and city points from official data (CBS); the Jordan River
      from OpenStreetMap; Natural Earth only for the neighboring countries
- [x] Tiers 2 and 3: 97 more cities and towns (118 in all), with aliases and confusables
- [x] Map shows region outlines (faint dashed borders between natural regions)
- [x] Name It offers names of the same kind (regions with regions, districts with
      districts); a missed tap on a region names the region you tapped
- [x] Every city is tagged with its natural region in `places.json`, ready for the
      region percentages in 1c
- [ ] Human review of region borders, Hebrew names, hints and facts
      (see `docs/images/phase1b-regions.png`)

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
   playtest.
2. Review the 1b content: region borders, Hebrew names, hints and facts.
3. Tune tolerances and session size from the playtest.
4. Phase 1c: onboarding, progress screen with region percentages, reminders, settings.
