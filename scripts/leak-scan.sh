#!/usr/bin/env bash
# Fail if any file under DIR matches a pattern in publish/leak-patterns.txt. See leak-scan.mjs.
set -euo pipefail
dir=${1:?usage: scripts/leak-scan.sh <dir>}
exec node "$(git rev-parse --show-toplevel)/scripts/leak-scan.mjs" "$dir"
