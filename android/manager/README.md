# DaedNext Android Manager

原生 Android 管理器，与 DaedNext KernelSU 模块共同工作。交互分工参考 [NetProxy Android Manager](https://github.com/Fanju6/NetProxy-Magisk/tree/main/src/android)，客户端与模块接口独立实现，未复制其 UI 或业务源码。

## 安装

需要 Android 8.0 或更新版本、KernelSU Root 授权，以及本项目 alpha7 或更新模块。

1. 在 KernelSU 中安装 Release 的 ARM64 模块 ZIP，按管理器提示重启。
2. 从同一个 ZIP 解压 `manager/DaedNext-Manager.apk`，在手机安装。
3. 在 KernelSU 的超级用户列表中允许 **DaedNext**，再打开 App。
4. 已有账户、节点、订阅及 Wi-Fi 策略直接使用。首次安装没有账户时，进入高级面板完成账户和基础配置。

App 不附带代理核心，不能脱离模块提供代理。安装或卸载 App 不会删除模块中的配置。

## 功能

| 页面 | 操作 |
| --- | --- |
| 仪表盘 | 代理启用／暂停、当前 Wi-Fi 策略、进程状态、流量和内存概况。 |
| 节点 | 独立及订阅节点列表、延迟记录、单节点测速、链接导入、名称与链接编辑、删除。 |
| 订阅 | 添加、编辑、更新、删除及自动更新状态。地址默认掩码显示。 |
| 网络 | Wi-Fi 黑白名单分别编辑、添加当前 Wi-Fi、独立的非 Wi-Fi 代理选项。 |
| 设置 | 后台服务启停、开机启动、Google 联网兼容、一致性备份、日志。 |
| 高级面板 | 内嵌现有 DaedNext 面板，管理策略组、节点选择、DNS、路由及完整配置。 |

“暂停代理”记录用户停用意图，保留后台管理服务；“停止服务”关闭整个模块服务。Wi-Fi 策略直连期间启用代理只记录启用意图，待离开直连网络后恢复接管。

## 接口与安全边界

- 原生 Java Views，无 AndroidX、Kotlin、WebUI Root JS 桥；高级面板单独使用 WebView。
- `ModuleClient` 只执行固定命令白名单。SSID 通过标准 Base64 传输，不拼接进 Shell 源码。
- 已授权的 Root 客户端通过本地 Unix socket 获取现有用户的 10 分钟会话。socket 验证 UID 0；HTTP 不提供会话签发入口。不会向 App 返回密码或签名密钥，也不创建或重置账户。
- 原生 API 客户端仅访问固定 `127.0.0.1:2023`，禁用 HTTP 代理和重定向；会话只保存在内存。GET 的认证失效可更新会话重试一次，修改请求不自动重发。
- 高级 WebView 仅允许同一本机地址，不暴露 JavaScript 原生接口，禁止文件／内容访问。其短期会话通过本机页面引导，退出时清理；停留超过会话有效期可返回后重新打开。
- 不申请 VPN、存储或应用列表权限，不提供外部导出的服务或 Provider；Android 云备份关闭。日志只在手机显示，未添加遥测与自动上传。
- 不直接修改 SQLite、节点文件、订阅文件或运行时拓扑，配置变更由模块接口执行。

## 构建与签名

需要 Python 3.10+、JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。可离线构建，不依赖 Gradle 或第三方 Maven 库：

```powershell
$env:ANDROID_HOME = 'C:\Android\sdk'
python android/manager/build.py
```

输出 `android/dist/DaedNext-Manager-v0.1.0.apk`。首次构建在 gitignored 的 `android/private/` 下创建本地 PKCS12 密钥和元数据，后续构建沿用同一身份。**请单独安全备份该目录中的签名材料**；缺失原密钥时不能覆盖更新既有 App。不要把密钥、密码或设备数据提交到 GitHub。

发布签名证书 SHA256：`e2d6b055b318f5a647ba81f2fdfeecabca7442af393460f13fb21310c5af3312`。

CI 使用以下命令编译和对齐 APK，不读取或生成签名密钥：

```sh
python android/manager/build.py --unsigned
```

将已签名 APK 放入 `android/module/manager/DaedNext-Manager.apk` 后重新打包模块。运行时恢复工具会从锁定的 Release 取回 APK；修改 App 源码后应重新构建，不能用旧 APK 替代新代码。Release 仍只发布一个 ZIP，APK 位于包内。
