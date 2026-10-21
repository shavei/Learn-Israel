#!/usr/bin/env bash
# Rebuilds mapa-promo.mp4 from the app screenshots in docs/images.
# Needs python3 (numpy, Pillow), node with playwright, and ffmpeg.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p shots
for n in points drills free-play motivation map-styles; do python3 crop.py ../../docs/images/$n.png shots/$n; done
python3 music.py
rm -rf frames && node render.js frames
ffmpeg -y -loglevel error -framerate 30 -i frames/f%05d.png -i music.wav -c:v libx264 -preset slow -crf 20 \
  -pix_fmt yuv420p -c:a aac -b:a 160k -shortest -movflags +faststart mapa-promo.mp4
