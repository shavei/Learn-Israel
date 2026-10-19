# 04. The Learning Engine

The learning engine is the heart of the product. It decides **what** to ask,
**when**, and **how hard**, and it turns map taps into memory signals. It is a pure
Kotlin module with no Android dependencies so it can be unit tested heavily.

## Concepts

- **Place:** a geographic item (see [03-content-curriculum.md](03-content-curriculum.md)).
- **Facet:** a skill on a place. `LOCATE` (find it on the map), `NAME` (name it when
  shown), `RELATE` (position relative to other places).
- **Card:** one `(place, facet)` pair. This is the unit the scheduler tracks. A city
  typically has 3 cards; a sea might only have `LOCATE` and `NAME`.
- **Review:** one answered question for a card: timestamp, grade, raw error
  (km or correctness), response time, confidence, mode.

## Scheduler: FSRS

We use **FSRS** (Free Spaced Repetition Scheduler, Ye et al., 2022 and later
versions), the modern open-source successor to Anki's SM-2. It models each card with:

- **Stability `S`**: days until recall probability falls to 90 percent.
- **Difficulty `D`**: how hard the card is for this learner (1 to 10).
- **Retrievability `R(t)`**: probability of recall after `t` days,
  `R(t) = (1 + F * t / S) ^ (-decay)` (FSRS 4.5 defaults: `decay = 0.5`,
  `F = 19/81`; FSRS 6 makes the decay trainable).

After each review, `S` and `D` are updated from the grade (Again / Hard / Good / Easy),
and the next interval is chosen to hit a **desired retention** (default 0.90, the
"around 85 to 90 percent success" sweet spot). FSRS outperforms SM-2 on large public
benchmarks and has reference implementations under permissive licenses
(py-fsrs, ts-fsrs, rs-fsrs, MIT).

**Decision:** implement FSRS ourselves in Kotlin, ported from an MIT-licensed
reference implementation, with the reference test vectors as unit tests. Existing
Kotlin ports exist but at least one is GPL-3.0, which would force the whole app to be
GPL. Porting is a few hundred lines.

Parameter strategy:
1. Ship with FSRS default parameters.
2. After launch, fit **global** parameters on anonymized aggregate review logs
   (opt in) because map items behave differently from vocabulary flashcards.
3. Once a user has about 1,000 reviews, optionally fit **personal** parameters on
   device (the FSRS optimizer is lightweight).

## From a tap to a grade

Map answers are not binary. We convert error to an FSRS grade.

### LOCATE on a point item (city, site)

```
d       = great-circle distance from tap to the place (km)
tol     = tolerance for this item (km), by type and size:
            big city 3, town 2, small locality 1.5, site 1.5
            (tap inside a city polygon counts as d = 0)
ratio   = d / tol

ratio <= 1    -> Good   (Easy if fast AND confidence "sure" AND not first review)
ratio <= 3    -> Hard   (right area, not precise)
ratio >  3    -> Again
```

### LOCATE on an area item (region, lake, crater)
Tap inside the polygon = Good (Easy with the speed/confidence rule). Outside: `d` is
the distance to the polygon edge, `tol` scales with the square root of the area.

### LOCATE on a line item (river, road) in Pin It
"Tap anywhere on Route 90". Distance to the nearest segment, tolerance by line type.
In Trace It: mean distance between the drawn path and the true line, graded by bands.

### NAME
Multiple choice: correct = Good at most (recognition is weaker evidence; Easy is
reserved for typed recall). Typed: exact or accepted alternate spelling = Good / Easy,
correct after one autocomplete hint = Hard, wrong = Again.

### RELATE
Questions are generated, not stored. Each relational question updates the `RELATE`
card of the place it targets (the less-known of the two). Distance-estimate questions
grade by log error ratio.

### Response time
Used only to upgrade Good to Easy (fluent recall). Never used to downgrade a correct
answer, because tapping speed varies by device and person.

### Blitz and Daily Challenge
Reviews from these modes are logged but applied with reduced weight: a success is
applied only if the card is due within 2 days (avoids inflating stability through
cramming); a failure always counts.

## Within-session (short-term) learning

FSRS schedules in days. Inside a session we also need minutes-level steps:

- **New card:** Discover, then the first retrieval within 1 to 2 questions, then again
  about 5 to 8 questions later. Graduates to FSRS after one success at the spaced step.
- **Lapsed card (Again):** feedback, then re-asked 3 to 6 questions later until
  answered correctly once. Only the *first* answer of the day updates FSRS.

## Sibling management

Facets of the same place are siblings. Show at most one sibling per session (except
NAME immediately after Discover in the tutorial), to avoid one answer cueing the other.

## Facet unlocking (difficulty ladder)

```
Discover
  -> LOCATE with a zoomed, regional view and 1 to 2 reference labels
  -> NAME multiple choice
  -> LOCATE on the full map, no labels
  -> NAME typed
  -> RELATE
```

A facet unlocks when the previous one reaches stability >= 3 days. The ladder keeps
early experiences successful and grows difficulty as memory strengthens.

## Hint ladder (scaffolding that fades)

When a card lapses repeatedly (3+ lapses), the engine adds support for its next
attempt:

1. Zoom to the right region before asking.
2. Show the anchor hint text ("between Tel Aviv and Haifa").
3. Show a mnemonic, if one exists.

Hinted answers cap the grade at Hard. Hints are removed as soon as the card succeeds.

## Session composer

Composes the daily session (default 20 questions). Pseudocode:

```kotlin
fun composeSession(now: Instant, size: Int = 20): List<Question> {
    val due = cards.filter { it.isDue(now) }
        .sortedBy { it.retrievability(now) }          // most at-risk first
    val recentAccuracy = reviewLog.successRate(lastN = 50)

    // Adapt new-item intake to keep success near ~85 percent
    val newBudget = when {
        due.size > size        -> 0
        recentAccuracy < 0.75  -> 1
        recentAccuracy < 0.85  -> 3
        else                   -> 5
    }.coerceAtMost(dailyNewRemaining())

    val reviews   = due.take(size - newBudget - 2)
    val newCards  = curriculum.nextNew(newBudget)        // ordering rules in doc 03
    val confusers = confusablePairsDue(limit = 1)       // interleaving
    val relate    = relationalQuestion(limit = 1)       // fixes distortions

    return arrange(reviews + newCards + confusers + relate) {
        startWithLikelySuccess()
        noTwoSiblings()
        spreadNewCardsWithShortTermSteps()
        interleaveRegions()            // avoid 5 Galilee items in a row
        endWithLikelySuccess()
    }
}
```

If nothing is due and the daily new cap is reached, the session offers
**"strengthen weakest"** (lowest stability cards) or free play, and says honestly that
today's essential work is done.

## Confusable pairs

- Seeded by hand in content data (`confusables`).
- Learned automatically: when a player taps place B while asked for A (tap lands
  inside or nearest to B), record a confusion `A -> B`. Pairs crossing a threshold
  (personal or global) become confusables.
- Once both are individually stable, the composer sometimes asks them back to back
  or as a direct contrast ("Tap Kiryat Gat, then Kiryat Malakhi").

## Placement test

Optional at onboarding, about 3 minutes, adaptive:
1. Ask Tier 0 and 2 anchors. If most are right, sample Tier 3 by region.
2. Items answered correctly get an initial FSRS state as if reviewed once with Good,
   so they come back soon for confirmation; wrong ones stay new.
3. Units whose sampled items are mostly correct are unlocked.

## Mastery metrics shown to the player

- **Place mastery:** retrievability today, shown as color intensity.
- **Region score:** mean retrievability of its places, as a percentage.
- **"Known places":** count of cards with `S >= 21 days`. This is the headline number
  and drives ranks.

## Data stored per review (for learning and research)

```
reviewId, cardId, placeId, facet, mode, timestamp, elapsedDaysSinceLast,
grade, errorKm (nullable), tapLat/tapLon (nullable), chosenOptionId (nullable),
responseMs, confidence (nullable), hintLevel, sessionId, appVersion
```

Stored locally in Room. Opt-in anonymous upload for parameter fitting and research
(see [08-measurement.md](08-measurement.md)).
