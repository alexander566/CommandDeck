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
(cd streamdeck && python3 -c "
import zipfile, os
root = 'io.github.alexander566.commanddeck.sdPlugin'
with zipfile.ZipFile('../dist/CommandDeck.streamDeckPlugin', 'w', zipfile.ZIP_DEFLATED) as z:
    for dp, dn, fn in os.walk(root):
        for f in fn:
            z.write(os.path.join(dp, f))
")
ls -l dist
