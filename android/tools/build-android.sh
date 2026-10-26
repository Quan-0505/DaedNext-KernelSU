#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "$0")/../.." && pwd)
export LC_ALL=C.UTF-8
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to Android NDK r27c (27.2.12479018)}"
TC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
for executable in aarch64-linux-android24-clang aarch64-linux-android24-clang++ llvm-ar llvm-strip; do
 test -x "$TC/$executable" || { echo "Missing NDK tool: $executable" >&2; exit 1; }
done
for command in cargo rustup bpf-linker cmake perl python3; do command -v "$command" >/dev/null; done
if [ "${1:-}" = --check-env ]; then
 cargo --version
 bpf-linker --version
 grep Pkg.Revision "$ANDROID_NDK_HOME/source.properties"
 exit 0
fi
BUILD="$ROOT/android/.build"
mkdir -p "$BUILD/ndk-shim" "$ROOT/android/module/bin" "$ROOT/android/module/lib"
unwind=$(find "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/lib/clang" -path '*/lib/linux/aarch64/libunwind.a' -print -quit)
test -n "$unwind" || { echo 'NDK libunwind.a was not found' >&2; exit 1; }
ln -sf "$unwind" "$BUILD/ndk-shim/libgcc.a"
cat > "$BUILD/android-linker.sh" <<'LINKER'
#!/usr/bin/env bash
exec "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang" "$@" "-L${DAENEXT_NDK_SHIM_DIR:?}"
LINKER
chmod 755 "$BUILD/android-linker.sh"
export DAENEXT_NDK_SHIM_DIR="$BUILD/ndk-shim"
export CC_aarch64_linux_android="$TC/aarch64-linux-android24-clang"
export CXX_aarch64_linux_android="$TC/aarch64-linux-android24-clang++"
export AR_aarch64_linux_android="$TC/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$BUILD/android-linker.sh"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384'
export CARGO_PROFILE_RELEASE_LTO=thin
export CARGO_PROFILE_RELEASE_CODEGEN_UNITS=4
export CARGO_BUILD_JOBS=${CARGO_BUILD_JOBS:-4}
export DAE_DAEMON_VERSION=${DAE_DAEMON_VERSION:-$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["daemonVersion"])' "$ROOT/android/upstream.lock.json")}
cd "$ROOT"
cargo build --locked -p dae-daemon --bin daed --release --target aarch64-linux-android
cp target/aarch64-linux-android/release/daed android/module/bin/daed
"$TC/llvm-strip" android/module/bin/daed
cp "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" android/module/lib/
sha256sum android/module/bin/daed
