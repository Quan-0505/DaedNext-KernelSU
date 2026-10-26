#!/system/bin/sh
MODDIR=${0%/*}
"$MODDIR/bin/daenextctl" stop
# Persistent configuration and backups are deliberately retained.
