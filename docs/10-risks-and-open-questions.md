# 10. Decisions and Risks

## Decisions (made 2026-10-19 and later)

| # | Question | Decision | Consequences |
|---|----------|----------|--------------|
| 1 | How are borders and contested areas shown? | De facto lines of control as the main map, plus an optional, neutrally labeled Green Line layer in settings | Pipeline builds both line sets; fact lines stay geographic, not political |
| 2 | Primary audience at launch? | General adults and Israeli school students | Curriculum-aligned units, casual-friendly sessions |
| 3 | Monetization? | Completely free: no ads, no purchases, no subscription | All tiers and modes open to everyone; costs kept near zero (offline first, backend only when needed) |
| 4 | App name? | **Mapa** (מפה) | Package id, store listing, and branding use Mapa |
| 5 | Languages? | Hebrew only | Full RTL UI; content written and reviewed in Hebrew only; data model still allows adding languages later |
| 6 | Target children under 13? | Not at launch | Google Play Families policy not required yet; revisit with a classroom mode |
| 7 | Backend for social features? | Decide later | Phases 0 to 2 are fully offline; choose Supabase or Firebase at the start of Phase 3 |
| 8 | Team? | One developer working with Claude Code | Tight scope per phase, heavy automated testing, each phase ships something usable |
| 9 | When do nature, ports and heritage (tiers 4 to 6) come in? (2026-10-20) | After the whole core (frame, regions, cities), as the curriculum is ordered | Several weeks of play before the first mountain or site; revisit if the exit test shows players stop before Tier 4 |
| 10 | Highways? (2026-10-20) | Left out for now, to be added later | Routes 1, 2, 4, 6, 40 and 90 wait for a later release (with Trace It or once the OSM download works) |
| 11 | Heritage sites inside a known city (the Old City, Yad Vashem, Old Jaffa, the Bahá'í Gardens, Old Akko)? (2026-10-20) | Kept, with Pin It and Name It like any place | Easy to find once the city is known; they still teach the name and the fact |
| 12 | Review of the tier 4 to 6 Hebrew text? (2026-10-20) | Shipped without a separate review | Names, hints and facts are fixed as testers report problems |

## Still open

- Exact tolerance values and grade thresholds for Pin It (tune during Phase 0 playtests).
- Whether to add a voluntary "support the project" link, given the app is free.
- Store age rating details and privacy policy text (before Phase 3 launch).

## Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Political sensitivity of the map | Bad reviews, store complaints, alienating users | Explicit documented policy (Q1), neutral facts, optional layers, human review of all content |
| Content errors (wrong location or name) | Teaches wrong facts, credibility loss | Pipeline validation, human review checklist, in-app "report a problem" button |
| Learning works but players churn | Small reach | Strong first session, mastery map reward, gentle reminders, daily challenge for social pull |
| Players churn because reviews pile up | Classic SRS failure mode | Daily new-item cap, welcome-back flow that caps backlog, honest "done for today" |
| Gamification that rewards the wrong thing (speed, grinding) | Shallow learning | Ranks tied to stability, reduced weight for blitz reviews, leagues by places strengthened |
| FSRS defaults fit map items poorly | Over or under reviewing | Track prediction calibration; fit global parameters once data exists |
| Map rendering performance on low-end phones | Janky experience | Pre-projected, simplified geometry; path caching; Macrobenchmark in CI |
| Tap precision on small screens | Frustration, unfair grading | Item-scaled tolerance, zoom, tolerance multiplier setting, tap-then-confirm option |
| Licensing (ODbL, GPL libraries) | Legal issues | Attribution screen; avoid GPL dependencies; record sources and versions |
| Scope creep (too many modes) | Never ships | Phase gates; MVP is 4 modes and 130 places |
