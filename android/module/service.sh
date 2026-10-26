#!/system/bin/sh
MODDIR=${0%/*}
# Late-start service does not hold up Android boot.
for i in $(seq 1 90); do
 [ "$(/system/bin/getprop sys.boot_completed)" = 1 ] && break
 sleep 2
done
[ -e "$MODDIR/disable" ] || [ -e "$MODDIR/remove" ] && exit 0
[ -e /data/adb/daenext/no-autostart ] && exit 0
# Wait for a usable Android policy route; this only reads network state.
for i in $(seq 1 60); do
 /system/bin/ip -4 route get 1.1.1.1 2>/dev/null | grep -q ' dev ' && break
 sleep 2
done
"$MODDIR/bin/daenextctl" start
