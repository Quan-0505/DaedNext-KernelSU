#!/system/bin/sh
# Opt-in ColorOS compatibility. Only the two named Google package UIDs are managed.
MODDIR=${MODDIR:-${0%/scripts/*}}
export MODDIR
. "$MODDIR/scripts/common.sh"
FLAG="$STATE/google-network-enabled"
JOURNAL="$STATE/google-network-original"
ABI_CACHE="$RUN/google-network-abi"
EXPECTED=d6da8dd5cb56b780a98e1ab26c7284a974295eee0b8ad0107765c4949a62a36f

supported() {
 boot=$(cat /proc/sys/kernel/random/boot_id)
 [ "$("$BB" cat "$ABI_CACHE" 2>/dev/null)" = "$boot $EXPECTED" ] && return 0
 # Binder ordinals/constants were read from this exact device framework.
 # Unknown OS builds must not receive guessed administrative transactions.
 [ "$(getprop ro.build.version.sdk)" = 37 ] || return 1
 actual=$("$BB" sha256sum /system/framework/oplus-framework.jar 2>/dev/null)
 [ "${actual%% *}" = "$EXPECTED" ] || return 1
 printf '%s %s\n' "$boot" "$EXPECTED" > "$ABI_CACHE"
}
package_uid() {
 cmd package list packages -U --user 0 "$1" </dev/null 2>/dev/null |
  "$BB" awk -v pkg="package:$1" '$1==pkg {sub(/^uid:/,"",$2); print $2; exit}'
}
valid_uid() {
 case "$1" in ''|*[!0-9]*) return 1;; esac
 [ "$1" -ge 10000 ] && [ "$1" -lt 100000 ]
}
get_policy() {
 answer=$(service call networking_control 2 i32 "$1" </dev/null 2>/dev/null)
 hex=$(printf '%s\n' "$answer" | "$BB" sed -n 's/.*00000000[[:space:]]\{1,\}\([0-9a-fA-F]\{8\}\).*/\1/p')
 case "$hex" in
  00000000) echo 0;; 00000001) echo 1;; 00000002) echo 2;;
  00000004) echo 4;; *) return 1;;
 esac
}
set_policy() {
 valid_uid "$1" || return 1
 case "$2" in 0|4) ;; *) return 1;; esac
 # TrafficMonitor itself runs as system UID 1000; root UID has no whitelisted package.
 su 1000 -c "service call networking_control 1 i32 $1 i32 $2" </dev/null >/dev/null 2>&1
 [ "$(get_policy "$1")" = "$2" ]
}
restore_locked() {
 [ -s "$JOURNAL" ] || return 0
 supported || { log 'Google network restore skipped: unsupported framework'; return 1; }
 remaining="$JOURNAL.pending.$$"
 : > "$remaining"
 while read -r pkg uid original; do
  case "$pkg" in com.android.vending|com.google.android.gms) ;; *) continue;; esac
  valid_uid "$uid" && [ "$original" = 4 ] || continue
  # Do not change a UID reused after package removal/reinstallation.
  actual_uid=$(package_uid "$pkg")
  [ -n "$actual_uid" ] || { echo "$pkg $uid $original" >> "$remaining"; continue; }
  [ "$actual_uid" = "$uid" ] || continue
  current=$(get_policy "$uid") || { echo "$pkg $uid $original" >> "$remaining"; continue; }
  if [ "$current" = 0 ]; then
   if set_policy "$uid" "$original"; then log "Google network policy restored package=$pkg uid=$uid"
   else echo "$pkg $uid $original" >> "$remaining"; fi
  fi
  # A different current value represents an external/user policy change; preserve it.
 done < "$JOURNAL"
 if [ -s "$remaining" ]; then "$BB" mv -f "$remaining" "$JOURNAL"; return 1; fi
 "$BB" rm -f "$remaining" "$JOURNAL"
}
sync_locked() {
 [ -f "$FLAG" ] || { restore_locked; return $?; }
 supported || return 1
 report=$(ready 1s 2>/dev/null)
 active=0
 printf '%s' "$report" | "$BB" grep -q '"runtimeRunning":true' && active=1
 # A Wi-Fi bypass is still an enabled user session. Keep the opted-in Google
 # networking permission on managed Wi-Fi; a manual stop still restores it.
 if printf '%s' "$report" | "$BB" grep -q '"networkPolicyPaused":true' &&
    printf '%s' "$report" | "$BB" grep -q '"runtimeRequired":true'; then active=1; fi
 if [ "$active" = 0 ]; then restore_locked; return $?; fi
 for pkg in com.android.vending com.google.android.gms; do
  uid=$(package_uid "$pkg")
  valid_uid "$uid" || continue
  current=$(get_policy "$uid") || continue
  [ "$current" = 4 ] || continue
  if ! "$BB" grep -Fxq "$pkg $uid 4" "$JOURNAL" 2>/dev/null; then
   printf '%s %s 4\n' "$pkg" "$uid" >> "$JOURNAL" || return 1
   "$BB" chmod 600 "$JOURNAL"
  fi
  set_policy "$uid" 0 || return 1
  log "Google network compatibility applied package=$pkg uid=$uid"
 done
}
locked() (
 "$BB" flock -n 9 || exit 1
 "$@"
) 9> "$STATE/google-network.lock"

case "${1:-status}" in
 on)
  supported || { echo 'Google network compatibility is not supported on this OS build.' >&2; exit 1; }
  "$BB" touch "$FLAG"
  locked sync_locked
  ;;
 off) "$BB" rm -f "$FLAG"; locked restore_locked ;;
 sync) locked sync_locked ;;
 restore) locked restore_locked ;;
 status)
  enabled=false; [ -f "$FLAG" ] && enabled=true
  managed=false; [ -s "$JOURNAL" ] && managed=true
  printf '{"enabled":%s,"managed":%s}\n' "$enabled" "$managed"
  ;;
 *) echo 'Usage: google-network.sh {on|off|sync|restore|status}' >&2; exit 2;;
esac
