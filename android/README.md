# KernelSU Android integration

当前模块：**v0.1.0-alpha4**；核心：**3.1.0-android-ksu.3**。

本目录只包含可公开维护的模块源码、空白默认配置生成器、构建入口和 Android 补丁。Rust 核心完整源码位于仓库根目录，已经包含 Android 修改，克隆后不要重复应用补丁。

- `module/`：KernelSU 服务、控制器、WebUI、证书和配置管理。
- `tools/`：构建、恢复已发布构建输入、生成空白数据库、打包和公开源码检查。
- `patches/android-complete.patch`：相对于锁定上游提交的完整 Android 补丁；编号补丁仅作为历史记录。
- `upstream.lock.json`：上游版本、面板版本、已验证运行时输入及 SHA256。
- `docs/`：构建、维护及实测范围。

安装包见仓库 [Releases](https://github.com/Quan-0505/DaeNext-KernelSU/releases)。默认不含账户、节点、订阅，初始规则直连。更新和卸载保留 `/data/adb/daenext`；使用前备份自己的配置。

公开仓库不接收设备数据库、备份、抓包、登录信息、订阅 URL、节点 URI 或个人截图。提交前运行 `python android/tools/audit-public.py`。
