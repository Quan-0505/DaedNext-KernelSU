#!/system/bin/sh
[ "$ARCH" = arm64 ] || abort 'DaeNext requires ARM64 Android.'
[ "$API" -ge 24 ] || abort 'Android 7 or newer is required.'
MODDIR=$MODPATH
. "$MODPATH/scripts/root-env.sh" || abort 'Root manager BusyBox was not found.'
[ "$ROOT_MANAGER" != unknown ] || abort 'Install through KernelSU, Magisk or APatch Manager.'
(cd "$MODPATH" && "$BB" sha256sum -c SHA256SUMS >/dev/null) || abort 'Module integrity check failed.'
# Persist only the validated manager name, not installer environment or credentials.
printf '%s\n' "$ROOT_MANAGER" > "$MODPATH/root-manager"
if [ -x /data/adb/modules/daenext/bin/daenextctl ]; then
 ui_print '- Backing up existing data and stopping the previous version.'
 /data/adb/modules/daenext/bin/daenextctl backup || abort 'Existing data backup failed.'
 /data/adb/modules/daenext/bin/daenextctl stop || abort 'Existing daemon did not stop.'
fi
ui_print "- DaedNext Android / $ROOT_MANAGER / eBPF only"
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
[ "$ROOT_MANAGER" != kernelsu ] || export MODULE_HOT_INSTALL_REQUEST=true
