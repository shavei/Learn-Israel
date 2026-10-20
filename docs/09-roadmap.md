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
- [x] Map outline, lakes and city points from official data (CBS); coastlines,
      neighbors, the Dead Sea and the Jordan River from OpenStreetMap; Gaza and the
      Green Line from UN OCHA
- [x] Tiers 2 and 3: 97 more cities and towns (118 in all), with aliases and confusables
- [x] Map shows region outlines (faint dashed borders between natural regions)
- [x] Name It offers names of the same kind (regions with regions, districts with
      districts); a missed tap on a region names the region you tapped
- [x] Every city is tagged with its natural region in `places.json`, ready for the
      region percentages in 1c
- [ ] Human review of region borders, Hebrew names, hints and facts
      (see `docs/images/phase1b-regions.png`)

### 1c. Player experience - built
- [x] Onboarding: a welcome screen, then the first session or an optional placement
      test (about 3 minutes, adaptive: the anchors first, then each unit's most
      important places; found places are seeded for review in two days)
- [x] Progress screen: known places, streak, history of average city error per day,
      and a percentage for each of the 19 natural regions
- [x] Daily reminder, offered once after the first session (never on first run);
      quiet on days already played, every third day after a week away
- [x] Streak with freezes: one freeze per 7 days of streak, up to 2 saved; a freeze
      covers one missed day
- [x] Settings: session length (10, 15 or 25 questions), daily reminder and its time,
      Green Line layer, light / dark / system theme
- [x] Dark mode with its own map palette, not an inversion
- [x] Accessibility pass: text colors at WCAG AA contrast in both themes; known places
      get a heavier outline (not color alone); cards scroll instead of covering the map
      at large font sizes; headings, live regions for questions and feedback, spoken
      descriptions of the map, the chart and the region bars; whole-row switches

What 1c simplified, on purpose:
- Settings live in SharedPreferences and the reminder is an inexact AlarmManager
  alarm, instead of DataStore and WorkManager (doc 05): same behavior, no new
  dependencies. Navigation is a small screen enum, not Navigation Compose.
- Units are not locked in the engine, so placement does not unlock units: new places
  come in curriculum order and the found ones are already out of the queue.
- Not yet: the screen reader mode built on Name It and Which Way, the Pin It
  tolerance multiplier and tap-then-confirm (doc 07), and the welcome-back flow.

### 1d. Measurement - built
- [x] Pre/post map test: the same 20 places every time (two lakes, three anchors, four
      natural regions, eleven cities from every part of the country), Pin It with no
      feedback and no card changes, scored at the standard tolerance (4 km for a city)
      with the median error. Optional on the welcome screen before anything else (the
      pre-test); offered on home 30 days after the first practice and then monthly
      (the post-test), with "later" asking again in 3 days
- [x] Every answer logs what the stats and later parameter fitting need: the card's
      state and due time before the answer, days since its last review, the
      scheduler's predicted recall, whether a hint was shown, and the place a missed
      tap was nearest to (database version 3; older answers keep these empty)
- [x] Progress screen: map test results and the change since the first one, and
      learning stats for the last 30 days: retention at due reviews against the 85 to
      92 percent band, the scheduler's predicted recall for the same reviews, recall
      after 30+ days without review, confusion rate with confusable twins, days
      practiced in the last 7 and 30 days, and totals
- [x] "Share results" sends the stats and map test results as text, so closed beta
      testers can report them before any upload exists

What 1d simplified, on purpose:
- No upload and no dashboard: beta results come in through the share button. The
  opt-in anonymous log upload stays in Phase 2.
- The long-term retention probe uses ordinary reviews that came after 30+ days, not
  extra probe questions. Calibration is shown as mean predicted vs actual recall, not
  log loss. Engagement cohorts (D1, D7, D30) need many players, so they wait for the
  upload; each player sees their own active days instead.
- Practice time counts the time spent answering, not reading feedback.

### Map styles - built
- [x] Two map styles in settings, each with a preview: standard (flat colors) and
      relief (hypsometric tint, green lowlands to tan hills, under hillshading lit from
      the north-west). A topographic style with contour lines was built and then
      removed: the relief shows height more clearly at the zooms the game is played
      at; whoever had chosen it gets the relief
- [x] The relief comes from the Copernicus DEM GLO-90 on a 100 m grid, over
      Israel only (sharper than a phone shows it at the home zoom); the neighbors stay
      flat, which keeps the elevation data to 2.6 MB of the APK. The phone draws the
      relief into images once, off the main thread, so panning costs no more than the
      standard map
- [x] Each style has its own light and dark design; a relief that is not ready yet
      fades in over the flat map
- [x] Water stays vector: the sea, lakes, borders and region outlines are drawn on top,
      so coasts are sharp at any zoom

### Points - built
- [x] GeoGuessr-style points: every answer earns 0 to 5,000. A tap within 1 km of the
      place (or inside it) earns all 5,000, and every further 10 km halves them; a
      name or a relation earns 5,000 when right and 0 when wrong
      (docs/04-learning-engine.md, "Points")
- [x] The running score shows above each question, each answer's points with a bar
      on its feedback, and the session's total out of the most it could have been on
      the summary (daily sessions, extra practice and the placement test; the map
      test stays without feedback)
- [x] Points are a score only: the scheduler still grades each answer by its own
      tolerance, which widens for new places
- Not yet: points are not stored, so there is no best score or history of scores

### Pace - built
- [x] As many sessions a day as the player likes: once today's is done, home says so
      and offers another session
- [x] A pace setting for new places: 20 a day (normal, the default), 40 (fast) or no
      limit; at the limit, home says so and points to the setting
- [x] At every pace, a new place comes in only while fewer than 4 places are still in
      their learning steps and recent accuracy is high; at most a third of a session
      is new places (5 of 15, 8 of 25)
- [x] Places learned in an earlier session today come back once more, 2 or more hours
      later (minutes, hours, then days)

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
   playtest (see `docs/images/phase1c-flow.png` and `docs/images/phase1d-flow.png`
   for the newest screens). Ask testers to take the map test on day one.
2. Review the 1b content: region borders, Hebrew names, hints and facts.
3. Tune tolerances, session size and the placement test from the playtest.
4. Check the reminder on real phones: Android 13+ (the permission question, and the
   way to the phone's notification settings when Android refuses without asking),
   after a reboot, after an app update, and on phones that stop apps to save battery.
   Settings has "send a test reminder" for this.
5. Agree the D7 retention benchmark before the closed beta, and collect results
   through "Share results" (Phase 1 exit test).
