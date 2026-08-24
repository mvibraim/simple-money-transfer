#!/bin/bash
# Stop hook: enforce Spring Java Format once per turn, only when a .java file
# actually changed. Runs `./gradlew checkFormat` (not `format`) so it never
# rewrites files out from under Claude mid-turn — it only reports.
set -euo pipefail

input=$(cat)
cd "$(jq -r '.cwd' <<<"$input")"

if [[ "$(jq -r '.stop_hook_active' <<<"$input")" == "true" ]]; then
  exit 0
fi

if git diff --name-only HEAD 2>/dev/null | grep -q '\.java$'; then
  : # fall through to the format check below
elif git status --porcelain 2>/dev/null | grep -q '\.java$'; then
  : # untracked or newly added .java files also need checking
else
  exit 0
fi

if ./gradlew checkFormat --offline --console=plain -q > /tmp/claude-checkformat.log 2>&1; then
  exit 0
fi

jq -n \
  --arg reason "Spring Java Format violations found. Run ./gradlew format to fix — don't hand-format or fight the tool's output." \
  '{decision: "continue", reason: $reason}'
