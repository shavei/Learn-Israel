# Mapa (Learn-Israel)

An Android game, in Hebrew, that teaches the geography of Israel, built on the science of how
people actually remember.

> **Status:** planning. This repository currently contains the design plan only. No
> app code yet.

## The idea in one paragraph

Your own map of Israel starts gray. Each day a 5 to 10 minute session asks you to
find and name places on a blank map. Every answer gets instant spatial feedback (a
line from your tap to the true spot, the distance in km, and one memorable fact). A
spaced repetition scheduler (FSRS) brings each place back right before you would
forget it. Places you master light up on your map; places you neglect slowly fade.
You learn the big frame first (coast, seas, neighbors, the big three cities), then
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

## Next step

The key product decisions are made (see
[doc 10](docs/10-risks-and-open-questions.md)). Start Phase 0 from the
[roadmap](docs/09-roadmap.md).
