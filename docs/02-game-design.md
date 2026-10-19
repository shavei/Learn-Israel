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
Drag region or district shapes into place on the outline. Great for the frame layer
(districts, natural regions, seas, neighbors). Satisfying and very tactile on a phone.

### 6. Trace It (later, facet: `LOCATE` for lines)
Draw the path of the Jordan River, Route 6, or the coastline with a finger. Scored
by average distance between drawn path and the true line (Fréchet-like distance).

### 7. Road Trip (story mode, later)
A narrative chain: "You land at Ben Gurion. Drive to the Dead Sea, then to Masada,
then down to Eilat." Each stop is a Pin It question; the route draws across the map.
Builds route knowledge on top of survey knowledge, and is great for tourists.

### 8. Daily Challenge (social)
Same 10 questions for everyone that day, Wordle style. Score = total km error. A
shareable emoji result (no spoilers). Drives word of mouth.

### 9. Blitz (free play)
60 seconds, as many correct pins as possible, from places you have *already learned*
only. Fun, fast, and still a valid retrieval: results feed the scheduler at a reduced
weight because speed pressure changes the signal.

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
Tourist, Hiker, Guide, Navigator, Cartographer, Sabra-level (names tunable). Tied to
the number of places at high stability, so a rank means real knowledge.

### Achievements (competence focused)
"Know all 6 districts", "Pin every city over 100k residents within 3 km",
"Kinneret circle: all towns around the Sea of Galilee", "No-miss session",
"30 days in a row" (with streak freezes).

### Social (opt in)
Friends list, weekly league of ~20 players with promotion/demotion, daily challenge
comparison. Leaderboards rank *learning* (places strengthened) not just raw speed so
beginners can compete.

## Session design rules

- Session length 15 to 25 questions, adjustable (short / normal / long).
- First and last question are likely successes (start confident, end positive).
- No more than 5 new places per session and a daily cap (default 10 new per day).
- An item answered wrong is retried later in the same session (3 to 6 questions later)
  after its feedback, until answered right once.
- Mode mix per session is chosen by the engine, not the player, in the daily session.
  Free play lets the player choose.

## Feedback and feel

- Haptic tick on tap, gentle success sound, a different softer sound on miss (never
  harsh).
- Distance feedback is visual first: a line and a number, color coded.
- The fact card appears for 2 to 3 seconds and can be tapped to expand ("Learn more"),
  never blocking flow.
- Optional confidence buttons (sure / think so / guess) before revealing, unlocked
  after the tutorial. Confidence is used for calibration stats and hypercorrection.

## Monetization

**Decision: completely free.** No ads, no purchases, no subscriptions, no energy
systems. Every place and every mode is available to everyone. This keeps the design
honest: nothing ever competes with learning for the player's attention. A voluntary
"support the project" link may be added later.
