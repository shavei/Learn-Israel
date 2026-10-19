# 05. Technical Architecture (Android)

## Principles

- **Offline first.** The whole learning experience works without a network. Maps and
  content ship in the APK (or a small asset pack).
- **Fast and smooth.** 60 fps pan and zoom on mid-range phones; cold start under 2 s.
- **Small.** Target APK under 25 MB.
- **Testable core.** Learning logic and geometry are pure Kotlin with no Android
  dependency.

## Stack

| Concern | Choice | Why |
|---------|--------|-----|
| Language | Kotlin | Standard for Android |
| UI | Jetpack Compose + Material 3 | Modern, fast iteration, good animation APIs |
| Map rendering | Custom Compose `Canvas` renderer over pre-projected vector data | We need a *blank*, game-styled map with precise hit testing, custom highlighting and fade animations, offline, with no tiles or API keys. A full map SDK is heavy and fights the "blank map" design |
| Fallback option | MapLibre Native with a self-hosted vector style | Only if we later need street-level detail |
| Persistence | Room (SQLite) | Review logs, card states, settings |
| Settings | DataStore | Small key/value prefs |
| DI | Hilt | Standard, works with ViewModels |
| Async | Coroutines + Flow | Standard |
| Navigation | Navigation Compose | Standard |
| Background | WorkManager | Daily reminder scheduling |
| Analytics (opt in) | Self-hosted or privacy-friendly (e.g. PostHog / Firebase with minimal events) | Decide later; local logs are the source of truth |
| Backend (phase 3) | Decided later (Supabase or Firebase) | Accounts, sync, leagues, daily challenge seed. Phases 0 to 2 are fully offline |
| Build | Gradle Kotlin DSL, version catalog | Standard |
| Min SDK | 26 (Android 8.0) | Covers nearly all active devices |

## Module layout

```
:app                      Application, navigation, DI wiring
:core:geo                 Pure Kotlin: projection, geometry, hit testing, distances
:core:learning            Pure Kotlin: FSRS, grading, session composer, curriculum ordering
:core:content             Content models, asset loading (places, facts, geometry)
:core:data                Room database, repositories, DataStore
:core:ui                  Theme, design system, shared Compose components
:core:map                 Compose map renderer (layers, camera, gestures, animations)
:feature:onboarding       Language, placement test, tutorial
:feature:session          Daily session player (hosts all question types)
:feature:modes            Free play: Pin It, Name It, Builder, Blitz, Trace, Road Trip
:feature:progress         Mastery map, stats, achievements
:feature:settings         Preferences, layers (e.g. Green Line), notifications
tools/                    Python data pipeline (not shipped), see doc 06
```

## Map renderer design

1. **Projection.** All geometry is pre-projected offline into a planar coordinate
   system suited to Israel (Israel Transverse Mercator, EPSG:2039, or a local
   Transverse Mercator). This gives near-true shapes and lets us compute distances in
   plain meters at runtime. Store as integer meters to keep data small.
2. **Layers.** Background (neighbors, sea), land outline, regions, water bodies,
   rivers, roads, places (points). Each layer is a list of paths with styles.
3. **Level of detail.** Two or three simplification levels per geometry (made with
   mapshaper in the pipeline). Pick the level by zoom.
4. **Camera.** A `MapCamera` state (center, scale, optional rotation) driven by
   `Modifier.transformable` / gesture detectors with fling and bounds clamping.
   Animated transitions (zoom to region, pulse on answer) via `Animatable`.
5. **Hit testing.** Convert screen tap to map meters with the inverse camera
   transform, then:
   - points: distance to the point,
   - polygons: point-in-polygon (ray casting) plus distance to edge,
   - lines: distance to nearest segment.
   A simple grid spatial index keeps it fast for hundreds of items.
6. **Rendering cost.** Cache `Path` objects per layer and level of detail, redraw only
   the transform on pan/zoom. Labels drawn with `TextMeasurer`, culled by zoom and
   collision.
7. **RTL and labels.** Hebrew labels render with correct bidi; label
   placement uses precomputed anchor points from the pipeline.

## Data flow

```
assets/
  geometry.bin   (projected, simplified, all layers)
  places.json    (ids, types, tiers, units, anchors, confusables, importance)
  i18n/he.json (names, alt names, facts, hints)
        |
        v
:core:content (load once, keep in memory, a few MB at most)
        |
        v
:core:learning  <---->  :core:data (Room: cards, reviews, unlocks)
        |
        v
:feature:session ViewModel -> UI state -> Compose screens (+ :core:map)
```

Content updates ship with app updates in v1. Later, a versioned content pack can be
downloaded, with `id` stability guaranteeing review history survives.

## Database (Room) sketch

```
CardEntity(cardId PK, placeId, facet, state, stability, difficulty,
           due, lastReview, reps, lapses, unlockedAt)
ReviewEntity(reviewId PK, cardId, ts, grade, errorKm, tapX, tapY, chosenOptionId,
             responseMs, confidence, hintLevel, mode, sessionId, appVersion)
UnitProgressEntity(unitId PK, unlockedAt, completedAt)
ConfusionEntity(askedPlaceId, tappedPlaceId, count, PK(asked, tapped))
AchievementEntity(id PK, unlockedAt)
```

## Testing strategy

- `:core:learning`: unit tests with FSRS reference vectors, property tests for the
  composer (never two siblings, respects caps, new-item budget logic), simulation
  tests that replay synthetic learners and check retention targets.
- `:core:geo`: unit tests for projection round trips, distances against known values,
  point-in-polygon edge cases.
- Compose UI: screenshot tests (Paparazzi or Roborazzi) for key screens in Hebrew (RTL)
  and light/dark.
- Instrumented smoke test of a full session on an emulator in CI.
- Performance: Macrobenchmark for startup and map frame timing.

## CI/CD

- GitHub Actions: lint (ktlint/detekt), unit tests, screenshot tests, build debug and
  release APK/AAB.
- Data pipeline check: regenerate assets from pinned sources and fail on unexpected
  diffs.
- Release via Google Play internal testing, then closed beta, then production.

## Privacy and security

- No account needed for core use. No location permission needed (we never use the
  player's position).
- Local data only by default. Opt-in anonymous review upload, with clear explanation.
- Google Play Data Safety form kept accurate. Not targeting children under 13 at
  launch (decision), so the Families policy does not apply yet; revisit with a
  classroom mode.
