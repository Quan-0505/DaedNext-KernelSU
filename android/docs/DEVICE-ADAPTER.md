# Android 跨设备适配接口（仅 eBPF）

目标是按运行能力适配设备，而非按品牌或型号放行。alpha9 已统一 KernelSU、Magisk、APatch 安装和服务脚本；目前只有一加 13 / KernelSU 完成真机验证，其他管理器的隔离脚本检查不等于真机兼容认证。只允许 eBPF 接管，不提供 TUN 回退。

## 已实现：只读能力报告 v1

```sh
/data/adb/modules/daenext/bin/daenextctl capabilities
```

返回 JSON，固定 `schemaVersion: 1`，不启动核心、不加载 BPF、不创建网卡、不更改路由。调用者仍需 Root。源码新增该命令，旧版发布包不提供它。

| 字段 | 含义 |
| --- | --- |
| `android` | SDK 与 ABI，不读取设备序列号或账户 |
| `kernel` | 内核版本及 BTF、cgroup v2、网络命名空间、TUN 设备的可见性 |
| `root` | 当前 UID、管理器身份与已知 BusyBox 路径；身份优先读取安装环境或经过白名单校验的安装标识，旧版无法确定身份时为 unknown |
| `selinux` | 当前 SELinux 模式，不修改策略 |
| `validation` | BPF 加载、TCX/cgroup 挂载、命名空间创建、实际流量均标为 `not_tested` |
| `policy` | ebpfOnly=true、requestedAttachBackend=auto、nonEbpfFallback=false |
| `backends` | eBPF 仍需运行时验证；TUN 为 `disabled_by_policy` |
| `compatibility` | 固定 `unverified`；不能由路径存在推断兼容成功 |

BTF 文件可读不等于程序能加载，网络命名空间可见不等于允许创建，TUN 设备存在也不等于本核心已有 TUN 接管实现。报告没有 IMEI、SSID、IP、节点、订阅或登录信息。

## 安装、运行与适配边界

1. **基础运行环境**：root-env.sh 集中选择三种管理器的官方 BusyBox 路径，优先识别 APatch，避免其 Magisk 兼容变量造成误判。安装时写入 root-manager 文本，运行时读取但不执行。已识别管理器缺少自己的 BusyBox 时直接报错，不借用其他管理器残留文件。Magisk 提供 META-INF 入口；只有 KernelSU 请求热安装。不支持 Recovery 安装。
2. **设备能力与运行预检**：在独立测试资源上验证 BPF 加载、TCX/cgroup、veth 和命名空间，记录失败阶段并清理测试资源；成功前不得标记可用。
3. **网络接管后端**：模块固定启用原生 eBPF、resident 数据面和 auto 挂载。复用核心现有的 TCX／传统 TC eBPF 选择及真实加载、挂载和后置校验；TCX 不可用时按核心逻辑清理并尝试 TC，二者都属于 eBPF。不满足核心准入条件则拒绝接管，保留管理入口，不切换 TUN、不关闭 SELinux。
4. **厂商适配**：通用连接事件与网络接口发现优先；厂商联网兼容独立挂载，按能力与框架校验启用。当前 ColorOS Binder 兼容保持精确匹配，不推广到未知框架。
5. **统一管理接口**：App／WebUI 使用同一套能力、状态、启停与配置接口；配置未完成、权限不足、内核能力缺失和节点握手错误分别呈现。
6. **验收矩阵**：覆盖不同内核／系统与 Root 管理器；分别验证升级保留、冷启动、Wi-Fi／蜂窝切换、黑白名单、实际 TCP／UDP／DNS 流量及失败还原。

朋友的 AnyTLS 报错属于节点、群组和路由关联问题，详见 [首次代理配置](FIRST-PROXY.md)，不属于跨机型内核适配的证据。

## 配置检查与实际运行证据

`daenextctl config-check` 只读检查数据库及生成的运行配置，不等于内核预检通过。运行时通过已有的认证接口 `/api/runtime/overview` 查看 `runtime.startupEvidence` 中的加载结果、bindings、bindingRegistry 和 bindingPostflight，并结合 residentDataplane 与实际流量验证。`status` 的 runtimeRequired、runtimeRunning、networkPolicyPaused 分别表示用户意图、实际运行与 Wi-Fi 策略直连。

隔离测试覆盖安装矩阵、身份持久化、变量重叠、BusyBox 缺失、恶意标识以及 eBPF-only 策略。一加 13 KernelSU 已验证脚本启停、蜂窝 eBPF 准入和 TCX 绑定后置证据，App UID 访问 YouTube／Google 返回 HTTP 204；随后恢复 1503 自动直连。生成配置除导出时间戳外一致。Magisk、APatch 真机及冷启动尚待验证。

规范依据：[KernelSU](https://kernelsu.org/guide/module.html)、[Magisk](https://topjohnwu.github.io/Magisk/guides.html)、[APatch](https://apatch.dev/apm-guide.html)。
