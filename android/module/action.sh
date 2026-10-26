#!/system/bin/sh
MODDIR=${0%/*}
"$MODDIR/bin/daenextctl" start && /system/bin/am start -a android.intent.action.VIEW -d http://127.0.0.1:2023/
