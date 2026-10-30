#!/system/bin/sh
# Read-only observations. Presence is not proof of BPF attach or working traffic.
BB=
for candidate in /data/adb/ksu/bin/busybox /data/adb/magisk/busybox /data/adb/ap/bin/busybox; do
 [ -x "$candidate" ] && { BB=$candidate; break; }
done
[ -n "$BB" ] || { echo 'A supported BusyBox binary was not found.' >&2; exit 1; }
present() { if [ -e "$1" ]; then printf true; else printf false; fi; }
readable() { if [ -r "$1" ]; then printf true; else printf false; fi; }
safe_token() { "$BB" tr -cd 'a-zA-Z0-9._+-'; }
sdk=$(/system/bin/getprop ro.build.version.sdk | safe_token)
case "$sdk" in ''|*[!0-9]*) sdk=null ;; esac
abi=$(/system/bin/getprop ro.product.cpu.abi | safe_token)
kernel=$(/system/bin/uname -r | safe_token)
selinux=$(/system/bin/getenforce 2>/dev/null | safe_token)
printf '{"schemaVersion":1,"readOnly":true,"android":{"sdk":%s,"abi":"%s"},"kernel":{"release":"%s","btfReadable":%s,"cgroupV2Visible":%s,"networkNamespaceVisible":%s,"tunDeviceVisible":%s},"root":{"uid":%s,"kernelSUBusyBox":%s,"magiskBusyBox":%s,"apatchBusyBox":%s},"selinux":"%s","validation":{"bpfLoad":"not_tested","tcxAttach":"not_tested","cgroupAttach":"not_tested","namespaceCreate":"not_tested","traffic":"not_tested"},"backends":{"ebpf":"requires_runtime_validation","tun":"not_implemented"},"compatibility":"unverified"}\n' \
 "$sdk" "$abi" "$kernel" "$(readable /sys/kernel/btf/vmlinux)" \
 "$(present /sys/fs/cgroup/cgroup.controllers)" "$(present /proc/self/ns/net)" \
 "$(present /dev/net/tun)" "$(/system/bin/id -u)" \
 "$(present /data/adb/ksu/bin/busybox)" "$(present /data/adb/magisk/busybox)" \
 "$(present /data/adb/ap/bin/busybox)" "$selinux"
