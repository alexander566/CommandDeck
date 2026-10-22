#!/bin/bash
# Builds every mod jar and the Stream Deck plugin into dist/.
set -e
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}
rm -rf dist && mkdir dist
for e in mod/eras/*/; do
	[ -f "$e"build.gradle ] || continue
	mod/gradlew -p "$e" clean build --no-daemon -q
done
mod/pack-forge-mojmap.sh neoforge-1.20.5-1.21 1.20.6-1.21 '[1.20.6,26)' 21
mod/pack-forge-mojmap.sh neoforge-26.x 26.x '[26.1,)' 25
cp mod/eras/*/build/libs/commanddeck-*.jar dist/
npx -y @elgato/cli@latest pack streamdeck/io.github.alexander566.commanddeck.sdPlugin -o dist --force >/dev/null
mv dist/io.github.alexander566.commanddeck.streamDeckPlugin dist/CommandDeck.streamDeckPlugin
ls -l dist
