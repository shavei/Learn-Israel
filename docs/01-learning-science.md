# 01. The Science of Remembering: What the Research Says

This document summarizes the evidence base we design on. Each principle ends with a
**"So in the game"** line that turns the finding into a concrete design rule. Full
citations are in [references.md](references.md).

---

## 1. Retrieval practice (the testing effect)

**Finding.** Pulling information *out* of memory strengthens it far more than putting
it *in* again. Students who practiced recalling a text remembered much more a week later
than students who reread it, even though rereaders *felt* more confident
(Roediger & Karpicke, 2006). In Karpicke & Roediger (2008), once an item was learned,
dropping it from further *testing* crushed long-term recall, while dropping it from
further *study* barely mattered. Meta-analyses (Rowland, 2014; Adesope et al., 2017)
confirm a robust, medium-to-large effect across ages and materials. Dunlosky et al.
(2013) rated practice testing as one of only two "high utility" study techniques.

**So in the game:** almost every interaction is a retrieval attempt ("Where is Afula?",
"What is this valley called?"). Passive viewing exists only as a short introduction
step before the first retrieval.

## 2. Spaced (distributed) practice

**Finding.** The same amount of practice spread over time beats massed practice
(Ebbinghaus, 1885; Cepeda et al., 2006, meta-analysis of 254 studies). The best gap
grows with how long you want to remember: roughly 10 to 20 percent of the target
retention interval (Cepeda et al., 2008). Memory decays along a forgetting curve, and
each successful retrieval flattens it.

**So in the game:** a spaced repetition scheduler (FSRS, see
[04-learning-engine.md](04-learning-engine.md)) decides *when* each place comes back.
Short daily sessions beat long weekly binges, and the game is built around a daily
review loop.

## 3. Desirable difficulties

**Finding.** Conditions that make practice feel harder (spacing, interleaving,
generation, varied context) often make learning stronger, while conditions that feel
fluent (rereading, blocked practice) create an *illusion of competence*
(Bjork, 1994; Bjork & Bjork, 2011; Kornell & Bjork, 2008).

**So in the game:** we deliberately prefer recall over recognition, mix item types,
and vary map zoom and orientation cues. But difficulty must stay *desirable*: if the
learner fails most of the time, motivation and learning collapse (see section 10).

## 4. Interleaving and discrimination

**Finding.** Mixing similar categories during practice improves the ability to tell
them apart (Kornell & Bjork, 2008, painting styles; Rohrer & Taylor, 2007, math
problems). Blocked practice is easier but leads to worse discrimination later.

**So in the game:** places that are commonly confused (Ramat Gan / Givatayim /
Bnei Brak, Kiryat Gat / Kiryat Malakhi, Ashdod / Ashkelon, Upper vs Lower Galilee) are
tracked as **confusable pairs** and deliberately mixed together in sessions once each
is individually known.

## 5. Generation effect and recall vs recognition

**Finding.** Information you generate yourself is remembered better than information
you read (Slamecka & Graf, 1978). Free recall is harder than multiple choice, and
produces stronger memory, although multiple choice still helps if the distractors are
plausible (Little, Bjork, Bjork & Angello, 2012).

**So in the game:** each place moves through a **difficulty ladder**: recognize (pick
from 4) then locate (tap on a blank map) then produce (type or choose name with no
hints). Tapping a blank map is itself a generative act: the player must produce a
location, not choose one.

## 6. Feedback, errors, and the hypercorrection effect

**Finding.** Testing works best with feedback, and feedback that shows the correct
answer is better than right/wrong only (Pashler et al., 2005; Butler & Roediger, 2008).
Making an error and then getting corrected is fine, even helpful, compared with never
attempting (Kornell, Hays & Bjork, 2009; Metcalfe, 2017). Errors made with **high
confidence** are corrected *more* readily than low-confidence ones, the hypercorrection
effect (Butterfield & Metcalfe, 2001).

**So in the game:** after every answer, show the true location immediately, draw a
line from the guess to the answer, and state the distance in km. Wrong items are
re-queued a few minutes later within the same session. An optional confidence tap
("sure / think so / guess") makes high-confidence errors memorable and improves
calibration.

## 7. Dual coding and conjoint retention

**Finding.** Information encoded both verbally and visually is remembered better than
either alone (Paivio, 1986). For maps specifically, Kulhavy & Stock's **conjoint
retention** work shows that facts learned alongside a map are recalled better because
the map acts as a spatial retrieval scaffold (Kulhavy, Stock & Kealy, 1993).

**So in the game:** every place has a name, a position, and one short memorable fact
("Beersheba: capital of the Negev", "Kiryat Shmona: the northernmost city at the end of
Route 90"). The fact appears in feedback, and the *map* is always the canvas, never a
plain text list.

## 8. How people actually store maps: hierarchy, anchors, and distortion

**Finding.** Spatial memory is **hierarchical**. People store regions and then places
within them, and they reason from the region (Stevens & Coupe, 1978; Hirtle & Jonides,
1985). This causes predictable errors (people think Reno is east of San Diego because
Nevada is east of California). People also rotate and align remembered shapes to
straighten them (Tversky, 1981). Learning from a map gives good survey knowledge
(relative positions, straight-line distances), while navigation gives route knowledge
(Thorndyke & Hayes-Roth, 1982). Good map learners use **anchors**: they learn a few
salient landmarks first and then encode other places relative to them (Thorndyke &
Stasz, 1980).

**So in the game:**
- Teach **frame first, then detail**: coastline, neighbors, Kinneret, Dead Sea, Jordan
  River, then regions, then cities inside regions.
- Every new place is introduced relative to an anchor already known ("Hadera: on the
  coast, halfway between Tel Aviv and Haifa").
- Add **relational questions** ("Which is further north: Safed or Akko?") that directly
  target hierarchical distortions.
- Keep north-up for the core game, but use occasional rotated or zoomed views at high
  mastery levels to build a flexible mental map rather than a memorized picture.

## 9. Cognitive load and chunking

**Finding.** Working memory holds only about 4 meaningful chunks (Cowan, 2001).
Instruction that overloads it fails (Sweller, 1988). Chunking related items into
meaningful groups expands what can be handled.

**So in the game:** introduce new places in small batches (3 to 5) grouped by region.
Only the relevant layer is visible: no clutter of 200 labels. Units are geographic
chunks ("Gush Dan", "Haifa Bay", "Arava") so each batch shares context.

## 10. The sweet spot: around 85 percent success

**Finding.** Learning is fastest when tasks are hard enough to produce some errors but
easy enough that most attempts succeed. A theoretical and empirical analysis found an
optimum near an 85 percent success rate (Wilson et al., 2019). This matches flow
theory: engagement peaks when challenge matches skill (Csikszentmihalyi, 1990).

**So in the game:** the session composer targets roughly 80 to 90 percent success,
adding new items when the player is cruising and pulling back to review when they
struggle. FSRS's target retention of 0.9 lands in the same zone.

## 11. Motivation that does not undermine learning

**Finding.** Self-determination theory: lasting motivation comes from **autonomy,
competence, and relatedness** (Ryan & Deci, 2000). Expected, controlling rewards can
undermine intrinsic interest (Deci, Koestner & Ryan, 1999). Gamification has a small but
real positive effect on learning when it supports these needs, and none when it is
decorative (Sailer & Homner, 2020).

**So in the game:**
- *Competence:* the central reward is **your own map filling in** with color as places
  are mastered, and visibly fading as memory decays. Progress is honest.
- *Autonomy:* players choose which region to explore next and which modes to play.
- *Relatedness:* friends, shared daily challenges, a weekly league (opt in).
- Points and streaks exist but are gentle: streak freezes, no guilt-trip notifications.

## 12. Sleep and the daily rhythm

**Finding.** Sleep consolidates newly learned material (Diekelmann & Born, 2010).
Learning just before sleep and reviewing the next day is especially effective.

**So in the game:** a single daily session (5 to 10 minutes) is the default. An
optional evening reminder fits the consolidation window. New material is capped per
day so tomorrow's reviews stay manageable.

## 13. Memory palaces and elaboration (used lightly)

**Finding.** The method of loci and elaborative mnemonics can dramatically improve
recall, and trained memory athletes use them (Dresler et al., 2017). Elaborative
interrogation ("why is it here?") has moderate support (Dunlosky et al., 2013).

**So in the game:** the map *is* already a memory palace: every fact has a location. We
add "why" facts that link geography to meaning (Eilat is on the Red Sea, so it is
Israel's port to Asia; the Hula Valley is a bird migration corridor). Optional mnemonic
hints are shown after repeated failures on an item, not before.

---

## Summary: the ten design rules

| # | Rule | Source principle |
|---|------|------------------|
| 1 | Every turn is a retrieval, not a reading | Testing effect |
| 2 | A scheduler decides when items return | Spacing |
| 3 | Recall over recognition, as soon as possible | Generation |
| 4 | Immediate, rich, spatial feedback after every answer | Feedback |
| 5 | Frame first, anchors next, detail last | Hierarchical spatial memory |
| 6 | Small regional batches of 3 to 5 new items | Cognitive load |
| 7 | Mix confusable places on purpose | Interleaving |
| 8 | Keep success near 85 percent | Optimal difficulty |
| 9 | Name + place + one fact for every item | Dual coding |
| 10 | Rewards show real competence; the map is the trophy | Self-determination |
