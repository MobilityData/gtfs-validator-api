#!/usr/bin/env bash
# Prints the MobilityData/gtfs-validator git commit (full SHA) that a validator core version was
# built from:
# - a release (e.g. 8.0.1): the commit of its `v8.0.1` tag.
# - a SNAPSHOT (e.g. 8.0.2-SNAPSHOT): the commit of the newest successful "Publish Jars
#   Snapshots" run on master, which uploaded the newest SNAPSHOT build. The SNAPSHOT version
#   string stays the same across uploads, while this commit changes with each one.
# Uses the GitHub CLI: in GitHub Actions, set GH_TOKEN (e.g. to github.token).
# Usage: scripts/validator-commit.sh <validator version>
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <validator version>" >&2
  exit 1
fi

repo="MobilityData/gtfs-validator"
if [[ "$1" == *-SNAPSHOT ]]; then
  commit=$(gh api "repos/${repo}/actions/workflows/publish_snapshots.yml/runs?branch=master&status=success&per_page=1" \
    --jq '.workflow_runs[0].head_sha // empty')
else
  commit=$(gh api "repos/${repo}/commits/v$1" --jq '.sha // empty')
fi
if [ -z "$commit" ]; then
  echo "No ${repo} commit found for version $1" >&2
  exit 1
fi
echo "$commit"
