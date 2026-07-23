#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

rm -rf out dist
mkdir -p out dist/lib

javac -encoding UTF-8 -cp lib/jlayer-1.0.1.jar -d out src/mobilebae/*.java
cp lib/jlayer-1.0.1.jar lib/jlayer-1.0.1-sources.jar lib/LICENSE-JLAYER.txt dist/lib/
jar cfm dist/retro-dls.jar manifest.mf -C out .

echo "Built dist/retro-dls.jar"
