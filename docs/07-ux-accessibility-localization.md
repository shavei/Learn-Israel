# 07. UX, Accessibility, and Localization

## First-run experience (under 60 seconds to the first "aha")

1. One screen: "Build a map of Israel in your head, 5 minutes a day."
2. Immediately play: the map outline appears, Discover shows Tel Aviv, then
   "Tap Tel Aviv". Success, the city lights up. Then Jerusalem, then Haifa.
3. "Want to skip what you know?" Placement test (optional) or continue.
4. Ask for a daily reminder time *after* the first session, not before.

No signup, no permissions on first run.

## Key screens

- **Home:** the player's mastery map (big), today's session button with a count
  ("12 reviews, 4 new"), streak, current unit.
- **Session player:** full-screen map, question bar on top, minimal chrome, feedback
  card sliding up from the bottom. In landscape the question and the cards stand in a
  side panel and the map is framed beside it, so a card never hides the place in
  question. Leaving with an answer given and the session not over (Exit or system back)
  asks first; leaving the map test says it discards the test, and its answers are deleted.
  Between map test questions the tap stays on the map with "הסימון נקלט", never a sign of
  right or wrong. Naming options a few kilometers apart are viewed close up, and a wrong
  name is marked where that place is.
- **Session summary:** the map lights up the places practiced in the session (a road
  trip shows its route, solid to stops found and dashed to stops missed); stats fit the
  kind of session (the daily session: accuracy, average error km, new places; Map Builder:
  places found; focused practice: no new places line; the placement test lists the places
  found). A hard session ends on a kind line. Practice rounds and free play offer "play
  again" when another round can be planned. One button leads back, and says where (after
  the pre-test, to the welcome screen).
- **Explore:** pick a region, see its units, browse learned places (study view with
  labels on, which is allowed but does not count as practice).
- **Progress:** region percentages, known places count, the rank, achievements,
  learned confusable pairs, a history chart of average error over time (a scale from
  0 km, lower is better, the first day and today written under its ends). Before the
  first practice the screen invites the player to start one instead of showing
  zeros, and sections with nothing in them yet start folded. Lines stop at 640 dp
  wide in landscape. Counts use Hebrew plural forms ("מקום אחד", "יומיים").
- **Settings:** session length, daily new cap, reminders, map layers,
  sounds/haptics, confidence buttons on/off, data and privacy.

## Visual design

- Clean, flat cartographic style: muted land, clear sea blue, one strong accent color
  for the current question.
- Mastery colors: gray (unknown) to a warm full color (mastered), with a separate
  outline treatment so color is not the only signal.
- Light and dark themes. Dark mode map palette designed, not just inverted.
- Two map styles, chosen in settings with previews: standard (flat) and relief
  (hypsometric tint with hillshading, which shows height). Each has its own light and
  dark design; water, borders and outlines are the same in both.
- Large tap targets for UI; the map itself is the target in Pin It, so allow
  zoom and provide generous tolerance at default zoom.
- Map Builder: one finger drags the shape, two fingers pan and zoom, so precision never
  needs a steady single finger at the full-country view. Nothing is final on lifting
  the finger: "here" hands the answer in.

## Accessibility

- **Color:** never rely on color alone (patterns or outlines for mastery states;
  check against deuteranopia and protanopia simulations). WCAG AA contrast for text.
- **Text size:** respect system font scale; layouts tested at 200 percent.
- **Motor:** adjustable tolerance multiplier for Pin It; optional "tap then confirm"
  mode to avoid accidental taps; no time pressure outside Blitz, which is optional free
  play. Its clock sits outside the question's live region, so a screen reader reads it
  on demand instead of every second.
- **Screen readers:** TalkBack labels on all controls. A pure map tapping game is
  inherently visual; provide an accessible mode built on Name It / Which Way questions
  with spoken descriptions ("highlighted place: coastal city between Tel Aviv and
  Haifa").
- **Motion:** respect "remove animations" setting (no pulsing, instant transitions).
- **Sound:** all audio feedback has a visual equivalent.

## Language

- **Hebrew only** (decision). One language keeps content production and review
  manageable for a solo developer, and fits the target audience.
- The whole UI is **RTL**. Compose mirrors layouts automatically; check custom map
  overlays and the feedback card manually.
- Map is never mirrored (north up, east right).
- Place names and facts come from content data, not Android string resources. The
  data model still allows other languages, so adding one later is a content task, not
  a rewrite.
- Distances in km with Hebrew number formatting.
- Place names follow official CBS / Survey of Israel spellings.

## Notifications

- One optional daily reminder at a time the player chose.
- Copy is factual and kind ("8 places are ready for review"), never guilt based.
- If the player ignores reminders for a week, reduce frequency automatically.
- A "Welcome back" flow after a long break: a short catch-up session instead of a
  huge review pile (cap reviews, prioritize at-risk anchors).
