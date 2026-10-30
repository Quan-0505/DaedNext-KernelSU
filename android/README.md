# KernelSU Android integration

开发中模块：**v0.1.0-alpha9**（Manager **0.1.12**）；核心：**3.1.0-android-ksu.6**。当前 Release 仍为 alpha8 / Manager 0.1.7。

本目录只包含可公开维护的模块源码、空白默认配置生成器、构建入口和 Android 补丁。Rust 核心完整源码位于仓库根目录，已经包含 Android 修改，克隆后不要重复应用补丁。

- `module/`：KernelSU 服务、控制器、WebUI、证书和配置管理。
- `tools/`：构建、恢复已发布构建输入、生成空白数据库、打包和公开源码检查。
- `patches/android-complete.patch`：相对于锁定上游提交的完整 Android 补丁；编号补丁仅作为历史记录。
- `upstream.lock.json`：上游版本、面板版本、已验证运行时输入及 SHA256。
- `docs/`：构建、维护及实测范围。

安装包见仓库 [Releases](https://github.com/Quan-0505/DaedNext-KernelSU/releases)。默认不含账户、节点、订阅，初始规则直连。更新和卸载保留 `/data/adb/daenext`；使用前备份自己的配置。

公开仓库不接收设备数据库、备份、抓包、登录信息、订阅 URL、节点 URI 或个人截图。提交前运行 `python android/tools/audit-public.py`。

Wi-Fi 黑白名单与移动数据策略见 [Wi-Fi 自动切换](docs/WIFI-POLICY.md)。

原生 App、接口边界及签名维护见 [Android 管理器](manager/README.md)。

跨机型能力接口与适配范围见 [设备适配接口](docs/DEVICE-ADAPTER.md)。源码已提供只读 `daenextctl capabilities`；它不是其他机型已兼容的认证。
