# B 版琉璃鹅核素材

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
