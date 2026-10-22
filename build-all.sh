#!/bin/bash
# Builds every mod jar and the Stream Deck plugin into dist/.
set -e
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}
rm -rf dist && mkdir dist
for e in mod/eras/*/; do
	mod/gradlew -p "$e" build --no-daemon -q
	cp "$e"build/libs/commanddeck-fabric-*.jar dist/
done
npx -y @elgato/cli@latest pack streamdeck/io.github.alexander566.commanddeck.sdPlugin -o dist --force >/dev/null
mv dist/io.github.alexander566.commanddeck.streamDeckPlugin dist/CommandDeck.streamDeckPlugin
ls -l dist
