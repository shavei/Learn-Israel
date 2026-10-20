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
d       = distance from tap to the place (km); a tap inside a city polygon is d = 0
tol     = base tolerance x learning multiplier
          base: city, the Jordan, mountain and crater 4 km; site, reserve, port,
          airport and stream 3 km (small targets, often next to a city);
          a long ridge its own (the Carmel 8 km, the Hermon and the Gilboa 6 km);
          areas max(2 km, 10% of their size)
          multiplier by the LOCATE card's stability (how well the place is known):
            new or under 1 day  2.5   (a new city: 10 km)
            under 3 days        2.0   (8 km)
            under 10 days       1.5   (6 km)
            under 21 days       1.25  (5 km)
            21 days or more     1.0   (4 km)
ratio   = d / tol

ratio <= 1    -> Good   (Easy if fast, within half the tolerance, and not first review)
ratio <= 3    -> Hard   (right area, not precise)
ratio >  3    -> Again
```

Early attempts are forgiving so the first sessions stay near the 85 percent success
target; precision is asked for once the memory is stable.

### LOCATE on an area item (region, lake)
Tap inside the polygon = Good (Easy with the speed/confidence rule). Outside: `d` is
the distance to the polygon edge, `tol` scales with the square root of the area.

### LOCATE on a line item (river, stream, crater; later roads) in Pin It
"Tap anywhere on Route 90". Distance to the nearest segment, tolerance by line type.
A crater is drawn in OpenStreetMap as a line along its floor, so it is graded as one.

### LOCATE in Map Builder (regions and districts)

```
d       = how far the shape was dropped from its place (km), the length of the move
          between where it lies and where the player left it
tol     = max(4 km, 15% of the shape's size, the square root of its area)
          x learning multiplier
```

15 percent of its size leaves most of a shape on its place; 4 km keeps small regions
(Menashe, the Western Galilee) fair to drop with a finger. Same bands as a tap; Easy
needs half the tolerance in under 8 seconds.

### When the engine asks it

Map Builder practices the place's `LOCATE` card, like Pin It: in the daily session, a
due review of a region or a district is asked that way half of the time. Places in
their learning steps, and places with hints on, always get Pin It. From home, a Map
Builder round asks every region and district the player has met, weakest first, each
once; a miss comes back once. A round brings no new places, and its answers count like
extra practice.

### Focused practice

A round from home chosen by the answer log alone. Each card's tally (right, close and
wrong, and the miss rate over its last 10 answers, a close answer counting half) gives
it a need:

```
need    = recent miss rate + 1 / (2 + answers so far)

          never answered: 0.5; right once: 0.33; right 20 times: 0.05
          missed 4 in 10: 0.48; missed every time, 10 answers: 1.08
```

Every card the player has met can be asked, still in its learning steps or not. Each
place is asked once, by its card with the most need; the 10 places with the most need
come first, ties to the weaker card. A miss comes back once, after the 10. Pin It is
always a tap (no Map Builder), names and relations as in the session. Answers are
logged with their question type and update the tally, but the scheduler's card stays as
it was: the round never moves a review earlier or later.

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

### Points
Separately from the grade, every answer earns points, as in GeoGuessr, so the player
sees how close they came on a scale they know:

```
d       = distance from tap to the place (km), 0 inside an area
points  = 5000                          if d <= 1
          5000 x 0.5 ^ ((d - 1) / 10)   otherwise (every 10 km further halves them)

          d:      1     3     5    10    20    50   100
          points: 5000  4353  3789 2679  1340  167   5

Map Builder: d is how far the shape landed from its place
NAME, RELATE: 5000 when right (a spelling slip included), 0 when wrong
```

- **Points never change the schedule.** The grade above decides what FSRS learns; points
  are feedback and a score. Keeping them apart lets the grade stay forgiving for new
  places (a wide tolerance) while points stay strict and the same for every place and
  every stage, so a player can compare today's score with last week's.
- **One scale for every place.** A fixed 10 km half distance, not one per place type:
  a tap inside a region is already worth 5000, and being 10 km off means the same for
  a city or a lake. 10 km is about the gap between neighboring towns, so a tap in the
  wrong town loses about half, and one in the wrong part of the country (50 km and
  more) earns almost nothing.
- **Hints cost no points.** Hints appear on their own after two misses, so taking points
  for them would punish struggling, not choices; the grade already caps hinted
  answers at Hard.
- A session shows its running score above each question, each answer's points with a
  bar on its feedback, and its total out of the most it could have been (5000 per
  answer) on the summary. The map test shows no points, as it shows no feedback.

### Free play (Blitz, Road Trip) and Daily Challenge
Reviews from these modes are logged but applied with reduced weight: a success is
applied only if the card is due within 2 days (avoids inflating stability through
cramming); a failure always counts. Only a place's first answer in a round is applied,
so a blitz that comes back to a place does not review it twice. Free play asks only
places past their learning steps, with the tolerance of each place's own stage (as in
the daily session) and no hints. Under a blitz's clock every answer is fast, so speed
never upgrades a blitz answer to Easy; a road trip is not timed and grades as Pin It.

## Within-session (short-term) learning

FSRS schedules in days. Inside a session we also need minutes-level steps:

- **New card:** Discover, then the first retrieval within 1 to 2 questions, then again
  about 5 to 8 questions later. Graduates to FSRS after one success at the spaced step.
- **Lapsed card (Again):** feedback, then re-asked 3 to 6 questions later until
  answered correctly once. Only the *first* answer of the day updates FSRS.

## Pace: as many sessions as the player likes

The daily session is the minimum; after it, home offers another session for as long
as the player wants to go on. How fast new places come in is set by the player's own
learning, not by the calendar:

- **Few places in learning at once.** A new place comes in only while fewer than 4
  places are still in their short-term learning steps. Each new place must stick
  (answered right at its 10-minute step) before more are added: small batches,
  consolidated before the next, as working memory and interference research suggest
  (doc 01, rule 6). A struggling player gets new places slowly; a fluent one gets them
  as fast as they can learn them.
- **Accuracy near 85 percent.** Over the last 8 answers: below 60 percent no new
  places, below 75 percent one, below 85 percent half the session's share.
- **A share of each session.** At most a third of a session's questions bring new
  places (each needs about two questions to learn), so reviews always have room.
- **A daily limit the player chooses.** 20 a day (normal, the default), 40 (fast), or
  none. Each new place adds a few reviews over the next weeks,
  so the limit protects tomorrow's workload; a player who wants to go faster can lift
  it, and the gates above still apply.
- **Expanding retrieval within the day.** A place first learned in an earlier session
  today comes back once more in a later one, at least 2 hours after its last answer
  and before its first review in days: minutes, hours, then days. FSRS-6 counts these
  same-day answers with its short-term stability model, so they strengthen the memory
  a little without distorting the schedule.
- **Reviews keep their days.** Places in review are asked when due, however many
  sessions are played. When nothing is due and no new place may come in, "extra
  practice" reviews the places most in need early; FSRS gives an early review a smaller
  gain, as the spacing effect predicts.
- **Learning steps never fill a session.** A place still in its learning steps is not
  asked early while other places can be: when nothing else is left, other places in
  review are asked early in between (the most in need first, each once), and the
  learning steps come back when due or in the session's overtime. No card is asked
  more than 4 times in a session; one that keeps being missed waits for the next.
  Before this, a player who kept landing close (HARD, which keeps a place at its
  learning step) got the same three or four places for a whole session.
- **The places most in need.** Where the session is free to choose (extra practice,
  and the places asked between learning steps), it goes round every place before any
  comes back, then picks the ones this player has missed most in their last 10
  answers (a close answer counts half), then the ones asked least so far, then the
  weakest by the scheduler. Due reviews keep the scheduler's order. The progress
  screen lists the five places missed most, with their right, close and wrong answers.

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
  inside or nearest to B), the answer logs `tappedNear = B`. A pair mixed up twice or
  more, either way round, becomes a confusable for that player (personal only for now;
  a global threshold waits for the log upload in Phase 2e).
- Learned pairs join the seeded ones everywhere: asked back to back in review once
  both are stable for 3 days, offered first as each other's wrong names in Name It,
  and as partners in Which Way. As a wrong name, only a partner of the same kind (a
  stream mixed up with a town stays out of Name It). The progress screen lists them.
- The confusion rate in the learning stats still counts only the seeded pairs, since
  learned pairs are made of confusions and would inflate it.

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
