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

Goal: more to learn after the cities, and more ways to practice it. Built as five
smaller releases, in order of learning value, each playable on its own.

### 2a. Tiers 4 to 6 - built
- [x] 49 new places after the core, in 10 themed units: 22 of nature (mountains,
      craters, streams, springs and reserves), 5 of infrastructure (two airports, three
      ports) and 22 heritage sites (docs/03-content-curriculum.md); the six highways
      followed later (below)
- [x] Geometry from OpenStreetMap: rivers and craters by their way or relation, points
      copied from the OSM object named beside each place
- [x] Each place type belongs to a theme (core, nature, infrastructure, heritage);
      Name It offers names of the same kind first (streams with streams, sites
      with reserves), then of the same theme
- [x] Tolerances: 3 km for sites, reserves, ports, airports and streams (small
      targets, often next to a city), 4 km for mountains and craters, and a tolerance
      of its own for a long ridge (the Carmel 8 km)
- [x] A missed tap names the other stream or site the player probably meant
- [x] Progress screen and "Share results": places practiced and known in each theme,
      so the exit test can see who continues past Tier 3
- [x] The placement test stays on the core
- [x] Region percentages count every place in the region: its cities, mountains,
      streams (by the middle of their course), reserves and sites
- Decided (doc 10): tiers 4 to 6 come after the whole core; places inside a known city
  stay; region percentages include nature and heritage; 49 places are enough until the
  playtest shows players reach them; Haifa airport and Tel Dan stay out; the new Hebrew text ships without a separate review and is fixed as testers
  report problems
- Left out at first: the highways, since built (below)

### 2b. Map Builder and Trace It - built
- [x] Map Builder: drag a region or district shape into place on the outline, one shape
      per question, from the same start in the sea every time; scored by how far it
      lands (right within 15 percent of its size, at least 4 km)
- [x] Trace It: draw the course of the Jordan or one of the 8 streams with a finger,
      scored by the mean distance between the drawing and the line, both ways (right
      within the line's Pin It tolerance: 4 km for the Jordan, 3 km for a stream)
- [x] One finger drags or draws, two pan and zoom; "here" and "done" hand the answer in
- [x] Both practice the place's LOCATE card: in the daily session a due region, district
      or line review is asked this way half of the time (Pin It while learning or with
      hints), with points and feedback like any answer
- [x] Home offers a round of each on the places of its kind already met, weakest first,
      each once, a miss once more (docs/04-learning-engine.md)
- [x] Answers are logged as BUILD and TRACE: where the shape's center was dropped, and
      the drawn path
- Decided (doc 10): in the daily session and as rounds from home; one shape per
  question; the Jordan and all 8 streams, craters Pin It only
- Not yet: roads, the coastline, and seas or neighbors in Map Builder
- Later removed: Trace It, replaced on home by focused practice (below); rivers and
  streams are Pin It only, and older logs keep their TRACE answers

### 2c. Free play: Blitz and Road Trip - built
- [x] Blitz: 60 seconds of Pin It on learned places (past their learning steps), each
      once in random order before any comes back; no feedback card, the next place comes
      at once with the last answer shown faintly on the map and its points above the
      question; the clock starts on "start" and a tap after it runs out does not count
- [x] Road Trip: a chain of 5 stops through learned towns, lakes, mountains, reserves,
      sites, ports and airports, planned on the spot: each leg 12 to 70 km, never turning
      back by more than 75 degrees. The start is given; each further stop is a Pin It
      question with full feedback, and the route draws from stop to stop as they are found
- [x] Reduced weight for both (docs/04-learning-engine.md, "Free play"): a miss always
      counts, a success only on a card due within 2 days, and only a place's first answer
      in a round; a blitz never rates an answer EASY for speed
- [x] Home offers both once places are learned: Blitz from 4 learned places, Road Trip
      once a trip of at least 3 stops can be planned
- [x] Answers are logged as BLITZ and ROAD_TRIP; they count as practice for the streak
      and the stats, but not for the history of Pin It errors
- Decided (doc 10): free play only on learned places; the same reduced weight for Road
  Trip as for Blitz; trips are planned from the player's own places, not written by hand
- Not yet: written trips with a story ("You land at Ben Gurion..."), trips along the
  highways, and a best Blitz score (points are not stored yet)

### 2d. Motivation - built
- [x] Ranks by known places (LOCATE stable for 21 days): Tourist, Hiker (10), Guide
      (30), Navigator (60), Cartographer (100) and Sabra (150); always the current one,
      shown on home and on the progress screen with what the next one needs
- [x] 15 achievements, each a piece of real knowledge (docs/02-game-design.md): the
      frame, all regions, all districts, the main cities within 3 km, around the
      Kinneret, a whole region, all cities, nature, ports and airports, heritage, a
      no-miss session, a place remembered after 30+ days, 7 and 30 days in a row.
      Stored when first earned and kept (database version 4); the progress screen
      lists them with their date or how far along they are, and the session summary
      names new ones and a new rank
- [x] Learned confusable pairs: two places mixed up twice or more (by the place a
      missed tap was nearest to, either way round) join the seeded pairs: asked back to
      back, offered as each other's wrong names, and listed on the progress screen
- [x] "Share results" adds the rank, the achievements earned and the learned pairs
- Decided (doc 10): the rank can drop and achievements stay; personal pairs only,
  from 2 confusions; achievements tied to places use the current content, so new
  places in a theme reopen nothing already earned
- Not yet: global confusable pairs (with the log upload, 2e), and a best Blitz score

### Focused practice - built
- [x] A round of 10 from home, in place of Trace It, run by the answer log alone: every
      place met can come up, the most in need first (recent miss rate, plus a bonus for
      few answers, docs/04-learning-engine.md)
- [x] Each place asked once, by the skill its log shows the most need for; a miss
      comes back once at the end
- [x] Answers are logged and update the tally, but leave the scheduler's cards as they were
- Decided (doc 10): Trace It removed everywhere; places in their learning steps count;
  missed most and answered least weigh together in one score

### Highways - built
- [x] Routes 1, 2 (the coastal highway), 4, 6 (Trans-Israel), 40 and 90, in their own
      unit at the start of tier 5: every motorway to secondary road in OpenStreetMap
      that carries the number, within 1 km of Israel's land or Gaza (the numbers repeat
      across the borders), from the Overpass API
- [x] Pin It anywhere along the road, 3 km like a stream; Name It offers roads with
      roads; a missed tap names the other road the player probably meant
- [x] A highway counts toward no region percentage (each crosses many), is not a Road
      Trip stop, and leaves the gateways achievement to ports and airports
- Not yet: trips along the highways in Road Trip, and roads in Map Builder

### 2e. Data - postponed until after launch
- [ ] Opt-in anonymous log upload; first global FSRS parameter fit
- Decided (doc 10): postponed. A global fit needs many players, so it waits for the
  public launch; until then beta results come through "Share results", and the game
  stays fully offline

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
