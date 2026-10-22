# על המפה (Learn-Israel)

![על המפה: בונים מפה של ישראל בראש, 5 דקות ביום](marketing/brand/logo.png)

**Al HaMapa** ("on the map") is an Android game, in Hebrew, that teaches the geography of
Israel, built on the science of how people actually remember.

> **Status:** Phase 2 in progress. A daily session on a blank map with 209 places
> (the frame, 19 natural regions, 7 districts and 118 cities and towns, then 56
> mountains, craters, streams, reserves, highways, ports and heritage sites): find them,
> name them (choice, then typed), and relate them ("which is further north?"),
> scheduled by spaced repetition (FSRS). Onboarding with a placement test, a progress
> screen with region percentages, a daily streak (a day counts once its session is finished) and reminder, settings, and dark
> mode. A 20-place map test before the first session and a month later, and local
> learning stats you can share. Two map styles: standard and shaded relief.
> GeoGuessr-style points: up to 5,000 for each answer, more the closer you tap.
> As many sessions a day as you like, at the pace of new places you choose.
> Map Builder (drag a region into place), mixed into the session and as a round of its own.
> Focused practice: a round of the places your own answers say need it most. Free play on learned places:
> Blitz (a minute against the clock, with your best score to beat) and Road Trip (a chain of stops across the map).
> Ranks by places known, 15 achievements for real knowledge, and confusable pairs
> learned from your own missed taps, practiced side by side.
> See the [roadmap](docs/09-roadmap.md).

## Install on your phone

**[Download the latest Al HaMapa APK](https://github.com/shavei/Learn-Israel/releases/latest/download/mapa.apk)**

This link always points to the newest build of `main`. The Release APK workflow
publishes it after every merge, once its tests pass and the APK checks out, and checks
every 10 minutes that nothing was missed; it never replaces a newer build with an older one.
Open it on an Android phone (8.0 or newer) and allow installing from your browser when
asked. Installing a newer build updates the app and keeps your progress.

After that, update from inside the app: Settings, "Check for updates". That button is
the app's only use of the internet; nothing is checked or sent unless you tap it.

![Phase 1c: welcome, placement test, home with the reminder offer, progress, settings in dark mode, and the dark map with the Green Line](docs/images/phase1c-flow.png)

![Points: the running score above a question, 5,000 points for a tap inside the sea, 23 points for a miss 79 km away, and the session total](docs/images/points.png)

![Map Builder and focused practice: the Sharon dragged from the sea, its feedback with the true place, the two rounds on home, and focused practice opening on the place missed most](docs/images/drills.png)

![Motivation: the rank on home, the achievements on the progress screen, the pairs learned from missed taps, and a new achievement on a session summary](docs/images/motivation.png)

![Free play: a blitz question with the last answer under it, the blitz summary, a road trip stop with the route so far, and the whole trip on its summary](docs/images/free-play.png)

![Map styles: the choice in settings, the relief map, zoomed in on the Dead Sea, and in dark mode](docs/images/map-styles.png)

![Phase 1d: the map test (no feedback between questions), its summary, the post-test offer a month later, and the progress screen with map test results and learning stats](docs/images/phase1d-flow.png)

## The idea in one paragraph

Your own map of Israel starts gray. Each day a 5 to 10 minute session asks you to
find and name places on a blank map. Every answer gets instant spatial feedback (a
line from your tap to the true spot, the distance in km, and one memorable fact). A
spaced repetition scheduler (FSRS) brings each place back right before you would
forget it. Places you master light up on your map; places you neglect slowly fade.
You start with the big three cities and the sea beside them, then the rest of the big
frame (seas, lakes, neighbors), then
regions, then the cities and towns inside them, then nature, roads, and heritage
sites.

## Why it will work

The design is built on the most robust findings in learning science:

| Principle | How the game uses it |
|-----------|----------------------|
| Retrieval practice | Every turn is a recall attempt on a blank map, not reading |
| Spacing | FSRS schedules each place at the moment it is about to be forgotten |
| Feedback and learning from errors | Line, distance, fact after every answer; misses come back in the same session |
| Hierarchical spatial memory | Frame first, anchors next, detail last; every place taught relative to an anchor |
| Interleaving | Confusable places (Ramat Gan / Givatayim, Kiryat Gat / Kiryat Malakhi) are mixed on purpose |
| Optimal difficulty (~85% success) | Session composer adjusts new material to keep success in the sweet spot |
| Dual coding | Name + location + one fact for every place |
| Self-determination theory | The trophy is your own mastery map, honest and earned |

## The plan

| Doc | Contents |
|-----|----------|
| [01. Learning science](docs/01-learning-science.md) | The research, and the 10 design rules derived from it |
| [02. Game design](docs/02-game-design.md) | Vision, players, core loop, modes, progression, motivation |
| [03. Content and curriculum](docs/03-content-curriculum.md) | Tiers, units, item model, ordering, border policy |
| [04. Learning engine](docs/04-learning-engine.md) | FSRS, grading taps by distance, session composer, confusables |
| [05. Technical architecture](docs/05-technical-architecture.md) | Kotlin + Compose stack, modules, custom map renderer, testing |
| [06. Map data](docs/06-map-data.md) | Data sources, licenses, pipeline, validation |
| [07. UX, accessibility, localization](docs/07-ux-accessibility-localization.md) | Onboarding, screens, a11y, Hebrew-only RTL |
| [08. Measurement](docs/08-measurement.md) | How we prove people learn; experiments; privacy |
| [09. Roadmap](docs/09-roadmap.md) | Phases with exit tests and the first sprint |
| [10. Decisions and risks](docs/10-risks-and-open-questions.md) | Decisions made, and how we handle risks |
| [References](docs/references.md) | Papers, algorithms, data sources |

## Project layout

| Path | What it is |
|------|------------|
| `app/` | Android app (Kotlin, Jetpack Compose, Room): map renderer, onboarding, home, session, progress and settings screens, daily reminder |
| `core/geo/` | Pure Kotlin geometry on the Israeli TM grid: distances, point in polygon, shapes |
| `core/content/` | Place and map models, parser for the generated assets |
| `core/learning/` | FSRS scheduler (port of py-fsrs), tap grading, session engine, placement test, pre/post map test, learning stats, mastery and region scores, streak |
| `content/places.yaml` | The curriculum: which places are taught, with Hebrew names, hints and facts |
| `content/regions.yaml` | Which official natural regions make up each of the game's 19 regions |
| `content/natural_regions.geojson` | Official natural regions (CBS; Ministry of Agriculture for Judea and Samaria): the land's outline, the regions and the districts |
| `content/localities.csv` | CBS locality file: official points of the cities and towns |
| `content/jordan_river.geojson` | The Jordan River from OpenStreetMap |
| `content/elevation_detail.bin`, `content/elevation.json` | Ground height on a 100 m grid around Israel (Copernicus DEM GLO-90); the pipeline cuts it to Israel's land for the relief style |
| `tools/fetch_sources.py` | Refreshes the source files in `content/` |
| `content/surroundings.geojson`, `content/palestine.geojson` | Coastlines, neighbors and the Dead Sea (OpenStreetMap); Gaza and the Green Line (UN OCHA) |
| `marketing/brand/` | `make_icon.py` draws the app icon from the game map into `icon.svg` and the Android drawables; `render.js` renders the 512 px store icon and the logo |
| `tools/readme_images.sh` | Rebuilds the README pictures from the screen tests' screenshots |
| `tools/build_content.py` | Data pipeline: the files above + `places.yaml` into `app/src/main/assets/` |

## Build and test

Requirements: JDK 17+, Android SDK with platform 35 (set `sdk.dir` in `local.properties`
or `ANDROID_HOME`).

```bash
./gradlew test                 # all unit tests, plus Robolectric screen tests
./gradlew :app:assembleDebug   # APK at app/build/outputs/apk/debug/app-debug.apk
```

The screen tests save screenshots of a real session to `app/build/screenshots/`;
`tools/readme_images.sh` then rebuilds the pictures in this README from them.

To change what is taught, edit `content/places.yaml` and regenerate the assets:

```bash
pip install -r tools/requirements.txt
python3 tools/build_content.py
```

CI checks that the committed assets match the pipeline output.

## Data credits

Israel's outline, natural regions, districts and city and town locations from the Israel
Central Bureau of Statistics (natural regions 2018, locality file 2023); natural regions of
Judea and Samaria from the Ministry of Agriculture. Gaza and the Green Line from UN OCHA
(CC BY-IGO). Coastlines, neighboring countries, the Dead Sea, the Jordan River and the
location of Jericho © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors
(ODbL 1.0). Locations of the other Palestinian cities from
[GeoNames](https://www.geonames.org/) (CC BY 4.0). The FSRS scheduler is
ported from [py-fsrs](https://github.com/open-spaced-repetition/py-fsrs) (MIT).
