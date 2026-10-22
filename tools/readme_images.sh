#!/usr/bin/env bash
# Rebuilds the README pictures in docs/images from the screenshots the screen tests save.
# Run ./gradlew test first. Needs ImageMagick.
set -euo pipefail
cd "$(dirname "$0")/.."
shots=app/build/screenshots

# picture height screens...
row() {
  local out=docs/images/$1.png height=$2; shift 2
  local args=()
  for s in "$@"; do args+=("$shots/$s.png"); done
  convert "${args[@]}" -resize "x$height" -bordercolor white -border 12 +append -bordercolor white -border 4 "$out"
  echo "$out"
}

row phase1c-flow 669 0-welcome c1-placement-ask 7-home-after c3-progress c5-settings-dark c6-home-dark-green-line
row points 860 3-ask 6-feedback 4-miss g2-summary-points
row drills 1000 b2-build-dragged b3-build-feedback b0-home-drills f1-focus-ask
row motivation 1000 d0-home-rank p6-progress-achievements d3-progress-mixed-pairs d4-summary-achievement
row free-play 1000 f2-blitz-next f3-blitz-summary f5-trip-leg f7-trip-summary
row map-styles 669 s1-settings-map-styles s2-home-relief s2b-home-relief-zoomed s4-home-relief-dark
row phase1d-flow 669 d1-map-test-ask d2-map-test-summary d3-home-map-test-offer d4-progress-stats
