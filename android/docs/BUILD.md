# 构建与维护

## 修改服务 / WebUI 后重新打包

Python 3.10+、Git 即可完成这一流程，Windows 和 Linux 均可使用。它复用 alpha7 发布包内已验证的核心、面板、NDK 运行库与 geodata，不重新编译 Rust。

```sh
git clone https://github.com/Quan-0505/DaedNext-KernelSU.git
cd DaedNext-KernelSU
python android/tools/fetch-runtime.py
python android/manager/build.py
python -c "import shutil; shutil.copyfile('android/dist/DaedNext-Manager-v0.1.12.apk', 'android/module/manager/DaedNext-Manager.apk')"
python android/tools/seed-defaults.py
python android/tools/package.py
```

安装 ZIP 输出到 `android/dist/`，SHA256 显示在打包输出中，不生成单独校验附件。离线已有 alpha7 安装包时，可使用 `fetch-runtime.py --archive /path/to/DaedNext-KernelSU-v0.1.0-alpha7-arm64.zip`；脚本先检查锁定的 SHA256，只提取构建输入，不导入配置或用户数据。

恢复脚本会取回旧版 APK；修改 App 后须按上述步骤构建并替换。App 构建需要 JDK 17、Android SDK 35 与 Build Tools 35.0.0，签名维护见 [管理器说明](../manager/README.md)。

发布新版本前修改 `android/module/module.prop` 的 version/versionCode，更新 WebUI 版本标识及 CHANGELOG。不要把用户数据库复制到 module/defaults；默认数据库由源码 schema 生成。

## 修改 Rust 核心后完整构建

已使用的工具链：Rust 1.98.0、nightly-2026-09-01（rust-src）、bpf-linker 0.10.3、Android NDK r27c / 27.2.12479018。构建入口面向 Linux x86_64，建议至少 8 GiB 内存和充足磁盘。

Debian/Ubuntu 依赖包括 build-essential、clang/LLVM、CMake、Perl、pkg-config、Python 3、Git。安装 rustup 与官方 Android NDK 后：

```sh
rustup toolchain install 1.98.0 --profile minimal
rustup target add aarch64-linux-android --toolchain 1.98.0
rustup toolchain install nightly-2026-09-01 --profile minimal --component rust-src
cargo +1.98.0 install bpf-linker --version 0.10.3 --locked
export RUSTUP_TOOLCHAIN=1.98.0
export DAE_RUST_NATIVE_BPF_TOOLCHAIN=nightly-2026-09-01
export ANDROID_NDK_HOME=/path/to/android-ndk-r27c
python3 android/tools/fetch-runtime.py
bash android/tools/build-android.sh --check-env
bash android/tools/build-android.sh
python3 android/tools/seed-defaults.py
python3 android/tools/package.py
```

Rust 修改后必须执行 build-android.sh；只重新打包会继续使用旧核心。链接脚本在忽略的 `.build` 目录生成 NDK libgcc 兼容链接，保留 Android 16 KiB 页对齐；不会写入系统 `/opt` 或依赖某台私人构建机。

## 构建面板

面板源码由 upstream.lock.json 锁定。需要 Node.js 22.12+ 与 pnpm 10.24.0。

```sh
corepack enable
bash android/tools/build-panel.sh
python3 android/tools/package.py
```

面板检出到 `android/.build/panel-source`。已有不同版本的检出不会被自动重置；升级面板时保留自己的修改后使用新的构建目录，并修改锁定提交。

## 合并上游核心更新

```sh
git switch -c maintenance/update-core
git remote add upstream https://github.com/ksong008/DaeNext.git  # 仅首次需要
git fetch upstream work/boringssl
git merge --no-ff upstream/work/boringssl
```

处理冲突时保留 Android 特有改动。特别检查控制 socket mark、蜂窝 QoS 下的 socket 标记识别、网络命名空间编号、路由探测、辅助进程、TLS 信任库与 schema 迁移。根 README 保留本项目说明，上游原说明见 docs/upstream。

更新 android/upstream.lock.json 的上游基线和 daemonVersion，再生成补丁：

```sh
git diff <new-upstream-commit> -- crates Cargo.toml Cargo.lock > android/patches/android-complete.patch
```

运行相关 Rust 回归和完整 Android 构建，在实机验证后再更新发布包。基础回归：

```sh
cargo test --locked -p dae-resident-model --lib android_control_mark
cargo test --locked -p dae-ebpf-program --features android-fwmark --lib
cargo test --locked -p dae-daemon --lib production_runtime_owner::topology::tests
```

发布前复验标准 KernelSU 安装、冷启动、Wi-Fi/蜂窝切换、实际应用流量、严格 TLS、账户/订阅保留，以及兼容开关关闭/停止时的还原。进程存活不等于代理正常。

## GitHub Actions

- Android source checks：检查新增 Android 源码、Shell/Python/JavaScript 语法以及空白默认数据库，不声称覆盖 Android 数据面。
- Build Android module：手动完整构建核心，复用锁定的面板/geodata 输入并上传 Artifact；选择 rebuild_panel 可重新编译锁定面板。工作流不自动创建 Release。

不要把 SSH 密码、订阅或手机数据库放进 Actions 变量或构建产物。GitHub Release 只上传模块 ZIP 和独立 APK 两个附件，不上传单独的 SHA256 文件；如需展示校验值，写在发布说明中。新版本在实机通过验收后再发布。
