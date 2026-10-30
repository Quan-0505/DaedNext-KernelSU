#!/system/bin/sh
. "$MODDIR/scripts/root-env.sh" || return 1
STATE=${DAENEXT_STATE_DIR:-/data/adb/daenext}
CONFIG="$STATE/config"
RUN="$STATE/run"
DAEMON="$MODDIR/bin/daed"
export LD_LIBRARY_PATH="$MODDIR/lib"
export DAE_LOCATION_ASSET="$STATE/assets"
export DAE_RUNTIME_DIR="$RUN"
export DAE_NETWORK_POLICY_STATE="$RUN/wifi-policy-state.json"
export DAE_ALLOW_EMPTY_LAN=1
export NETNS_LINK=veth
# Only native eBPF is admitted. Auto selects an eBPF attach mechanism, never TUN.
export RESIDENT_NATIVE_EBPF=1
export RESIDENT_DATAPLANE=1
export RESIDENT_NATIVE_EBPF_BACKEND=auto
export SSL_CERT_FILE="$STATE/certs/ca-certificates.crt"
export PATH="$MODDIR/bin:/system/bin:/system/xbin:/vendor/bin"
if [ -d /sys/fs/cgroup/apps ]; then
 export DAE_CGROUP2_ROOT=/sys/fs/cgroup/apps
fi
umask 077
init_state() {
 "$BB" mkdir -p "$CONFIG" "$RUN" "$STATE/logs" "$STATE/backups" "$STATE/assets" "$STATE/certs"
 "$BB" chmod 700 "$STATE" "$CONFIG" "$RUN" "$STATE/backups"
 [ -f "$CONFIG/daed.db" ] || "$BB" cp "$MODDIR/defaults/daed.db" "$CONFIG/daed.db"
 [ -f "$STATE/wifi-policy.json" ] || "$BB" cp "$MODDIR/defaults/wifi-policy.json" "$STATE/wifi-policy.json"
 "$BB" chmod 600 "$STATE/wifi-policy.json"
 [ -f "$CONFIG/config.dae" ] || "$BB" cp "$MODDIR/defaults/config.dae" "$CONFIG/config.dae"
 for asset in geoip.dat geosite.dat; do
  [ -f "$STATE/assets/$asset" ] || "$BB" cp "$MODDIR/assets/$asset" "$STATE/assets/$asset"
 done
 "$BB" chmod 600 "$CONFIG/daed.db" "$CONFIG/config.dae"
 # Android ships hashed PEM files rather than a Linux-style combined bundle.
 # Preserve certificate verification using the device's system trust store.
 ca_dir=
 for candidate in /apex/com.android.conscrypt/cacerts /system/etc/security/cacerts; do
  [ -d "$candidate" ] && { ca_dir=$candidate; break; }
 done
 [ -n "$ca_dir" ] || { echo 'Android system CA store was not found.' >&2; return 1; }
 ca_tmp="$STATE/certs/ca-bundle.$$"
 "$BB" cat "$ca_dir"/* > "$ca_tmp" || { "$BB" rm -f "$ca_tmp"; return 1; }
 "$BB" grep -q 'BEGIN CERTIFICATE' "$ca_tmp" || { "$BB" rm -f "$ca_tmp"; return 1; }
 "$BB" chmod 600 "$ca_tmp"
 "$BB" mv -f "$ca_tmp" "$SSL_CERT_FILE"
}
pid_from() {
 [ -r "$1" ] || return 1
 read -r found_pid < "$1"
 case "$found_pid" in ''|*[!0-9]*) return 1;; esac
 [ "$found_pid" -gt 1 ] && kill -0 "$found_pid" 2>/dev/null || return 1
 echo "$found_pid"
}
daemon_pid() {
 p=$(pid_from "$RUN/daemon.pid") || return 1
 [ "$("$BB" readlink /proc/$p/exe)" = "$DAEMON" ] || return 1
 # Verify the command belongs to this state directory before signalling it.
 "$BB" tr '\000' '\n' < "/proc/$p/cmdline" | "$BB" grep -Fxq "$CONFIG" || return 1
 echo "$p"
}
supervisor_pid() {
 p=$(pid_from "$RUN/supervisor.pid") || return 1
 "$BB" tr '\000' '\n' < "/proc/$p/cmdline" | "$BB" grep -Fxq "$MODDIR/scripts/supervise.sh" || return 1
 echo "$p"
}
log() { echo "$("$BB" date '+%Y-%m-%dT%H:%M:%S%z') $*" >> "$STATE/logs/service.log"; }
ready() { "$DAEMON" wait-ready --control "$RUN/control.sock" --timeout "${1:-2s}" --json; }
panel_ready() { "$BB" wget -q -T 2 -O /dev/null http://127.0.0.1:2023/ 2>/dev/null; }
