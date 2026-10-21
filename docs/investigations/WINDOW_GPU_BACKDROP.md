# 高德、米家、运动健康：窗口 GPU 采样第二轮

2026-10-20。针对上一轮停帧、采样迟滞、高德导航页闪烁和二级页返回异常，
这三款不再复用 decor RenderNode 作为独立窗口的材质源。

## 路径和问题边界

首轮独立窗口只 invalidate ComposeView，不能保证缓存的 Compose backdrop layer 重录；
地图 SurfaceView 的独立缓冲也不在普通 View 显示列表中。以上是代码层面的风险，
不是已经通过真机追踪证实的全部根因。

新 `WindowGpuBackdropSampler` 使用应用自身窗口 SurfaceControl 的异步 layer capture，
在工作线程提交，返回 HardwareBuffer 直接包装为 HARDWARE Bitmap，并沿用原有
ViewBackdropSnapshot / ViewBackdropLayer 显示接口。因此每个发布的新帧都显式推动
Compose 材质更新，不依赖仅对 View 的 invalidate。GPU 主路径不调用 View.draw、
copyPixelsFromBuffer、getPixels 或 Bitmap.copy，不将像素读回 CPU。

捕获仅当前应用 UID，裁剪到底栏与至少 48dp 的边缘（attach 时按完整模糊核和折射位移计算一次），最长边中的宽度限制为 384px。
显式 childrenOnly=false 包含窗口本体，显式 excludeLayers 排除独立底栏窗口，
即使其 Surface 位于应用 Surface 子树中也不递归采样自己的材质。
不改变原有独立窗口承载、navigation inset、原色图标和底部渐变。

平台签名/行为依据本机 Android SDK 35 源码：
`android/view/View.java#getViewRootImpl`、`ViewRootImpl.java#getSurfaceControl`、
`ViewTreeObserver.java#registerFrameCommitCallback`、`android/window/ScreenCapture.java`。
LayerCaptureArgs.Builder 默认 childrenOnly=true，不能直接沿用视频子层的配置捕获窗口本体。
这些是隐藏平台访问方案，ROM 支持性必须以实际设备结果为准。
getSurfaceControl 被 Lint 标记为 target API 35 的 BlockedPrivateApi。该点是有意的
Xposed 注入进程访问（不是模块普通 UI 路径），在仅包含窗口访问计划的 companion 上
明确 SuppressLint 并保留异常回退。没有修改 VM 全局隐藏 API 豁免；如果目标进程仍拒绝
访问，这个宿主将只使用 PixelCopy。抑制静态诊断不等于已证明设备可用。

`NativeLayerCapture.capture` 的现有视频调用保持原参数；新增窗口路径只在窗口调用时
使用可选的 exclude/children 方法，缺少这些方法时仅窗口 GPU 回退，不影响原视频路径。

## 帧、恢复和兼容回退

- 普通 View 帧从 frame commit 回调驱动；最多一个 GPU 请求，后续源帧合并为 dirty 标记。
- 约 60Hz 上限（按毫秒节拍）；源静止时无持续采样。地图 Surface 可独立更新，触摸/转场后只在有限
  240–1200ms burst 内跟随。系统采样 IPC 在工作线程，不在触摸/绘制回调同步等待。
- 窗口控制元数据一次解析；控制句柄在激活时重取，逐帧只做有效性检查，不重复查找方法。
- attach / Surface 尚未就绪最多重试 3 次，每次 32ms，随后回退；不存在无限启动重试。
- native callback 最多等待 750ms；拒绝、错误、非硬件结果或超时后，本宿主其余生命周期
  使用旧 PixelCopy 路径，不在每帧反复探测权限/隐藏 API。旧路径禁止软件绘制回退。
- 原生回调通过弱引用找宿主；dispose 后旧回调不强持有 Activity/View。已发布缓冲由显示
  列表引用寿命释放，只有未发布/过期/重复结果才 recycle。
- pause / disable 停采样并清空快照；resume 清除旧上下文并重新获取控制。页面、裁剪和
  激活 epoch 不匹配的结果丢弃，不把二级页/旧尺寸内容发布回首页。
- 三款增加 main Activity 过滤的 resume/pause 桥接，pause 在原回调前停用。
  运动健康补齐可见状态、触摸区域释放、原生底栏/分隔线、内容 padding 和系统材质恢复。
- 反射 metadata 只静态缓存平台类/方法。后台工作线程按需启动，回退或销毁后退出。

## 验证

`GpuBackdropFrameGateTest` 覆盖源帧合并/单请求、裁剪切换、pause/resume、超时取消、
重复与过期回调、空闲退出；这些测试验证状态逻辑，不验证 ROM 隐藏 API 或 GPU 合成。

首次 GPU 成功日志为 `Window GPU backdrop active: ... HARDWARE`；失败一次记录
`Window GPU backdrop unavailable; using legacy PixelCopy: ...`，便于设备日志确认实际路径。
不使用逐帧日志、像素内容检查或诊断截图。

该方案增加 compositor capture 工作和硬件缓冲轮换；相对 PixelCopy 省去了 CPU 像素回读，
但没有真机对照数据，不能据此声称帧率、GPU 时间、卡顿率或耗电改善。
真机仍需覆盖：高德首页/地图拖动/惯性/导航进出，米家设备详情/场景多选进出，
运动健康二级详情进出；同时验证后台返回、旋转、禁用恢复及接口失败回退。
遵照用户要求不截图。

最终验证：365 项 JVM 测试通过（包括 4 项新增帧请求状态测试），Release/R8 构建成功，
APK v2 签名验证通过；`git diff --check` 通过。最终 Lint 为 17 个既有错误、203 个警告，
窗口 Surface 的已知 BlockedPrivateApi 仅在有意访问计划处局部标注，未报告新增 error。
新增警告包括平台私有访问兼容性风险，不能把上述结果视为 ROM 运行验证。
日志：`/private/tmp/hmc-window-gpu-delivery.log`（含完整测试）和
`/private/tmp/hmc-window-gpu-release.log`（最终签名构建及 Lint）。
