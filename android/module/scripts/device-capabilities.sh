#!/system/bin/sh
# Read-only observations. Presence is not proof of BPF attach or working traffic.
MODDIR=${MODDIR:-${0%/scripts/*}}
. "$MODDIR/scripts/root-env.sh" || exit 1
present() { if [ -e "$1" ]; then printf true; else printf false; fi; }
readable() { if [ -r "$1" ]; then printf true; else printf false; fi; }
safe_token() { "$BB" tr -cd 'a-zA-Z0-9._+-'; }
sdk=$(/system/bin/getprop ro.build.version.sdk | safe_token)
case "$sdk" in ''|*[!0-9]*) sdk=null ;; esac
abi=$(/system/bin/getprop ro.product.cpu.abi | safe_token)
kernel=$(/system/bin/uname -r | safe_token)
selinux=$(/system/bin/getenforce 2>/dev/null | safe_token)
printf '{"schemaVersion":1,"readOnly":true,"android":{"sdk":%s,"abi":"%s"},"kernel":{"release":"%s","btfReadable":%s,"cgroupV2Visible":%s,"networkNamespaceVisible":%s,"tunDeviceVisible":%s},"root":{"manager":"%s","uid":%s,"kernelSUBusyBox":%s,"magiskBusyBox":%s,"apatchBusyBox":%s},"selinux":"%s","validation":{"bpfLoad":"not_tested","tcxAttach":"not_tested","cgroupAttach":"not_tested","namespaceCreate":"not_tested","traffic":"not_tested"},"policy":{"ebpfOnly":true,"requestedAttachBackend":"auto","nonEbpfFallback":false},"backends":{"ebpf":"requires_runtime_validation","tun":"disabled_by_policy"},"compatibility":"unverified"}\n' \
 "$sdk" "$abi" "$kernel" "$(readable /sys/kernel/btf/vmlinux)" \
 "$(present /sys/fs/cgroup/cgroup.controllers)" "$(present /proc/self/ns/net)" \
 "$(present /dev/net/tun)" "$ROOT_MANAGER" "$(/system/bin/id -u)" \
 "$(present /data/adb/ksu/bin/busybox)" "$(present /data/adb/magisk/busybox)" \
 "$(present /data/adb/ap/bin/busybox)" "$selinux"
