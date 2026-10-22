#!/bin/bash
# pack-forge-mojmap.sh <neoforge era> <label> <mc range> <java release>
# Forge 1.20.6+ runs on Mojang names like NeoForge, so its jar reuses the NeoForge build's classes
# with ForgeEntry and META-INF/mods.toml instead of the NeoForge entry and metadata.
set -e
cd "$(dirname "$0")"
src=eras/$1/build/classes/java/main
out=eras/forge-$2/build
ver=$(grep '^mod_version=' eras/$1/gradle.properties | cut -d= -f2)
rm -rf $out && mkdir -p $out/classes $out/stub $out/libs
cp -r $src/. $out/classes/
rm -f $out/classes/io/github/alexander566/commanddeck/NeoForgeEntry.class
javac -nowarn --release 8 -d $out/stub loader/forge/stub/net/minecraftforge/fml/common/Mod.java
javac -nowarn --release $4 -cp $out/classes:$out/stub -d $out/classes loader/forge/java/io/github/alexander566/commanddeck/ForgeEntry.java
mkdir -p $out/classes/META-INF $out/classes/assets/commanddeck
cp common/resources/assets/commanddeck/icon.png $out/classes/assets/commanddeck/
sed -e "s/\${version}/$ver+forge-mc$2/" -e "s/\${mc_range}/$3/" loader/forge/resources/META-INF/mods.toml > $out/classes/META-INF/mods.toml
cp ../LICENSE $out/classes/
(cd $out/classes && jar cf ../libs/commanddeck-$ver+forge-mc$2.jar .)
ls -l $out/libs
