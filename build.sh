#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

rm -rf out dist
mkdir -p out dist

javac -encoding UTF-8 -d out src/mobilebae/*.java
jar cfe dist/retro-dls.jar mobilebae.MobileBae -C out .

echo "Built dist/retro-dls.jar"
