# GPU 底栏与 Deadliner 最新材质迁移

## 前轮回退状态（2026-10-20）

后续重新尝试窗口 GPU 采样，当前方案见 [窗口 GPU 采样第二轮](WINDOW_GPU_BACKDROP.md)。

用户反馈高德导航页闪烁/采样停住，米家和运动健康迟滞、二级页退出异常。
按用户授权，高德、米家、运动健康恢复 GPU 迁移前的完整 ViewBackdropSampler/PixelCopy
路径、触摸刷新、启动捕获和恢复逻辑；不再使用 NativeGpuBackdrop。
保留最新 Deadliner 材质、底部渐变和 Activity 索引弱值。PixelCopy 的原有节流与
禁止软件回退仍然有效；米家、高德沿用可见状态停采样。以下 GPU 数据路径段落记录的是首轮尝试。

哔哩哔哩继续 GPU 树内渲染，补回原生 navigation inset：从现有
BilibiliHomeImmersion 原生 padding / 导航背景读取流程，在清零原生留白之前记录实际值，
作为稳定 inset 的附加来源。无固定高度或系统 dimension 猜测；不新增全局 Insets Hook。
与窗口 inset 合并后驱动 navigationPadding，配置方向变化时丢弃旧方向值，临时零值保持
已观测的当前方向值；禁用时仍按原流程恢复原生 padding / 背景 inset。
新增逻辑回归覆盖 inset 被消费、短暂零值、旋转清理及实际窗口值更新。

这些修改尚无真机操作及帧时间对照；回退是恢复原路径，不能仅凭编译宣称全部视觉问题消失。


## 范围与来源

2026-10-20。底栏包括运动健康、米家、高德、哔哩哔哩、小米社区、支付宝、Spotify、抖音。
应用商店保留原方案，设置入口移到末尾，标记「已有官方底栏方案 · 默认关闭」。
仅改变未设置时的默认值，不清除用户已明确保存的开启状态。
默认不安装 MarketHooks；手动开启后由设置回调安装，通过 onResume 接入已经存在的主界面。

Deadliner 本地源：`/Users/aritxonly/Codes/Android/Deadliner`，
revision `5499df09383e84a120e19bd0c629c6e6ffa7ef0d`。本轮只读取源项目。
源/目标均为 MIUIX 0.9.4-rc01、Shapes 1.2.0；Compose BOM 源为 2025.08.00，目标为
2025.01.00，按目标实际解析的依赖编译验证。

## GPU 数据路径

- 运动健康、米家、高德：独立应用面板窗口复用宿主 decor 的现有 RenderNode。
- 哔哩哔哩、小米社区：改为树内宿主，分别只记录原生 content / M0 内容 View。
- 支付宝：沿用 tabcontent GPU 路径；不重新引入滚动内容 padding 或 clipToPadding 改写。
- Spotify：沿用内容 RenderNode 与播放条/底栏共享区域，移除硬件 Bitmap / PixelCopy 回退。
- 抖音：沿用内容 RenderNode 与视频 Surface 的 GPU 路径；Surface 不在 View 显示列表中，
  保留异步硬件缓冲视频合成，不引入 CPU 像素回读。
- 应用商店：用户要求排除 GPU 迁移，手动开启时沿用原采样方案。

GPU 不可用时保留可读材质底色，不启动 CPU 采样回退。独立窗口仅在源绘制时合并一次
consumer invalidation；同窗不从 OnDraw 再触发自身帧，避免反馈循环。
保留源显示列表，裁剪区域只在边界/源几何改变时重录，不调用 View.draw 或强制源更新。
删除已经没有用途的 Activity 触摸采样桥接、滚动采样请求与米家启动捕获重试。
GPU 目标不再构造旧 ViewBackdropSampler，避免其 Handler、缓存及无用途滚动监听。
Amap/MiHome/Health/Spotify 的 Activity 索引使用弱值，避免宿主反向引用导致弱键失效。

## Deadliner 闭包与本地扩展

同步 `model/AdvancedMaterial{FineTuning,Level,Tuning}`、`ui/theme/AdvancedMaterial`、
`ui/navigation/{MiuixFloatingTabBar,FloatingTabMotion,FloatingNavigationShadow}`；
同步材质、折射 shader、采样核 padding、边界高光、阴影宿主、SoftGlassSurface、FAB、
按压反馈及官方材质依赖闭包。保留 MIT/Apache 许可和 vendored upstream hashes。
未复制无关业务页面，也未更改现有顶部栏交互。

最新基础模糊半径 52，底栏倍率 0.4；同步饱和度、浅色 tint 的 8-bit 量化、高光/阴影，
新 rimWidth uniform 与完整模糊核采样范围；Spotify 原生播放胶囊也补齐新 uniform，
GPU 裁剪余量在 attach 时根据最新参数计算一次。按压改用 canonical SinkFeedback，
取消旧的指示器和图标重复缩放，保留拖动、弹簧与外部选中同步。
堆叠标签 80dp/项且最大 380dp；横排 104dp/项加双侧内容间距。

本地扩展继续保留：原色图标、原生双态图标、角标、live icon renderer、显式 recipe/tint、
Spotify 全宽模式、独立操作胶囊和原生路由。FAB 颜色由调用者提供，
不依赖 Deadliner 的业务 HomeFabColors；设置页现有阴影缩放参数继续兼容。
官方玻璃实验分支只补齐依赖，默认 useOfficialMiuixGlass=false，仍使用最新版柔光方案。

## 底部渐进效果

来源：`ui/base/ImmersiveMiuixCompactNavigationScaffold.kt` 的
`FloatingNavigationEdgeMask` 与 `ui/base/EdgeFade.kt`。
原版是 surface 实色层加全高度 DstIn 渐隐，并非新的一路渐进模糊。
覆盖高度为 `54dp + 4dp + 32dp + navigation inset + bottom lift`。

除抖音、米家外的底栏宿主添加同样的透明→surface alpha ramp。抖音以显式
`bottomEdgeMask=false` 排除；米家按用户要求移除主页底栏容器的 `navigationEdgeMask()`。
应用商店仅在用户手动开启时显示渐进效果。直接在现有宿主 drawWithCache 中折叠等价的
surface + DstIn 合成，避免额外离屏层，brush 仅在尺寸/主题变化时重建。
绘制位于横向/系统 inset padding 之前，位于内容 recorder 之外，不改变胶囊点击区域。
顶部预留 32dp，内容占位仍扣除新增的视觉阴影空间，不将渐变带重复注入滚动列表。

## 性能和验证边界

静态检查关注启动 hook 安装、每帧分配、源/消费层循环、弱引用、原值恢复、启停、
导航/IME inset 与内容占位。GPU 迁移移除了软件绘制、CPU 像素回读和无用途触摸采样；
新增渐变不包含计时器、IPC、反射、独立采样或模糊。

编译、JVM 测试、Release/R8、签名和 Lint 报告的最终结果见本轮交付。
完整 Lint 基线已有 17 个错误（含现存 API 等级和私有 API 检查），不能视为全部通过。
遵照用户要求不截图。未通过真机滑动/FrameTimeline/Perfetto 前后对照测量，
不声称帧率、卡顿率、GPU 时间或耗电已改善；各应用的边缘滚动、旋转、前后台、
实时启停和多模块共存仍需要真机验证。

最终验证：`:app:testDebugUnitTest :app:assembleRelease :app:lintReportRelease --offline`
成功，358 项 JVM 测试全部通过；Release 已经 R8，APK v2 签名验证通过。
Lint 报告仍为 17 个既有错误；新增/迁移的材质与底栏文件未新增 Lint error。
`git diff --check` 通过。新增回归覆盖采样核边界、异向模糊、tint 过渡端点和拖动选中同步。
这些属于构建与逻辑验证，不代替真机视觉、兼容性和性能测量。

回退修正版验证：361 项 JVM 测试全部通过，Release/R8 构建成功，APK v2 签名验证通过；
Lint 仍为 17 个既有错误，`git diff --check` 通过。验证日志：
`/private/tmp/hmc-navigation-fallback-build.log`。未进行截图或真机性能测量。
