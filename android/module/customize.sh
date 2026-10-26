#!/system/bin/sh
[ "$ARCH" = arm64 ] || abort 'DaeNext requires ARM64 Android.'
[ "$API" -ge 24 ] || abort 'Android 7 or newer is required.'
[ -x /data/adb/ksu/bin/busybox ] || abort 'KernelSU BusyBox was not found.'
(cd "$MODPATH" && /data/adb/ksu/bin/busybox sha256sum -c SHA256SUMS >/dev/null) || abort 'Module integrity check failed.'
if [ -x /data/adb/modules/daenext/bin/daenextctl ]; then
 ui_print '- Backing up existing data and stopping the previous version.'
 /data/adb/modules/daenext/bin/daenextctl backup || abort 'Existing data backup failed.'
 /data/adb/modules/daenext/bin/daenextctl stop || abort 'Existing daemon did not stop.'
fi
ui_print '- DaeNext Android / KernelSU'
ui_print '- Data stays in /data/adb/daenext across updates.'
ui_print '- First launch: create your dashboard account and add nodes.'
ui_print '- Dashboard: http://127.0.0.1:2023'
set_perm_recursive "$MODPATH/bin" 0 0 0755 0755
set_perm_recursive "$MODPATH/scripts" 0 0 0755 0755
set_perm "$MODPATH/customize.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/lib/libc++_shared.so" 0 0 0644
set_perm "$MODPATH/defaults/daed.db" 0 0 0600
touch "$MODPATH/skip_mount"
# KernelSU versions without hot install safely keep the standard reboot staging.
export MODULE_HOT_INSTALL_REQUEST=true
