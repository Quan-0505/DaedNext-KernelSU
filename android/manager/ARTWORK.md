# App 美术素材

## C 版桌面图标（Manager 0.1.7）

- 透明前景：`res/drawable-nodpi/launcher_goose.png`，由内置 image_gen 从用户选定的 C 版卡通鹅概念提取。保留白鹅、粗黑描边、橙色喙与脚、小灰刀及跑动姿态。
- `res/drawable/launcher_background.xml` 与 `launcher_speeds.xml` 绘制黄色渐变和速度线；`launcher_foreground.xml` 使用 21% 相对 inset。
- `res/mipmap-anydpi-v26/ic_launcher.xml` 提供自适应图标；`res/mipmap-anydpi-v33/ic_launcher.xml` 增加单色主题层。前景 SHA256 已纳入公开源码审计白名单。
- 提取提示词：

Use case: background-extraction / precise-object-edit for a production Android adaptive icon. The attached image is the USER-SELECTED FINAL cartoon icon concept C. Preserve the exact same running white goose design and pose: long forward neck facing right, raised folded wing, cute glossy black eye, thick smooth black outlines, small gray rounded-tip knife with yellow handle in the orange bill, broad orange webbed feet, the same gray cel-shading and proportions. Extract ONLY that goose WITH its knife and feet as a single isolated transparent foreground asset. Remove the entire golden-yellow background, ALL white speed lines, ALL dots, ALL ground shadow. Do not redraw into a different goose, do not change expression or direction or style, no collar/backpack or new accessories. Keep original thick black outlines and clear internal feather shapes. Genuine alpha-transparent background, no baked checkerboard, no opaque white or black rectangle, no icon border, no rounded square, no text. Center the extracted complete subject inside a square canvas with about 10 percent transparent breathing room on every side and no cut-off wing, knife or feet. Main goose silhouette should occupy 80 percent of canvas width, scale proportionally. This is a clean production foreground layer for Android, not a presentation mockup.

## B 版琉璃鹅核素材

用于 DaedNext Manager 0.1.6。通过内置 image_gen 从用户选定的 B 版概念生成两套透明素材，App 离线加载，无运行时网络请求。

- 浅色：`res/drawable-nodpi/goose_core_light.png`
- 深色：`res/drawable-nodpi/goose_core_dark.png`
- 生成尺寸：1254 × 1254，RGBA。Android 以 2 倍采样缩小解码，单张约 1.5 MiB，按当前外观只加载一套。
- 身份参考：[dae 官方标识](https://github.com/daeuniverse/dae/blob/main/logo.png)。白鹅、橙色喙与脚、小灰刀来自该视觉方向；B 版概念及本次素材由 image_gen 生成。
- 轨道、轨道珠、聚散粒子和缩放由 `GlassGooseView` 绘制。资源保留透明背景；鹅核使用图片纹理以保持选定概念的玻璃质感，解码失败时使用原生路径回退。

## 生成提示词

Use case: precise-object-edit. This is a production illustration asset extraction/refinement from the selected B concept. Reference is a light/dark diptych of a translucent blue-violet glass globe with a satin white dae goose inside. Produce ONE single centered isolated glass globe with the EXACT same sculptural white goose pose, tiny orange bill/feet and small gray knife, glass ribbons and opaline 3D material as this reference, at the same relative scale inside the globe. No letter B, no split, no text, no background rectangle, no orbit line, no orbit pearls, no floor shadow: these are drawn separately by the Android app. Keep the circular sphere, soft white/cyan glass highlights, flowing periwinkle/lavender bands, visible dimensional goose, very clear silhouette at small size. Globe occupies 84 percent of a square canvas and is centered. Genuine transparent alpha background outside the globe, including corners; keep the glass object's material and highlights visible, no checkerboard baked in. Premium native Android hero asset, crisp smooth edges. Do not flatten the goose to vector clip art. Make the sphere self-contained with no cropped pixels.

### 浅色追加

Adapt the reference LEFT panel material for the light theme: milky translucent pale blue/lavender glass, soft opaline highlights, very gentle contrast.

### 深色追加

Adapt the reference RIGHT panel material for the dark theme: rich blue/violet clear glass, cyan rim illumination, opaline white goose, dark transparent interior, restrained premium glow.
