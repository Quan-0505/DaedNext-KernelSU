# DaedNext-KernelSU

ARM64 Android 上的 DaeNext 原生透明代理模块，提供 KernelSU WebUI、完整 DaedNext 管理面板、开机服务、持久化配置和启停/备份命令。

这是 Android 测试版本。核心基于 DaeNext `work/boringssl` 的 `a87b3416e9ecd746cc47fba7b77eec11b08d3cf3`；完整 Android 补丁见 `android/patches/android-complete.patch`。面板源码版本见 `panel-commit.txt`。

## 安装与使用

1. 在 KernelSU 管理器中安装 Release 提供的 ARM64 ZIP。
2. 打开模块 WebUI，点击「启动服务」。不支持热安装的 KernelSU 版本需要先重启。
3. 进入管理面板 `http://127.0.0.1:2023/`，创建自己的管理账户，再添加节点、订阅和分流规则。
4. 在面板中启动内核。初始配置使用 `wan_interface: auto`、不配置 LAN，默认直连。

模块不会携带测试账户或任何订阅。现有 NetProxy 等透明代理核心必须先停止，模块会拒绝与正在运行的同类核心同时接管网络。

## Android App

ZIP 内的 `manager/DaedNext-Manager.apk` 是配套原生管理器，需手动安装并在当前 Root 管理器中授予 Root 权限。App 直接管理现有模块，不导入或重置账户、节点、订阅和 Wi-Fi 名单。当前提供首页／资源／智联／设置四项导航，配置、DNS、路由、群组、节点和订阅均有原生资源页面。

alpha9 统一三种 Root 管理器的安装与服务接口，并固定仅用 eBPF；Magisk、APatch 尚缺真机验收，不应视作已验证支持。详情见 [设备适配接口](../docs/DEVICE-ADAPTER.md)。

App 的代理开关控制内核启用意图；设置中的服务开关控制整个后台服务。短期会话签发仅允许本地 Root socket 调用，不向 HTTP 开放。模块不会自动安装或替换手机上的 App。

## 数据与命令

- `/data/adb/daenext/config/`：数据库、面板配置、产品日志。
- `/data/adb/daenext/assets/`：可更新的 geodata。
- `/data/adb/daenext/certs/`：从 Android 系统信任库生成的 CA 证书包。
- `/data/adb/daenext/logs/`：启动与服务日志。
- `/data/adb/daenext/backups/`：备份。
- `/data/adb/daenext/wifi-policy.json`：Wi-Fi 自动切换设置，权限 0600，包含在模块备份中。
- `/data/adb/daenext/no-autostart`：存在时关闭开机启动。

Root shell：

```sh
CTL=/data/adb/modules/daenext/bin/daenextctl
$CTL start
$CTL status
$CTL stop
$CTL restart
$CTL reload
$CTL logs
$CTL diagnose
$CTL backup
$CTL autostart off
$CTL autostart on
$CTL google-network on
$CTL google-network off
$CTL wifi-policy get
```

`status` 分别报告服务进程和 `readiness.runtimeRunning`，服务面板就绪不代表透明代理内核已启动。备份会短暂停止服务，以一致地保存 SQLite/WAL，再恢复此前的运行状态。

## Wi-Fi 自动切换（alpha6）

在模块 WebUI 启用“Wi-Fi 自动切换”，选择黑名单或白名单，按每行一个 SSID 填写并保存。新安装默认关闭，黑白名单分别保存，不预置个人网络名称。

| 模式 | 名单内 Wi-Fi | 名单外 Wi-Fi |
| --- | --- | --- |
| 黑名单 | 手机直连 | 原有代理规则 |
| 白名单 | 原有代理规则 | 手机直连 |

“非 Wi-Fi 网络使用代理”独立控制移动数据等出口，默认开启。名单精确匹配、区分大小写。路由器是否具有代理由用户判断，本模块不自动检测。

策略直连时保留管理面板，释放本模块的数据面；状态为 `networkPolicyPaused: true`。离开直连网络后按原启用意图恢复，手动停止内核后不会因切网自动启动。节点、订阅及分流配置不变。在直连期间启动内核或应用配置，会记录启用意图，待网络策略允许后接管。

升级保留数据，已有安装会先备份和停止旧进程。卸载会停止服务并保留 `/data/adb/daenext`，便于恢复配置。模块不修改系统分区，不需要 metamodule。

## 核心修复

- Android/bionic 编译、运行目录、cgroup 子树、toybox netns、Android 默认路由探测等此前 9 项兼容修复。
- 把 `ARPHRD_RAWIP=519` 正确识别为 L3。
- 无 LAN 时在挂载 WAN 程序前初始化完整 routing/LPM/connectivity 数据；初始化失败时阻止启动。
- Android 用户态和 eBPF 使用 `0x01000000` 控制标记，避开网络 ID 和已实测会阻断蜂窝 UDP 的旧 `0x40000000` 标记；旧默认值会自动迁移。
- 重建网络命名空间时选择未占用编号，避免网络切换后旧命名空间仍保留而导致固定编号冲突。
- 蜂窝 QoS 覆盖数据包标记后，通过原始 socket 的控制标记识别自身连接；不修改厂商 QoS 或防火墙规则。
- Android 网络监视器使用当前策略路由，避免把 main 表空缺、dummy0 和备用 IPv6 路由当成活动出口。
- 从 Conscrypt/APEX 系统证书目录生成 CA bundle，保持节点 TLS 的严格证书校验。
- 订阅和 geodata 辅助进程在 Android 使用同一高位控制标记；控制层网络 socket 的 SO_MARK 实现支持 Android。

迁移到手机的规则若使用代理作为 fallback，应确保 DNS 上游可直接引导解析，避免节点域名解析依赖尚无可用节点的策略组。本次迁移在路由首部加入 `dip(8.8.8.8/32, 8.8.4.4/32, 223.5.5.5/32) && dport(53) -> direct`。手机仍保留自动 WAN、不配置 LAN；参考机的网卡名不能直接照搬。

TCP 底层原有代码已调用 `SO_MARK`，本项目未重复加入同一设置。厂商内核可能拒绝 cgroup attach，当前一加 13 采用不含 `pname()` 的配置。

## 构建

Linux 构建机需要 Rust stable/nightly、rust-src、bpf-linker、clang/LLVM、Android NDK r27c、CMake、Perl 和 pkg-config。

仓库根目录是已包含 Android 修改的完整 Rust 源码。`android/tools/build-android.sh` 提供可移植的 NDK 构建入口；`android/tools/build-panel.sh` 构建上游面板。两者的机器路径应按本机构建环境调整。

本地打包：

```powershell
python android/tools/seed-defaults.py
python android/tools/package.py
```

运行打包前，须把重编的 ARM64 `daed` 放到 `module/bin/daed`，`libc++_shared.so` 放到 `module/lib/`，上游面板放到 `module/panel/`。打包脚本验证 LF 换行、必需文件及默认数据库没有用户、节点、订阅，并生成文件清单和 ZIP 的 SHA256。

本次实际验证记录将在 `VALIDATION.md` 中列出。未实测的内核、设备、协议和启动场景不视为已支持。

## Google 服务联网兼容（alpha4）

模块 WebUI 提供「Google 服务联网兼容」开关，发布包默认关闭。本机 ColorOS 在网络切换时把 Play 商店和 GMS/GSF 共享 UID 设置为禁止联网，TCX 接管流量前连接已经被拒绝。启用此选项后，在内核运行或用户仍启用内核的 Wi-Fi 策略直连期间，通过系统流量管理接口维护这两个应用的联网权限，每约 6 秒检查一次；关闭、手动停止内核、停止服务或卸载时恢复记录的原策略。其他应用、Google 账户和商店数据不变。

目前仅支持 Android SDK 37 且 oplus-framework.jar SHA256 为 d6da8dd5cb56b780a98e1ab26c7284a974295eee0b8ad0107765c4949a62a36f 的已验证系统。未知系统不会收到猜测的 Binder 写入。选项保存在 /data/adb/daenext/google-network-enabled，原策略记录在 google-network-original；恢复失败时保留记录，便于重试。策略由系统流量管理应用使用的 UID 1000 调用；不添加 iptables/BPF 放行规则。
