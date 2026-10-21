# 02. Game Design

App name: **Mapa** (מפה). The app is in Hebrew only.

## Vision

> In 5 minutes a day, anyone can build an accurate mental map of Israel, from the
> big shapes down to small towns, and keep it for years.

The player's own map of Israel starts gray. Every place they master lights up. Places
they have not reviewed in a while fade, gently pulling them back. Completing the map is
the long-term goal; the daily session is the habit.

## Target players

| Segment | Need | Implication |
|---------|------|-------------|
| Adults who "never really knew where Afula is" (primary) | Casual self-improvement | Quick sessions, satisfying feedback |
| Israeli students, middle and high school (primary) | Geography curriculum, tests | Curriculum-aligned units |
| Hebrew-speaking olim | Orientation in a new country | Practical places: cities, highways, regions |
| Soldiers / tour guides (stretch) | Deep detail | Advanced tiers, roads, wadis, small localities |

## Core loop

```
Open app
  -> Daily Session (5 to 10 min, auto-composed by the learning engine)
       due reviews + a few new places + 1 to 2 relational questions
  -> Session summary: places strengthened, new places learned, map lights up
  -> Optional: free play modes, daily challenge, explore a region
  -> Come back tomorrow (scheduler has prepared reviews)
```

Three nested loops:

1. **Micro (seconds):** question, answer, instant spatial feedback.
2. **Session (minutes):** 15 to 25 questions, ends on a success, summary screen with
   the map glowing where you improved.
3. **Meta (weeks):** unlock regions and tiers, fill the mastery map, leagues,
   achievements.

## Game modes

Modes are ordered by when they unlock. Each maps to a *skill facet* tracked
separately by the learning engine (see [04-learning-engine.md](04-learning-engine.md)).

### 1. Discover (encoding step, not a mode on its own)
Shown the first time a place appears. The map zooms to the place, it pulses, the name
is shown in Hebrew, with its anchor relation and one fact. Two seconds,
then immediately the first easy retrieval ("Tap the place you just saw").

### 2. Pin It (core mode, facet: `LOCATE`)
"Where is **Netanya**?" Blank map (no labels except already-mastered anchors at low
levels). Player taps. Scoring is continuous by distance:

- Perfect: inside the city polygon or within a tolerance radius scaled to the item
  (about 3 km for a city, the polygon itself for a region).
- Close / Off / Far bands map to FSRS grades (see learning engine).
- Feedback: line from tap to the true spot, distance in km, the true place pulses with
  its fact. Neighbors briefly labeled for context.

Pinch zoom is allowed but the starting view adapts: beginners get a regional zoom,
experts get the whole country.

### 3. Name It (facet: `NAME`)
A place is highlighted on the map. Level 1: pick from 4 plausible options (same
region, similar type). Level 2: pick from 4 including a known confusable. Level 3:
type the name with autocomplete that only kicks in after 3 letters (true recall).

### 4. Which Way? (facet: `RELATE`)
Relational questions that fix hierarchical distortions:
- "Which is further north: Tiberias or Haifa?"
- "Is Modi'in closer to Jerusalem or to Tel Aviv?"
- "Driving from Beersheba to Eilat, which do you pass: Mitzpe Ramon or Arad?"
- "Estimate the distance from Haifa to Eilat" (slider, scored by error ratio).

### 5. Map Builder (puzzle, facet: `LOCATE` for regions)
Drag a region or district shape into place on the outline, one shape per question.
The shape starts in the sea off the middle of the coast (the same spot for every
shape, so the start gives nothing away); one finger drags it, two fingers pan and zoom
the map, and "here" hands it in. Scored by how far it lands from its place. Satisfying
and very tactile on a phone.

Map Builder practices the same `LOCATE` card as Pin It. In the daily session, a due
review of a region or a district is its Map Builder question half of the time; places
still being learned, and struggling places, get Pin It. Home also offers a round of
it, on the regions and districts the player has met.

### 6. Focused practice (any facet)
A round of 10 run entirely by the answer log, not by the scheduler. Every place the
player has met can come up, the ones most in need first: missed most in their last 10
answers, and answered least so far, even when those answers were right. Each place is
asked one way, the skill (find, name, relate) its log shows the most need for, and a
miss comes back once at the end of the round. Answers are logged, so the round adapts
as the player improves, but they leave the schedule as it was. It replaced Trace It
(drawing a river with a finger), which was removed; rivers and streams are Pin It only.

### 7. Road Trip (free play)
A chain of stops: "You start in Ashdod. Drive on to Rehovot, then Herzliya, Netanya and
Hadera." Each stop after the start is a Pin It question with its feedback; the route
draws across the map from stop to stop as they are found, and the summary shows the
whole way. Trips are planned on the spot from the places the player has learned (towns,
lakes, mountains, reserves, sites, ports and airports): five stops, each leg 12 to 70 km,
keeping the road's direction. Builds route knowledge on top of survey knowledge, and is
great for tourists. Written trips with a story ("You land at Ben Gurion. Drive to the
Dead Sea, then to Masada, then down to Eilat") can come later.

### 8. Daily Challenge (social)
Same 10 questions for everyone that day, Wordle style. Score = total km error. A
shareable emoji result (no spoilers). Drives word of mouth.

### 9. Blitz (free play)
60 seconds, as many correct pins as possible, from places you have *already learned*
only (past their learning steps), each once in random order before any comes back.
There is no feedback card to stop the clock: the next place comes at once, with the
last answer shown faintly on the map and its points above the question. Fun, fast, and
still a valid retrieval: results feed the scheduler at a reduced weight because speed
pressure changes the signal. Home offers it once 4 places are learned. Each round that
runs to the end of the clock is kept, so the summary and home show the best score to
beat.

## Progression and structure

### Layers (the curriculum skeleton, detail in [03-content-curriculum.md](03-content-curriculum.md))

1. **Frame:** borders and neighbors, seas, Kinneret, Dead Sea, Jordan River.
2. **Regions:** natural regions (Galilee, Golan, Coastal Plain, Sharon, Shephelah,
   Judean Mountains, Negev, Arava, Jordan Valley...) and administrative districts.
3. **Anchor cities:** the 15 most important cities.
4. **Cities and towns:** by region, in small units.
5. **Nature:** mountains, rivers, valleys, craters, deserts.
6. **Infrastructure:** major highways, airports, ports.
7. **Heritage and tourism:** Masada, Caesarea, Megiddo, Ein Gedi, etc.
8. **Deep cuts:** small localities, kibbutzim, wadis.

A unit is unlocked when its parent anchors are learned (not when a timer passes).
Players can also jump ahead with a **placement test** ("I already know the big
cities"), which seeds the scheduler with the results.

### Mastery map (the main reward)
- Each place is colored by its current **retrievability** (probability of recall
  today), from gray (never learned) through pale to full color.
- Regions show an aggregate percentage. "Galilee 87%".
- The fading is honest and motivating: "3 places in the Negev are fading. 2 minute
  review?"

### Ranks
Tourist, Hiker, Guide, Navigator, Cartographer, Sabra (in Hebrew with both genders,
"מטייל/ת"). Tied to the number of known places (LOCATE stable for 21 days): 0, 10, 30,
60, 100 and 150, the last being nearly the whole core. The rank is always the current
one, so a forgotten place can take it down a step until it is relearned. Home shows the
rank; the progress screen shows it with how many more known places the next one needs.

### Achievements (competence focused)
Fifteen, each a piece of real knowledge, never time spent or answers given:
the first known place; the frame (seas, lakes, the Jordan, the neighbors and the five
anchor cities); all 19 natural regions; all 7 districts; every main city (importance 1
and 2) found within 3 km in its latest Pin It answer; around the Kinneret (the lake
and every place within 5 km of its shore); one whole natural region; all 118 cities;
all of nature, all ports and airports, all heritage sites; a no-miss session (10
answers or more, all remembered); a place remembered after 30+ days without review;
7 and 30 days in a row (with streak freezes).

An achievement is stored when first earned and kept, even if a place in it later
fades: the rank and the map stay honest instead. The progress screen lists the earned
ones with their date on a tinted row with a check badge, then the rest with a ring
that fills as they get closer and how far along they are ("5 of 14"); an
all-or-nothing one (a no-miss session, a long memory) just says "not earned yet".
Around the Kinneret names its places, and one whole region names the region it is
measuring now (the one with the largest share known). Nature and heritage are named
"נוף הארץ" and "שורשים", apart from the theme rows below them. The session summary
names any earned in that session, and a new rank.

### Social (opt in)
Friends list, weekly league of ~20 players with promotion/demotion, daily challenge
comparison. Leaderboards rank *learning* (places strengthened) not just raw speed so
beginners can compete.

## Session design rules

- Session length 15 to 25 questions, adjustable (short / normal / long).
- First and last question are likely successes (start confident, end positive).
- Once a day is the minimum, not the limit: the player may play as many sessions as
  they like. New places per session are at most a third of its questions (5 of 15, 8
  of 25), and per day as the player sets the pace: 20 (normal), 40 (fast) or no limit.
  At every pace a new place comes in only while few places are still being learned
  and most recent answers are right (docs/04-learning-engine.md, "Pace").
- An item answered wrong is retried later in the same session (3 to 6 questions later)
  after its feedback, until answered right once.
- Mode mix per session is chosen by the engine, not the player, in the daily session.
  Free play lets the player choose.

## Feedback and feel

- Haptic tick on tap, gentle success sound, a different softer sound on miss (never
  harsh).
- Distance feedback is visual first: a line and a number, color coded.
- Points as in GeoGuessr: up to 5,000 for each answer, falling with the distance (half
  for every 10 km off), with a running score and a session total. Points are a score
  for the player and never change the schedule (docs/04-learning-engine.md, "Points").
- The fact card appears for 2 to 3 seconds and can be tapped to expand ("Learn more"),
  never blocking flow.
- Optional confidence buttons (sure / think so / guess) before revealing, unlocked
  after the tutorial. Confidence is used for calibration stats and hypercorrection.

## Monetization

**Decision: completely free.** No ads, no purchases, no subscriptions, no energy
systems. Every place and every mode is available to everyone. This keeps the design
honest: nothing ever competes with learning for the player's attention. A voluntary
"support the project" link may be added later.
