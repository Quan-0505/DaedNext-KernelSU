# DaedNext-KernelSU

一加 13 上验证的 ARM64 Android / KernelSU 原生透明代理模块，基于 [ksong008/DaeNext](https://github.com/ksong008/DaeNext) 和 [DaedNext](https://github.com/ksong008/DaedNext)。当前版本 **v0.1.0-alpha4**，属于设备实测的早期版本。

[下载安装包](https://github.com/Quan-0505/DaedNext-KernelSU/releases) · [构建与升级](android/docs/BUILD.md) · [实测范围](android/docs/VALIDATION.md) · [变更记录](android/CHANGELOG.md)

## 安装

1. 在 KernelSU 管理器安装 Release 中的 ARM64 ZIP，然后重启。
2. 打开模块 WebUI 启动服务，进入本机管理面板 `http://127.0.0.1:2023/`。
3. 首次创建自己的管理账户、添加订阅/节点，再在面板中启动内核。初始配置为直连。

安装包不含账户、订阅、节点或设备测试数据。数据保存在 `/data/adb/daenext`，更新与卸载会保留；模块提供备份。其他透明代理核心须先停止。

## Android 功能

- 原生 Rust 核心、上游完整管理面板、KernelSU WebUI、开机恢复及启停/重载/备份。
- 自动 WAN，兼容 Android 策略路由与 Wi-Fi/蜂窝切换；无 LAN 的手机配置。
- 系统 CA 信任库及严格节点 TLS 校验，SELinux Enforcing 下实测。
- 修复蜂窝 QoS 覆盖控制标记导致的 DNS 自循环，以及命名空间编号冲突。
- 可选的 Google 服务联网兼容：默认关闭，仅支持已验证 ColorOS 框架。在内核运行时维护两个 Google 应用的联网策略，停止时恢复原值。

不声称兼容所有设备。当前设备不支持基于进程名的 pname 路由；应用实际可用性与协议范围见实测记录。

## 维护结构

Rust 完整源码和上游提交历史保留在仓库根目录。Android 集成源码位于 [`android/`](android/README.md)，面板来源和基线输入由 `android/upstream.lock.json` 固定；补丁已包含在源码中，无需重复应用。

```sh
# 只修改模块服务或 WebUI 后打包，复用已验证运行时输入
python android/tools/fetch-runtime.py
python android/tools/seed-defaults.py
python android/tools/package.py
```

修改 Rust 后需先执行完整 Android 构建，参见构建说明。GitHub Actions 提供基础检查和手动构建入口。公开源码只包含维护所需文件，设备资料保留在本地，不应提交。

## License / 上游

核心与 Android 集成：AGPL-3.0-only，保留上游版权和许可；面板 MIT，KernelSU JavaScript SDK Apache-2.0。第三方说明见 `android/module/licenses/THIRD_PARTY.md`。上游原 README 与工作流归档在 `docs/upstream/`。
