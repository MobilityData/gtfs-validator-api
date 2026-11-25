#!/usr/bin/env bash
# Prints the newest SNAPSHOT version of the GTFS validator core published to the Maven Central
# snapshot repository, e.g. 8.0.2-SNAPSHOT.
set -euo pipefail

url="https://central.sonatype.com/repository/maven-snapshots/org/mobilitydata/gtfs-validator/gtfs-validator-main/maven-metadata.xml"
version=$(curl -fsSL "$url" | grep -o '<version>[^<]*-SNAPSHOT</version>' | sed -E 's#</?version>##g' | sort -V | tail -1)
if [ -z "$version" ]; then
  echo "No GTFS validator SNAPSHOT found at $url" >&2
  exit 1
fi
echo "$version"
