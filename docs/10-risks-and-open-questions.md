# 10. Risks and Open Questions

## Open questions (decisions needed)

| # | Question | Options | Recommendation | Blocking for |
|---|----------|---------|----------------|--------------|
| 1 | How are borders and contested areas shown? | De facto lines; 1949 lines; both with a toggle | De facto lines as default plus an optional, neutrally labeled Green Line layer | Phase 0 content |
| 2 | Primary audience at launch? | Students; olim/visitors; general adults | General adults + students, Hebrew first, English at launch | Phase 1 content tone |
| 3 | Monetization model? | One-time unlock; free; school licenses | Free core, one-time unlock for deep tiers; no ads in sessions | Phase 3 |
| 4 | App name and branding? | Learn Israel, Mapa, Pin Israel, ... | Test 3 names with target users | Phase 3 |
| 5 | Is Arabic in v1? | Yes / later | Later (Phase 4), but design for it now (RTL, data model) | No |
| 6 | Target children under 13? | Yes / no | Not at launch (simplifies Families policy); revisit with classroom mode | Phase 3 |
| 7 | Backend provider for social features? | Supabase; Firebase | Decide in Phase 2 | Phase 3 |
| 8 | Team and timeline? | | Needed to turn S/M/L into dates | Planning |

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
