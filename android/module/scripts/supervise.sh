#!/system/bin/sh
MODDIR=${MODDIR:-${0%/scripts/*}}
export MODDIR
. "$MODDIR/scripts/common.sh" || exit 1
echo $$ > "$RUN/supervisor.pid"
child=
readiness_pid=
policy_pid=
cleanup() {
 shutdown_policy=$policy_pid
 policy_pid=
 [ -n "$shutdown_policy" ] && kill -TERM "$shutdown_policy" 2>/dev/null
 [ -n "$shutdown_policy" ] && wait "$shutdown_policy" 2>/dev/null
 shutdown_readiness=$readiness_pid
 readiness_pid=
 [ -n "$shutdown_readiness" ] && kill -TERM "$shutdown_readiness" 2>/dev/null
 [ -n "$shutdown_readiness" ] && wait "$shutdown_readiness" 2>/dev/null
 shutdown_pid=$child
 child=
 [ -n "$shutdown_pid" ] && kill -TERM "$shutdown_pid" 2>/dev/null
 [ -n "$shutdown_pid" ] && wait "$shutdown_pid" 2>/dev/null
 /system/bin/sh "$MODDIR/scripts/google-network.sh" restore || log 'Google network restore needs retry'
 "$BB" rm -f "$RUN/supervisor.pid"
 daemon_pid >/dev/null || "$BB" rm -f "$RUN/daemon.pid"
}
trap 'cleanup; exit 0' TERM INT
trap cleanup EXIT
attempt=0
while [ "$attempt" -lt 3 ]; do
 [ -e "$STATE/stop-request" ] || [ -e "$MODDIR/disable" ] || [ -e "$MODDIR/remove" ] && break
 [ -f "$STATE/logs/daemon.log" ] && [ "$("$BB" wc -c < "$STATE/logs/daemon.log")" -gt 1048576 ] && "$BB" mv "$STATE/logs/daemon.log" "$STATE/logs/daemon.previous.log"
 if ! "$DAEMON" wifi-policy init --config "$STATE/wifi-policy.json" --state "$DAE_NETWORK_POLICY_STATE" >/dev/null 2>> "$STATE/logs/service.log"; then
  "$BB" rm -f "$DAE_NETWORK_POLICY_STATE"
  log 'Wi-Fi policy could not be loaded; normal proxy rules retained'
 fi
 log "starting daemon attempt=$attempt"
 "$DAEMON" run -c "$CONFIG" --listen 127.0.0.1:2023 --web-root "$MODDIR/panel" --control "$RUN/control.sock" >> "$STATE/logs/daemon.log" 2>&1 &
 child=$!
 echo "$child" > "$RUN/daemon.pid"
 "$DAEMON" wait-ready --control "$RUN/control.sock" --timeout 25s --json > "$RUN/readiness.json" 2>> "$STATE/logs/service.log" &
 readiness_pid=$!
 startup_ready=0
 wait "$readiness_pid" && startup_ready=1
 readiness_pid=
 if [ "$startup_ready" != 1 ]; then
  if panel_ready; then
   echo 'Runtime is not ready. The dashboard remains available for configuration repair.' > "$STATE/last-error"
  else
   echo 'Daemon readiness failed; inspect daenextctl logs.' > "$STATE/last-error"
   cleanup
   exit 1
  fi
 fi
 compat_tick=0
 while kill -0 "$child" 2>/dev/null; do
  if [ -e "$STATE/stop-request" ] || [ -e "$MODDIR/disable" ] || [ -e "$MODDIR/remove" ]; then
   cleanup
   exit 0
  fi
  "$DAEMON" wifi-policy sync --config "$STATE/wifi-policy.json" --state "$DAE_NETWORK_POLICY_STATE" --control "$RUN/control.sock" >/dev/null 2>> "$STATE/logs/service.log" &
  policy_pid=$!
  wait "$policy_pid" || true
  policy_pid=
  if [ "$compat_tick" = 0 ]; then
   /system/bin/sh "$MODDIR/scripts/google-network.sh" sync || true
  fi
  compat_tick=$(((compat_tick+1)%3))
  "$BB" sleep 2
 done
 wait "$child" 2>/dev/null
 "$BB" rm -f "$RUN/daemon.pid"
 child=
 /system/bin/sh "$MODDIR/scripts/google-network.sh" restore || log 'Google network restore needs retry'
 attempt=$((attempt+1))
 log "daemon exited; retry=$attempt"
 "$BB" sleep "$((attempt*2))"
done
[ "$attempt" -ge 3 ] && echo 'Daemon exited three times; automatic restart stopped.' > "$STATE/last-error"
