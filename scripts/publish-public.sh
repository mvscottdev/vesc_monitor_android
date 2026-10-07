#!/usr/bin/env bash
# Export the public subset of this private repo into a local clone of the public repo
# as ONE squashed commit (no private history). Never pushes.
# One-time setup:
#   git clone <public repo url> <dir> && touch <dir>/.public-mirror
#   git config publish.dir <dir>
#   git config publish.name "<public name>"
#   git config publish.email "<id>+<user>@users.noreply.github.com"
set -euo pipefail
root=$(git rev-parse --show-toplevel)
cd "$root"
msg=${1:?usage: scripts/publish-public.sh "release message"}

dest=$(git config publish.dir) || { echo "missing: git config publish.dir"; exit 1; }
name=$(git config publish.name) || { echo "missing: git config publish.name"; exit 1; }
email=$(git config publish.email) || { echo "missing: git config publish.email"; exit 1; }

# Guards: never wipe the wrong folder, never publish uncommitted work.
[ -f "$dest/.public-mirror" ] && [ -d "$dest/.git" ] || { echo "$dest is not a public mirror clone"; exit 1; }
[ "$(cd "$dest" && pwd -P)" != "$(pwd -P)" ] || { echo "publish.dir points at this repo"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "commit first: only committed files are published"; exit 1; }

paths=()
while read -r p; do
  git cat-file -e "HEAD:$p" 2>/dev/null && paths+=("$p")
done < <(grep -vE '^\s*(#|$)' publish/allowlist.txt)

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
git archive HEAD -- "${paths[@]}" | tar -x -C "$stage"
scripts/leak-scan.sh "$stage"

find "$dest" -mindepth 1 -maxdepth 1 ! -name .git ! -name .public-mirror -exec rm -rf {} +
cp -r "$stage"/. "$dest"/
git -C "$dest" add -A
if git -C "$dest" diff --cached --quiet; then echo "nothing changed"; exit 0; fi
git -C "$dest" -c user.name="$name" -c user.email="$email" commit -q -m "$msg"
echo "Committed in $dest. Review it, then push: git -C \"$dest\" push"
