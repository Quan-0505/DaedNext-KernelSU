#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "$0")/../.." && pwd)
PANEL="$ROOT/android/.build/panel-source"
readarray -t source < <(python3 -c 'import json,sys; p=json.load(open(sys.argv[1]))["panel"]; print(p["repository"]); print(p["commit"])' "$ROOT/android/upstream.lock.json")
if [ ! -d "$PANEL" ]; then
 mkdir -p "$(dirname "$PANEL")"
 git clone --no-checkout "${source[0]}" "$PANEL"
 git -C "$PANEL" checkout --detach "${source[1]}"
fi
[ "$(git -C "$PANEL" rev-parse HEAD)" = "${source[1]}" ] || { echo 'Panel checkout differs from lock file; use a new build directory.' >&2; exit 1; }
export CI=true TURBO_TELEMETRY_DISABLED=1
cd "$PANEL"
pnpm install --frozen-lockfile
pnpm build
python3 - "$PANEL/apps/web/dist" "$ROOT/android/module/panel" <<'COPY'
from pathlib import Path
import shutil,sys
source,target=map(Path,sys.argv[1:])
assert source.joinpath('index.html').is_file()
assert target.parent.name=='module' and target.name=='panel' and not target.is_symlink()
if target.exists(): shutil.rmtree(target)
shutil.copytree(source,target)
COPY
