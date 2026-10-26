#!/system/bin/sh
MODDIR=${MODDIR:-${0%/scripts/*}}
export MODDIR
. "$MODDIR/scripts/common.sh"
init_state || exit 1
LOCK="$STATE/manage.lock"
locked=0
for i in $("$BB" seq 1 100); do
 if "$BB" mkdir "$LOCK" 2>/dev/null; then echo $$ > "$LOCK/owner"; locked=1; break; fi
 owner=$(pid_from "$LOCK/owner") || {
  # A new owner may still be publishing its PID; do not steal a fresh lock.
  now=$("$BB" date +%s)
  created=$("$BB" stat -c %Y "$LOCK" 2>/dev/null)
  [ -n "$created" ] && [ "$((now-created))" -gt 5 ] && { "$BB" rm -f "$LOCK/owner"; "$BB" rmdir "$LOCK" 2>/dev/null; }
 }
 "$BB" sleep 0.2
done
[ "$locked" = 1 ] || { echo 'Another control operation is still running.' >&2; exit 1; }
trap '"$BB" rm -f "$LOCK/owner"; "$BB" rmdir "$LOCK" 2>/dev/null' EXIT
stop_service() {
 "$BB" touch "$STATE/stop-request"
 p=$(supervisor_pid) && kill -TERM "$p"
 p=$(daemon_pid) && kill -TERM "$p"
 for i in $("$BB" seq 1 20); do
  daemon_pid >/dev/null && { "$BB" sleep 1; continue; }
  supervisor_pid >/dev/null && { "$BB" sleep 1; continue; }
  "$BB" rm -f "$RUN/daemon.pid" "$RUN/supervisor.pid"
  /system/bin/sh "$MODDIR/scripts/google-network.sh" restore || return 1
  log 'stopped'
  echo stopped
  return 0
 done
 echo 'Graceful shutdown is still pending; no unrelated process was signalled.' >&2
 return 1
}
start_service() {
 [ ! -e "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ] || { echo 'Module is disabled or pending removal.' >&2; return 1; }
 if daemon_pid >/dev/null; then panel_ready; return $?; fi
 # Never remove another dae topology or take over an active transparent engine.
 for name in sing-box mihomo dae daed daed8; do
  [ -z "$("$BB" pidof "$name" 2>/dev/null)" ] || { echo "Conflicting process: $name" > "$STATE/last-error"; "$BB" cat "$STATE/last-error" >&2; return 1; }
 done
 /system/bin/ip link show dae0 >/dev/null 2>&1 && { echo 'Existing dae0 is not owned by this module.' >&2; return 1; }
 "$DAEMON" validate -c "$CONFIG" --json || return 1
 "$BB" rm -f "$STATE/stop-request" "$STATE/last-error"
 "$BB" setsid /system/bin/sh "$MODDIR/scripts/supervise.sh" >> "$STATE/logs/service.log" 2>&1 < /dev/null &
 for i in $("$BB" seq 1 40); do
  if daemon_pid >/dev/null && panel_ready; then echo 'Dashboard ready'; return 0; fi
  [ -f "$STATE/last-error" ] && { "$BB" cat "$STATE/last-error" >&2; return 1; }
  if daemon_pid >/dev/null && ready 1s 2>/dev/null; then return 0; fi
  "$BB" sleep 1
 done
 echo 'Service startup did not become ready.' > "$STATE/last-error"
 stop_service >/dev/null
 return 1
}
case "$1" in
 start) start_service ;;
 stop) stop_service && "$BB" rm -f "$STATE/last-error" ;;
 restart) stop_service && start_service ;;
 reload) ready 2s >/dev/null && "$DAEMON" reload --control "$RUN/control.sock" --timeout 30s --json ;;
 google-network)
  case "$2" in on|off) /system/bin/sh "$MODDIR/scripts/google-network.sh" "$2";; *) exit 2;; esac
  ;;
 autostart)
  case "$2" in on) "$BB" rm -f "$STATE/no-autostart";; off) "$BB" touch "$STATE/no-autostart";; *) exit 2;; esac
  "$MODDIR/bin/daenextctl" status
  ;;
 backup)
  # Stop before copying SQLite including its WAL, then restore the daemon.
  was_running=0; daemon_pid >/dev/null && was_running=1
  stop_service || exit 1
  archive="$STATE/backups/daenext-$("$BB" date '+%Y%m%d-%H%M%S').tar.gz"
  set -- config assets certs
  [ ! -f "$STATE/google-network-enabled" ] || set -- "$@" google-network-enabled
  "$BB" tar -czf "$archive" -C "$STATE" "$@"
  result=$?
  [ "$was_running" = 1 ] && start_service >/dev/null
  [ "$result" = 0 ] && echo "$archive"
  exit "$result"
  ;;
esac
