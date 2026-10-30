# Android 跨设备适配接口

目标是按运行能力适配设备，而非按品牌或型号放行。当前发布版本仅验证一加 13；本接口是诊断基础，不代表已经支持其他机型、Root 管理器或 TUN 后端。

## 已实现：只读能力报告 v1

```sh
/data/adb/modules/daenext/bin/daenextctl capabilities
```

返回 JSON，固定 `schemaVersion: 1`，不启动核心、不加载 BPF、不创建网卡、不更改路由。调用者仍需 Root。源码新增该命令，旧版发布包不提供它。

| 字段 | 含义 |
| --- | --- |
| `android` | SDK 与 ABI，不读取设备序列号或账户 |
| `kernel` | 内核版本及 BTF、cgroup v2、网络命名空间、TUN 设备的可见性 |
| `root` | 当前 UID 与已知 BusyBox 路径是否存在，不将其解释为管理器已适配 |
| `selinux` | 当前 SELinux 模式，不修改策略 |
| `validation` | BPF 加载、TCX/cgroup 挂载、命名空间创建、实际流量均标为 `not_tested` |
| `backends` | eBPF 仍需运行时验证；TUN 明确为 `not_implemented` |
| `compatibility` | 固定 `unverified`；不能由路径存在推断兼容成功 |

BTF 文件可读不等于程序能加载，网络命名空间可见不等于允许创建，TUN 设备存在也不等于本核心已有 TUN 接管实现。报告没有 IMEI、SSID、IP、节点、订阅或登录信息。

## 后续适配边界

1. **基础运行环境**：集中管理 BusyBox、su、证书目录和运行目录；适配并分别验证 KernelSU、Magisk、APatch 的安装与服务生命周期。当前主安装器仍只支持 KernelSU。
2. **设备能力与运行预检**：在独立测试资源上验证 BPF 加载、TCX/cgroup、veth 和命名空间，记录失败阶段并清理测试资源；成功前不得标记可用。
3. **网络接管后端**：保留原生 eBPF 路径；若要覆盖受限或旧内核，须另外实现、验证 TUN 或其他接管路径，包含 TCP、UDP、DNS、IPv6、Android fwmark 和防回环。不能用配置开关代替后端实现。
4. **厂商适配**：通用连接事件与网络接口发现优先；厂商联网兼容独立挂载，按能力与框架校验启用。当前 ColorOS Binder 兼容保持精确匹配，不推广到未知框架。
5. **统一管理接口**：App／WebUI 使用同一套能力、状态、启停与配置接口；配置未完成、权限不足、内核能力缺失和节点握手错误分别呈现。
6. **验收矩阵**：覆盖不同内核／系统与 Root 管理器；分别验证升级保留、冷启动、Wi-Fi／蜂窝切换、黑白名单、实际 TCP／UDP／DNS 流量及失败还原。

朋友的 AnyTLS 报错属于节点、群组和路由关联问题，详见 [首次代理配置](FIRST-PROXY.md)，不属于跨机型内核适配的证据。
