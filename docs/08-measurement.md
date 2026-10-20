# 08. Measuring Whether People Actually Learn

Engagement metrics alone can lie: an app can be fun and teach nothing. We measure
learning directly, and make design decisions with experiments.

## Learning metrics (primary)

| Metric | Definition | Target (initial guess, to calibrate) |
|--------|------------|--------------------------------------|
| Retention at review | Share of due reviews answered Good or better | 85 to 92 percent (FSRS target 0.90) |
| Average locate error | Mean km error on LOCATE, per tier, over time | Falls steadily; Tier 2 under 5 km after 2 weeks |
| Long-term retention probe | Accuracy on items not reviewed for 30+ days (occasional probe questions) | Over 70 percent |
| Known places | Cards with stability >= 21 days | Growth per active week |
| Calibration | Gap between confidence and accuracy | Shrinks over time |
| Confusion rate | Rate of tapping a known confusable instead of the target | Falls after interleaving |
| Pre/post test | Same 20-item map test at onboarding (optional) and after 30 days | Large gain; this is our headline evidence |

Phase 1d computes these on the device (`core/learning/.../Stats.kt` and `MapTest.kt`):
retention counts reviews of cards in review that were due, the long-term probe uses
reviews that came after 30+ days without one, and the pre/post test is the fixed
20-place map test, taken without feedback. Players see them on the progress screen and
can share them as text.

The FSRS model's own prediction error (log loss / calibration of predicted vs actual
recall) tells us how well the scheduler fits map learning, and guides parameter
fitting.

## Engagement metrics (secondary)

- D1 / D7 / D30 retention, sessions per active week, session completion rate.
- Share of players finishing the tutorial and first session.
- Notification opt-in and opt-out rates.
- Mode popularity in free play.

## Experiments (A/B tests) worth running

Each one tests a learning-science hypothesis in our specific domain:

1. **Confidence buttons on vs off:** does hypercorrection improve long-term accuracy
   enough to justify the extra tap?
2. **Desired retention 0.85 vs 0.90 vs 0.95:** workload vs retention trade-off.
3. **Anchor hints at Discover vs no hints:** does relational encoding help?
4. **Interleaved confusables vs not:** effect on confusion rate.
5. **Feedback richness:** line + distance + fact vs line + distance only.
6. **New items per session cap:** 3 vs 5.

Rules: pre-register the metric, use a delayed retention measure (not same-session
accuracy), run until a planned sample size.

## Data collection and privacy

- All review data lives on device by default.
- Opt-in, anonymous upload of review logs (no tap coordinates outside Israel's
  bounding box, no device identifiers beyond a random install id).
- Clear in-app explanation: "Help improve how the game teaches".
- Aggregate dashboards only. Delete-my-data option.

## Playtesting plan

- **Paper and Figma prototypes:** 5 to 8 people per round, watch them play,
  note confusion.
- **Alpha (internal):** 20 people, 2 weeks, daily use, pre/post map test.
- **Closed beta:** 200 to 500 people via Google Play closed testing, including a
  school class if possible.
- Interview a handful of players each round. Key questions: "What did you learn?",
  "When did it feel too hard / too easy?", "Why did you stop?"
