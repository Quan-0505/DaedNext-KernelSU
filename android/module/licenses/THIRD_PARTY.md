# Third-party components

- DaeNext core: ksong008/DaeNext, AGPL-3.0-only. Local Android source changes are supplied in `core-patches/android-complete.patch` in the development project.
- DaedNext panel: ksong008/DaedNext, MIT. The source commit is recorded in `build-info.json`.
- KernelSU JavaScript SDK 3.0.2: tiann/KernelSU, Apache-2.0, copyright KernelSU contributors. `webroot/kernelsu.js` is included from the official npm package.
- Android NDK `libc++_shared.so`: LLVM libc++, Apache-2.0 with LLVM exceptions. Provided by the official Android NDK r27c.
- Geodata: the user-provided DaeNext skill bundle's geoip.dat / geosite.dat, retained as input assets. Their upstream update sources are configurable in the product panel.

The module's integration scripts and WebUI are provided under AGPL-3.0-only.
