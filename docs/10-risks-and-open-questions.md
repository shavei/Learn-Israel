# 10. Decisions and Risks

## Decisions (made 2026-10-19 and later)

| # | Question | Decision | Consequences |
|---|----------|----------|--------------|
| 1 | How are borders and contested areas shown? | De facto lines of control as the main map, plus an optional, neutrally labeled Green Line layer in settings | Pipeline builds both line sets; fact lines stay geographic, not political |
| 2 | Primary audience at launch? | General adults and Israeli school students | Curriculum-aligned units, casual-friendly sessions |
| 3 | Monetization? | Completely free: no ads, no purchases, no subscription | All tiers and modes open to everyone; costs kept near zero (offline first, backend only when needed) |
| 4 | App name? | **על המפה** (Al HaMapa), renamed from מפה (Mapa) on 2026-10-22: a bare common noun named the app no better than a search for it would | Store listing and branding use the new name and the atlas icon; the package id `com.shavei.mapa` and `mapa.apk` stay, so installed apps keep updating with their progress |
| 5 | Languages? | Hebrew only | Full RTL UI; content written and reviewed in Hebrew only; data model still allows adding languages later |
| 6 | Target children under 13? | Not at launch | Google Play Families policy not required yet; revisit with a classroom mode |
| 7 | Backend for social features? | Decide later | Phases 0 to 2 are fully offline; choose Supabase or Firebase at the start of Phase 3 |
| 8 | Team? | One developer working with Claude Code | Tight scope per phase, heavy automated testing, each phase ships something usable |
| 9 | When do nature, ports and heritage (tiers 4 to 6) come in? (2026-10-20) | After the whole core (frame, regions, cities), as the curriculum is ordered | Several weeks of play before the first mountain or site; revisit if the exit test shows players stop before Tier 4 |
| 10 | Highways? (2026-10-20, built 2026-10-21) | Routes 1, 2, 4, 6, 40, 60 and 90, found with Pin It anywhere along them | They count toward no region percentage (each crosses many), are no Road Trip stops, and the gateways achievement stays ports and airports |
| 11 | Heritage sites inside a known city (the Old City, Yad Vashem, Old Jaffa, the Bahá'í Gardens, Old Akko)? (2026-10-20) | Kept, with Pin It and Name It like any place | Easy to find once the city is known; they still teach the name and the fact |
| 12 | Review of the tier 4 to 6 Hebrew text? (2026-10-20) | Shipped without a separate review | Names, hints and facts are fixed as testers report problems |
| 13 | Do nature and heritage places count in the region percentages? (2026-10-20) | Yes: a region counts every place in it | A region is 100 percent only when its cities, mountains, streams and sites are all known; percentages drop once tier 4 places exist |
| 14 | More tier 4 to 6 places now? (2026-10-20) | No: 49 are enough until the playtest shows players reach them | More reserves, valleys and sites wait; Haifa airport and Tel Dan stay out (each sits beside another place) |
| 15 | Where do Map Builder and Trace It live? (2026-10-20) | Both in the daily session, as another way to find a known region or line, and as rounds from home | They share the LOCATE card with Pin It; a home round brings no new places |
| 16 | What is one Map Builder question? (2026-10-20) | One shape per question, dragged into place | One card per question, as in the rest of the session; a whole-puzzle round can come later |
| 17 | Which lines can Trace It ask for? (2026-10-20) | The Jordan and all 8 streams, the Dan and the Hermon included | The craters stay Pin It only; the highways were to join it, but Trace It was later removed (24) |
| 18 | What can free play ask? (2026-10-20) | Only places past their learning steps, for Blitz and Road Trip alike | Free play never teaches a new place; Blitz needs 4 learned places, Road Trip a trip of 3 stops |
| 19 | Does a road trip count fully for the scheduler? (2026-10-20) | No: the same reduced weight as Blitz (a miss always, a success only when due within 2 days) | Playing for fun never pushes reviews far ahead; a road trip still catches places being forgotten |
| 20 | Written trips or trips planned from the player's places? (2026-10-20) | Planned on the spot from learned places | Every player gets trips from the first days; written story trips (Ben Gurion to Eilat) can come later, once players know tiers 4 to 6 |
| 21 | Can a rank go down? (2026-10-20) | Yes: the rank always follows the places known today | A rank means real knowledge; a forgotten place costs a step until it is relearned |
| 22 | Are achievements kept once earned? (2026-10-20) | Yes: stored with the date first earned | Earned moments are not taken back; the map and the rank carry the honest current state |
| 23 | When does a pair of places become a learned confusable? (2026-10-20) | After 2 confusions, either way round, for that player | Pairs appear quickly from real mistakes; global pairs wait for the log upload (2e) |
| 24 | Trace It, or a mode run by the answer log? (2026-10-20) | Focused practice replaces Trace It on home, and Trace It is removed everywhere | Rivers and streams are found with Pin It only; older logs keep their TRACE answers |
| 25 | What does focused practice ask, and does it move the schedule? (2026-10-20) | Every place met, by one score of recent misses and few answers; 10 places, each the way most in need, a miss once more; answers update the tally only | The round adapts to the player without pulling reviews earlier or pushing them later |
| 26 | Opt-in log upload (2e) before launch? (2026-10-21) | Postponed until after the public launch | A global parameter fit needs many players; until then results come through "Share results", and the game stays fully offline. Personal parameter fitting on the phone (Phase 4) needs no server |

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
