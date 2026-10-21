# Deadliner 最新材质与主 APP 底栏渐进背景

2026-10-20。源项目 `/Users/aritxonly/Codes/Android/Deadliner`，核对完成时 HEAD
`9119af22827e15d4b5b3bea7d938f865800ee1da`。仅读取源项目。
目标沿用 MIUIX 0.9.4-rc01、Shapes 1.2.0、Compose BOM 2025.01.00。

## 材质同步

完整同步 `GlassShadowPreset`、`AdvancedMaterialSpec`、`SoftGlassShadow`、
`TopBarGlassShadowHost` 与 `SoftGlassSurface` 的当前源实现。Shader、折射采样、光学配方、
默认高级参数已与源项目一致；保留本地 Shader 归属注释。

浅色自研玻璃默认使用 `BalancedDiffuse`：ambient 26dp / alpha 0.03459375，
outer 10dp / alpha 0.06490625，edge 2dp / alpha 0.0094375。
顶部控件 ambient 为 34dp / alpha 0.043453125。暗色及官方材质沿用原配方。
预设模型是源材质的依赖；不迁入源应用的实验室入口或业务偏好存储。

主 APP 顶部渐进材质内接入同一 `TopBarGlassShadowHost`，让控件阴影在顶部背景之后绘制，
并随 material alpha 渐进显现。移除本地按钮的 0.34 阴影半径缩放，使用源实现的完整阴影。
保留现有渐进进度、启用开关和本地 CompositionLocal 接口。

主 APP Scaffold 去掉固定 92 的模糊度覆盖，使用已同步的默认 84。
注入底栏的原生图标、Lottie、角标、宽度、采样和生命周期等本地适配接口保留。

关键源 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| SoftGlassShadow.kt | baa6a84f6bbd8654ea7f51a3b95110f052fdfa00c06c64fda6c340e6caafe0a3 |
| TopBarGlassShadowHost.kt | de0c12520aaca4eced9a84a26e43cc50fe4e7b6f04fd7dce14a0ce65e4198509 |
| GlassShadowPreset.kt | bb0bd9a83b35179a40b7b4d27bb61fdcd5cbcbee896450afc979f713c8e01569 |

## 主 APP 底栏背景

对照源 `ImmersiveMiuixCompactNavigationScaffold.FloatingNavigationEdgeMask`：
覆盖高度为底栏 54dp + 底部间距 4dp + 额外 32dp + navigation inset；主 APP 无额外 lift。
在主页/关于共用的底栏 Row 上绘制从透明到当前 MIUIX surface 的全宽渐变。
保持现有底栏间距、内容占位和点击范围；二级页不创建底栏背景。

复用 `navigationEdgeMask` 的缓存渐变，新增可选 coverageHeight：从底部反算渐变起点，
允许向底栏测量区域上方延伸，实现源独立遮罩的视觉覆盖。已核对当前 MIUIX Scaffold
底栏布局没有裁剪这段向上延伸区域。渐变位于内容专用 LayerBackdrop 之外，不被底栏采样。
没有增加截图、模糊、独立离屏层、全局 Hook 或轮询；无参数调用保持其他应用现有行为，
米家和抖音的渐进背景例外继续保留。

## 性能与验证边界

阴影继续使用源实现的三段 dropShadow，未增加阴影段数；顶部外置阴影宿主按现有所有权
及 Dispose 清理，避免对话框跨 View 注册。默认扩散半径和透明度变化可能增加 GPU
绘制覆盖，未做真机帧耗时/耗电对照，不宣称性能改善。

迁入源逻辑测试验证浅色阴影、暗色/官方不变、顶部补偿和底栏阴影配方；不迁入源截图测试。
遵照用户要求不截图，布局和动画效果尚需真机确认。

本次验证：381 项 JVM 测试全部通过，Release/R8 构建和 APK v2 签名验证通过。
Lint 报告仍有 17 项已有错误、205 项警告，未声称全量 Lint 通过。
`git diff --check` 通过，构建日志 `/private/tmp/hmc-material-sync-edge-mask.log`。
