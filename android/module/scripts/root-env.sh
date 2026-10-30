#!/system/bin/sh
# Shared installer/runtime contract. Never source a persisted marker as shell code.
detect_root_manager() {
 ROOT_MANAGER=unknown
 if [ "${APATCH:-}" = true ]; then ROOT_MANAGER=apatch
 elif [ "${KSU:-}" = true ]; then ROOT_MANAGER=kernelsu
 elif [ -n "${MAGISK_VER_CODE:-}" ]; then ROOT_MANAGER=magisk
 elif [ -r "${MODDIR:-}/root-manager" ]; then
  read -r ROOT_MANAGER < "$MODDIR/root-manager"
 fi
 case "$ROOT_MANAGER" in kernelsu|magisk|apatch) ;; *) ROOT_MANAGER=unknown ;; esac
}
select_root_busybox() {
 BB=
 case "$ROOT_MANAGER" in
  kernelsu) candidates=/data/adb/ksu/bin/busybox ;;
  magisk) candidates=/data/adb/magisk/busybox ;;
  apatch) candidates=/data/adb/ap/bin/busybox ;;
  # Old releases have no marker. A binary path alone does not identify the manager.
  *) candidates='/data/adb/ksu/bin/busybox /data/adb/magisk/busybox /data/adb/ap/bin/busybox' ;;
 esac
 for candidate in $candidates; do
  [ -x "$candidate" ] && "$candidate" true 2>/dev/null && { BB=$candidate; break; }
 done
 [ -n "$BB" ] || { echo "BusyBox unavailable for Root manager: $ROOT_MANAGER" >&2; return 1; }
 export ROOT_MANAGER BB
}
detect_root_manager
select_root_busybox
