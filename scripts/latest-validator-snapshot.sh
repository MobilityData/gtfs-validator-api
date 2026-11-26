#!/usr/bin/env bash
# Prints the newest SNAPSHOT version of the GTFS validator core published to the Maven Central
# snapshot repository, e.g. 8.0.2-SNAPSHOT.
set -euo pipefail

url="${SNAPSHOT_METADATA_URL:-https://central.sonatype.com/repository/maven-snapshots/org/mobilitydata/gtfs-validator/gtfs-validator-main/maven-metadata.xml}"

# The snapshot repository is sometimes slow or briefly unavailable: retry, and bound each attempt.
if ! metadata=$(curl -fsSL --retry 3 --retry-all-errors --retry-delay 5 --connect-timeout 10 --max-time 30 "$url"); then
  echo "Could not download the GTFS validator snapshot metadata from $url" >&2
  exit 1
fi

# `|| true`: with pipefail, grep finding nothing would exit here silently, before the error below.
version=$(printf '%s\n' "$metadata" | grep -o '<version>[^<]*-SNAPSHOT</version>' | sed -E 's#</?version>##g' | sort -V | tail -1 || true)
if [ -z "$version" ]; then
  echo "No GTFS validator SNAPSHOT found at $url" >&2
  exit 1
fi
echo "$version"
