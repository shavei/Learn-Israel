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
          airport, stream and road 3 km (small targets, often next to a city);
          a long ridge its own (the Carmel 8 km, the Hermon and the Gilboa 6 km)
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

### LOCATE on an area item (sea, lake, country, region, district)
Right only inside: a tap inside the polygon is Good (Easy when fast and not the first
review). Outside it is never right, however big the area: `d` is the distance to the
polygon edge, and within a close margin it is Hard, beyond it Again.

```
margin  = 10% of the area's size (square root of its area), between 2 km and 8 km,
          x the learning multiplier above
          the Kinneret 2 km (5 km while new), the Negev or the sea 8 km (20 km while new)
```

A tap on land is never the Mediterranean, in a session, the placement test or the map
test: with a tolerance that grew with the area's size (10 percent, times 2.5 while new),
a tap 61 km inland used to count as finding the sea.

### LOCATE on a line item (river, stream, road, crater) in Pin It
"Tap anywhere on Route 90". Distance to the nearest segment, tolerance by line type.
A crater is drawn in OpenStreetMap as a line along its floor, so it is graded as one.

### LOCATE in Map Builder (regions and districts)

```
d       = how far the shape was dropped from its place (km), the length of the move
          between where it lies and where the player left it
tol     = 15% of the shape's size (the square root of its area), between 4 km and 8 km,
          x learning multiplier
d <= tol      -> Good (Easy when within half of it in under 8 seconds)
d <= 2 x tol  -> Hard
otherwise     -> Again
```

15 percent of its size leaves most of a shape on its place; 4 km keeps small regions
(Menashe, the Western Galilee) fair to drop with a finger, and 8 km keeps a large one
(the Negev) from being right half its width away. Close is within twice the tolerance,
not three times: a Negev dropped 68 km off is a miss, not close.

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
come first, ties to the weaker card. A round is 10 questions, misses included, so the
counter can say "question 3 of 10": a miss comes back once, in place of the last place
not yet asked. A player who has met fewer than 10 places gets a shorter round, and there
a miss comes back once at the end, the total growing with it (never past 10). Pin It is
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
NAME, RELATE: 5000 when right (a spelling slip included, but never a wrong digit:
             כביש 4 for Route 1 is wrong), 0 when wrong
```

Points follow the verdict, so the feedback title and the bar always agree:

```
right (Good, Easy)  at least 2500 (half the bar)
close (Hard)        500 to 2499
miss  (Again)       below 500
```

Within its band an answer keeps the points of its distance, so the curve above holds
where it can; a tap inside an area is right and worth 5000. A miss never outscores a
right answer, and "Excellent!" never comes with 80 points. A hinted answer that lands
right is graded Hard but keeps the points of a right one.

- **Points never change the schedule.** The grade above decides what FSRS learns; points
  are feedback and a score. Keeping them apart lets the grade stay forgiving for new
  places (a wide tolerance) while points stay strict and the same for every place and
  every stage within a verdict, so a player can compare today's score with last week's.
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
- Only Blitz rounds keep their points: each round that runs out its clock is stored
  (`blitz_rounds`) for a best score to beat. Other sessions keep no score history,
  and stored points still never change the schedule.

### Free play (Blitz, Road Trip) and Daily Challenge
Reviews from these modes are logged but applied with reduced weight: a success is
applied only if the card is due within 2 days (avoids inflating stability through
cramming); a failure always counts. Only a place's first answer in a round is applied,
so a blitz that comes back to a place does not review it twice. Free play asks every
place in practice (the places home counts, learning and known together, so Blitz opens
when home shows 4), with the tolerance of each place's own stage (as in the daily
session) and no hints. A place still in its learning steps keeps them: a free play
success on it changes nothing, only a miss counts. Under a blitz's clock every answer is fast, so speed
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

- **Home promises only what the session gives.** The gates on a new place (the daily
  limit and the places in learning) are one rule in the engine that home also uses, so
  home never says "5 new places" for a session that would bring none; and home offers a
  session only when the engine has a question for it, never an empty one.
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
  inside or nearest to B), the answer logs `tappedNear = B`. A missed highway only
  ever names another highway: it runs through cities, which the player did not mean. A pair mixed up twice or
  more, either way round, becomes a confusable for that player (personal only for now;
  a global threshold waits for the log upload in Phase 2e).
- Learned pairs join the seeded ones everywhere: asked back to back in review once
  both are stable for 3 days, offered first as each other's wrong names in Name It,
  and as partners in Which Way. As a wrong name, only a partner of the same kind (a
  stream mixed up with a town stays out of Name It). The progress screen lists them.
- The confusion rate in the learning stats still counts only the seeded pairs, since
  learned pairs are made of confusions and would inflate it.

## Placement test

Optional at onboarding (and again from settings), 2 to 5 minutes, adaptive: a
computerized adaptive test of Pin It questions, built on item response theory. It
estimates, place by place, what a player with no progress already knows, so practice can
start where that knowledge ends instead of at the first unit. The code is
`PlacementTest` in `core/learning/.../Placement.kt`; the constants named below live in
its companion object.

1. **Model.** The chance that the player finds a place is a logistic curve in
   `ability - difficulty`, with a small floor for a lucky tap and a ceiling below 1 for a
   slip. A place's difficulty comes from its importance (`PlacementTest.difficulty`): in
   the core the big cities are easy and small towns hard; in the other themes (nature,
   infrastructure, heritage) the best-known sites are about as hard as the core's major
   cities. The ability in a theme is the player's general ability plus a per-theme
   offset, so an answer about cities also says something about heritage sites, while the
   offsets let a player be strong in one theme and weak in another. Each level (a theme
   and an importance) also has its own small offset, so a level is judged known or
   unknown only once its own answers agree, and a player who knows half of a level is not
   credited with all of it. All of these are tracked as a probability distribution over
   a grid (a wide prior on the general ability, offsets around 0), updated by Bayes' rule
   after every answer. Places of the same theme and importance are the same to the model.
2. **Question choice.** Each question is the place that, averaged over its two possible
   answers (found or missed, weighted by how likely each is), is expected to leave the
   least uncertainty about the places not yet asked, measured as the sum of `p(1 - p)`
   over them. This asks near the edge of the player's knowledge, in whichever theme is
   least known. Among equally useful places, one from a unit not asked yet is drawn at
   random, so the questions spread over the map. The first question is a place the prior
   already gives a good chance of being found (a big city or the sea), and near the
   minimum the test asks the themes it has not asked `MIN_PER_THEME` times yet. When the
   test goes on only to confirm what looks known (see the stop rule), it asks one of the
   likely-known places. The test asks straight away, with no Discover card and no hints.
3. **Stop rule.** At least `MIN_QUESTIONS` (10) questions, even for a beginner who misses
   them all, and at most `maxQuestions` (`MAX_QUESTIONS`, 40). In between the test ends
   once at most `STOP_UNDECIDED` places not yet asked are still undecided: neither likely
   known (`CREDIT_RECALL` or more) nor likely unknown (`UNKNOWN_RECALL` or less). The
   test also needs every theme asked at least `MIN_PER_THEME` times, and no more places
   likely known than `CREDITS_PER_FIND` per place actually found, so a strong player does
   not get the whole map credited on a few answers (an expert answers about 30). A place
   whose chance is pinned down (to within `DECIDED_SD`) counts as decided even between
   the two thresholds, so a level the player half knows does not keep the test going. The
   counter shows "question n of at most `maxQuestions`". The player can leave at any
   point; what was found so far is kept, and nothing is credited.
4. **Seeding.** A place found (with the session's grading: an area only with a tap
   inside it) gets an initial FSRS state as if reviewed once with Good, so it comes back
   in a couple of days for confirmation. A place missed stays new.
5. **Crediting.** When the test ends, every place not asked whose estimated chance of
   being found is at least `CREDIT_RECALL` is credited (at most `CREDITS_PER_FIND` per
   place found): an FSRS state as if reviewed once
   with Easy. Their first reviews are spread `CREDITED_PER_DAY` a day from day
   `FIRST_CREDITED_DAY` on, the least certain first, so a strong player's map fills at
   once and the confirmations do not crowd out new places. A credited place the player
   does not know after all is missed in review and learned again.
6. **Everything else stays new** and is taught in curriculum order, so daily practice
   continues from what the player does not know yet.
7. Neither the places found nor the places credited count as introduced (the daily cap on
   new places is untouched), and the test is not a day of practice for the streak. Taken
   again from settings, it adds cards only for places not started yet: what is already
   in practice does not change.

## Mastery metrics shown to the player

- **Place mastery:** retrievability today, shown as color intensity.
- **Region score:** mean mastery of its places (a known place counts in full, one not
  yet practiced as 0), as a percentage: 100 percent exactly when all its places are known.
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
