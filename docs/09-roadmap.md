# 09. Roadmap

Phases are ordered by learning value first. Each ends with a concrete exit test.
Sizes are relative (S, M, L) because team size is not yet decided.

## Phase 0: Foundations and prototype (M)

Goal: prove the core loop feels good and teaches.

- [ ] Decide open questions marked "blocking" in [10-risks-and-open-questions.md](10-risks-and-open-questions.md)
- [ ] Android project skeleton (modules, Compose, Hilt, Room, CI)
- [ ] Data pipeline v0: Natural Earth + OSM extract for the frame and 30 places
- [ ] Map renderer v0: outline, sea, points, pan/zoom, hit testing
- [ ] FSRS port with reference test vectors
- [ ] Pin It mode with distance feedback
- [ ] Minimal daily session (reviews + new items, short-term steps)

**Exit test:** 5 to 8 testers play daily for one week; average error on the 30 places
drops clearly and testers want to keep going.

## Phase 1: MVP (L)

Goal: a complete, polished learning product for Tiers 0 to 3.

- [ ] Content: Tiers 0 to 3 (about 130 places) in Hebrew and English, reviewed
- [ ] Modes: Discover, Pin It, Name It (MC and typed), Which Way
- [ ] Full session composer (caps, siblings, confusables, 85 percent targeting)
- [ ] Facet ladder and hint ladder
- [ ] Mastery map home screen, session summary, progress screen
- [ ] Onboarding with placement test
- [ ] Daily reminder, streak with freezes
- [ ] Accessibility pass, RTL pass, dark mode
- [ ] Local analytics and pre/post test

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

- [ ] Accounts (optional) and cloud sync
- [ ] Daily Challenge with shareable result
- [ ] Friends and weekly leagues
- [ ] Store listing, screenshots, video in he/en
- [ ] Public launch on Google Play

## Phase 4: Growth (open ended)

- [ ] Arabic localization
- [ ] Tier 7 deep cuts, downloadable content packs
- [ ] Teacher / classroom mode with dashboards
- [ ] Personal FSRS parameter optimization on device
- [ ] Photo mode (identify the place from a landscape photo)
- [ ] iOS (Kotlin Multiplatform for `:core:learning` and `:core:geo` makes this cheaper)

## Suggested first sprint (concrete next steps)

1. Create the Android project skeleton with the module layout from doc 05.
2. Write `tools/` pipeline to export the coastline, neighbors, Kinneret, Dead Sea,
   Jordan River, and 30 cities.
3. Implement `:core:geo` (projection, distance, point-in-polygon) with tests.
4. Port FSRS into `:core:learning` with reference vectors.
5. Build the Compose map renderer and a single Pin It screen.
6. Wire a minimal session and play it every day.
