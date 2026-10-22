# Promo video

`mapa-promo.mp4`: a 35 second vertical (1080x1920) promo in Hebrew for Reels, TikTok and Shorts.

| Time | Scene |
|------|-------|
| 0 to 3 s | Hook: "בטוחים שאתם מכירים את המפה של ישראל?" |
| 3 to 8 s | Tap on a blank map, 5,000 points |
| 8 to 13 s | A miss: the line, the distance and a fact |
| 13 to 17 s | Map Builder |
| 17 to 21 s | Blitz and Road Trip |
| 21 to 26 s | Spaced repetition and progress |
| 26 to 30 s | Relief map and dark mode |
| 30 to 35 s | The app icon, "על המפה", and "הורידו עכשיו לאנדרואיד" |

The scenes are an HTML page (`promo.html`) with a small keyframe engine; `render.js` steps it
frame by frame in Chromium. The phone screens are cropped from `docs/images`, and the music
and sound effects are synthesized by `music.py`, so there are no licensed assets besides the
Heebo font (SIL Open Font License). Run `./build.sh` to rebuild after the screenshots change.
Open `promo.html?play` in a browser to preview it live.
