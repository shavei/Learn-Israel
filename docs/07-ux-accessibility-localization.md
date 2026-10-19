# 07. UX, Accessibility, and Localization

## First-run experience (under 60 seconds to the first "aha")

1. Language picker (Hebrew, English, Arabic later), auto-selected from device locale.
2. One screen: "Build a map of Israel in your head, 5 minutes a day."
3. Immediately play: the map outline appears, Discover shows Tel Aviv, then
   "Tap Tel Aviv". Success, the city lights up. Then Jerusalem, then Haifa.
4. "Want to skip what you know?" Placement test (optional) or continue.
5. Ask for a daily reminder time *after* the first session, not before.

No signup, no permissions on first run.

## Key screens

- **Home:** the player's mastery map (big), today's session button with a count
  ("12 reviews, 4 new"), streak, current unit.
- **Session player:** full-screen map, question bar on top, minimal chrome, feedback
  card sliding up from the bottom.
- **Session summary:** the map animates the places strengthened today; stats (accuracy,
  average error km, new places); single "Done" button.
- **Explore:** pick a region, see its units, browse learned places (study view with
  labels on, which is allowed but does not count as practice).
- **Progress:** region percentages, known places count, ranks, achievements, a
  history chart of average error over time.
- **Settings:** language, session length, daily new cap, reminders, map layers,
  sounds/haptics, confidence buttons on/off, data and privacy.

## Visual design

- Clean, flat cartographic style: muted land, clear sea blue, one strong accent color
  for the current question.
- Mastery colors: gray (unknown) to a warm full color (mastered), with a separate
  outline treatment so color is not the only signal.
- Light and dark themes. Dark mode map palette designed, not just inverted.
- Large tap targets for UI; the map itself is the target in Pin It, so allow
  zoom and provide generous tolerance at default zoom.

## Accessibility

- **Color:** never rely on color alone (patterns or outlines for mastery states;
  check against deuteranopia and protanopia simulations). WCAG AA contrast for text.
- **Text size:** respect system font scale; layouts tested at 200 percent.
- **Motor:** adjustable tolerance multiplier for Pin It; optional "tap then confirm"
  mode to avoid accidental taps; no time pressure outside Blitz.
- **Screen readers:** TalkBack labels on all controls. A pure map tapping game is
  inherently visual; provide an accessible mode built on Name It / Which Way questions
  with spoken descriptions ("highlighted place: coastal city between Tel Aviv and
  Haifa").
- **Motion:** respect "remove animations" setting (no pulsing, instant transitions).
- **Sound:** all audio feedback has a visual equivalent.

## Localization

- **Hebrew first** (largest audience, curriculum alignment), English at launch,
  Arabic in a later phase (significant Arabic-speaking population in Israel).
- Full **RTL** support: Compose layouts mirror automatically; check custom map
  overlays and the feedback card manually.
- Map is never mirrored (north up, east right in every language).
- Place names come from content data, not Android string resources, so all languages
  share one pipeline.
- Numbers and distances: km everywhere; localized number formatting.
- Translation review by native speakers; place names follow official CBS / Survey of
  Israel spellings where available.

## Notifications

- One optional daily reminder at a time the player chose.
- Copy is factual and kind ("8 places are ready for review"), never guilt based.
- If the player ignores reminders for a week, reduce frequency automatically.
- A "Welcome back" flow after a long break: a short catch-up session instead of a
  huge review pile (cap reviews, prioritize at-risk anchors).
