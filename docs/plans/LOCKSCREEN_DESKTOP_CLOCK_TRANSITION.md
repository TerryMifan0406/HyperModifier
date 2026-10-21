# 横向 AllInOne → 桌面 MAML 时钟

## 目标与约束

- 第一阶段只支持 AllInOne 横向单行布局（`mDoubleRow=false`）到默认经典 MAML 时钟。
- 由 SystemUI 主导显示和过渡，尽量复用原生字体、时钟与解锁动画路径。
- 桌面先零新增 Hook；只有现有接口确实不足时，才加入有证据的最小布局/可见性协作。
- 不硬编码格子对应的像素坐标，不把整个桌面窗口的缩放当成时钟缩放。
- 未确认目标、字体/时间格式不匹配、目标不在当前页、返回应用、息屏、编辑态、
  解锁取消或组件销毁时，必须退回原系统行为。
- 本轮手机端只读；不安装、不重启、不触摸、不修改设置。后续真机包需要用户
  手动安装和重启，或用户另外明确授权，才能继续验证新注入的实际回调。

## 提交基线

在新增原型代码前已按用户要求提交当前工作区：`9d98805`。
包含之前的模块迭代，不包含 Python 缓存及 reference APK/反汇编。

## 阶段与验收

| 阶段 | 工作 | 验收 | 当前状态 |
| --- | --- | --- | --- |
| 0 | 保存现有工作区 | 可回退的 Git 基线 | 完成 |
| 1 | 只读取证、核对绘制/解锁/资源契约 | 明确实际 APK、入口、退出和未决条件 | 静态取证完成 |
| 2 | 仅 SystemUI 的默认关闭诊断包 | 记录横向时钟边界及解锁事件顺序，不改画面 | v2 真机确认 |
| 3 | 锁屏侧连续过渡原型 | 在确认窗口生命周期后，复用数字显示路径，验证独立横纵缩放和取消恢复 | 字形准备与 v4 窗口显示已真机确认；v6 采样真机通过；v7 提前交接待真机，目标位移动画未实现 |
| 4 | 对接桌面真实目标 | 获得实时数字边界、当前页与可见性；完成双时钟隐藏/接管交接 | 待最小接口分析 |
| 5 | 回归与性能验证 | 开关恢复、打断、前导零、字体、页面/布局变化、多模块共存；同条件 Perfetto/FrameTimeline | 待原型 |

阶段 2 是观察器，不是动画功能。阶段 3 不能在未知桌面目标情况下冒充已经
实现一镜到底；可以先用明确标识的离线轨迹验证数学，但生产接管必须等待真实目标。

## 2026-10-21 只读设备证据

- ROM：`OS4.0.4.0.XPCCNXM`。
- 当前 SystemUI SHA-256：`d3628f6c3b85d16b1e74aef13ee227b41b6a855a01873c7f50364e274637f571`。
  与项目 OS4.0.2.0 参考基线相同；因此本轮沿用该 smali 作为实际设备匹配的证据。
- 实际启用桌面为 `/data/app` 更新包，不是 `/product` 预装包。
  `RELEASE-8.01.02.8583-260918-10161026-R`，versionCode `801028583`。
  APK SHA-256：`62277b6c54e84e0fae6375b69bf47c02e327ccac8e18b97102684d8522aafb83`。
  无 classes.dex，Rust launcher + Dart AOT；不依据该版本号启用 Hook。
- `LauncherProxyService` dump：`isConnected=true`，但这不等于已有可用的时钟共享元素接口。
- `dumpsys activity com.miui.home` 中的默认经典时钟：id `493`，screenId `1`，
  cell `(0,0)`，span `4×2`，MAML id `101390060`，productId
  `52b8238d-848f-4608-a0e6-28458d4b1f2c`，资源版本 `40130`。
  格子位置不等于数字像素边界，也不能证明当前页可见。
- 私有 MAML 资源目录普通 shell 访问被拒绝。通过转储中的官方资源地址只读下载
  同版 40130 包，解析内层 `widget_4x2`。资源/原始设备 dump 保留在本机临时目录，
  不提交原始日志、桌面应用清单、APK 或私有配置。
- 只读查询 `constant_lockscreen_info` 未得到可解析配置，不能据此确认当前手机
  正在显示横向 AllInOne。必须由进程内诊断确认。

## 源码契约和限制

### 锁屏绘制

`AllInOneBase` 提供 `mDoubleRow`、`mTimeGroup`、`mHourView`、`mMinuteView`、
`mColonView`；`TimeView` 使用原生 `Paint.getTextPath`，且存在 width/height/weight
字体轴。直接改整个容器的 scaleY 是可测试的几何近似，不等同于已证明复现字体轴。

诊断记录的是各 View 的**全局可见矩形**，包含父窗口裁剪，不是精确字形墨迹边界。
真正接管前还要区分字符 advance、字形边界、基线、窗口坐标和桌面窗口变换。

### 解锁时序

在实际匹配 APK 中有以下方法，可以解析固定元数据后观察：

- `KeyguardViewMediator.startKeyguardExitAnimation(SurfaceTransition.Params)`
- `KeyguardViewMediator.handleStartKeyguardExitAnimationInner(SurfaceTransition.Params)`
- `KeyguardViewMediator.onKeyguardExitFinished(String)`
- `KeyguardViewMediator.cancelKeyguardExitAnimation()` / `handleCancelKeyguardExitAnimation()`
- `KeyguardViewMediatorInjector.keyguardUnlockHide(long,long,String)`

这些事件不保证全都在主线程，也不保证“ExitFinished”就是合成器最后一帧。
小米 `useMiuiKeyguardUnlockHide` 的代码部分内联到合成 lambda，不能仅按日志名称
反射查找同名方法；也不能仅 Hook AOSP Smartspace 分支便认为覆盖了小米解锁。
诊断对非主线程事件只记时间/名称，不读取 View。状态记录发生在原方法执行前，
保留原参数、返回值、异常与解锁完成控制权。

### 桌面 MAML 资源

40130 manifest：`frameRate=0`、`useVariableUpdater=DateTime.Minute`，原组件没有常驻
逐帧定时器。通过 settings trigger 读取锁屏 clockInfo，设置模板、字体与字重。
AllInOne style 1/2/3 有显式分支，其余情况使用资源回退，不能假设任意 AllInOne
字体都与桌面一致。

主要单时区非 classic 时间元素为 `center_classic_plus`，24 小时制格式 `HH:mm`，
居中，size `96*scale`；scale 来自 `view_height/160`。资源内部还有 Group 偏移、
文字高度补偿和附属信息布局，只有宿主格子矩形不能推导精确时间边界。

现有模块的 widget inventory 只列身份，兼容状态 socket 只提供安装状态；
二者都未提供实时 MAML 数字边界和定向隐藏能力。

## 诊断包与性能边界

入口：锁屏 → 锁屏时钟 →「锁屏到桌面时钟过渡诊断」，默认关闭。
仅 SystemUI 增加 2 个生命周期观察点与 6 个低频解锁观察点；桌面新增 Hook 为 0。
生命周期中最多弱引用 8 个时钟，不强持有 View/Activity/Context。关闭时解锁回调
仅检查现有内存设置并退出；开启时每个进程最多 64 条日志，不记录时间文本、
通知正文或应用内容，不在绘制/触摸/动画帧回调中输出。

没有逐帧 Hook、轮询、截图、磁盘读写、新 IPC 或常驻计时任务；不修改任何系统
时钟属性、窗口、动画完成回调或认证状态。构造 Hook 元数据只在安装阶段执行。
以上是代码审查和结构性边界，非真机帧率/功耗测量。

## 下一次真机验证（必须由用户手动触发）

1. 用户手动安装诊断 Release，重启 SystemUI；开启诊断设置。
2. 保持横向 AllInOne 和默认经典桌面时钟，手动解锁返回桌面两次。
3. 用户手动锁屏后解锁返回应用一次，再做一次解锁取消/打断。
4. 只读收集 `adb logcat -d -s MHMClockTransition`，检查事件顺序、主线程、
   sources 数目与矩形在何时消失。没有证据前不延迟系统完成、不改原时钟 alpha。
5. 开关关闭后不再输出日志；其它锁屏时钟、竖排布局不接管。

离线契约检查：
`python3 -B tools/analysis/check-clock-transition-contract.py reference/systemui-4.0.2.0`
验证 13 项成员签名；这不代表运行时注入、内联覆盖和目标几何已经验证。

## 本地交付验证

- `:app:assembleRelease :app:test` 成功，390 项 JVM 测试通过，0 失败/错误/跳过。
- OS4.0.2.0 与本地 OS4.0.4.0 smali 均通过 13 项契约检查；实际设备 SystemUI
  哈希已与参考包核对。运行时回调覆盖尚未验证。
- Release APK v2 签名校验通过：`HyperModifier-v1.5.2-clock-transition-probe.apk`。
- SHA-256：`541a8938ec8ef8411f68b3780753784f2f4955504a04ca7e9c7776ddaa1a2c65`。
- 本轮未安装 APK，未启停/重启任何手机进程，未注入触摸，未修改手机设置。
- 尚未实现动画接管；为满足只读约束，在可人工安装验证的诊断包处交付。

## 第一次真机读取：诊断日志为空

用户确认诊断开关已开启、更新后已手动重启 SystemUI。只读核对安装 APK 的哈希
与首版诊断包完全一致，SystemUI 已是重启后的新 PID。全部 logcat 缓冲区均无
`MHMClockTransition`；因此不能宣称观察器已安装或时钟边界已验证。

已保存的系统日志包含：16:04:20.514 `keyguardGoingAway`，16:04:20.544
`KeyguardService: Starts IRemoteAnimationRunner`，16:04:20.548
`Finish IRemoteAnimationRunner`。后者是框架回调消息，不能将这 4ms 解释为完整
视觉动画时长。静态核对这个日志来自 `KeyguardService$1.startAnimation`。

共享库 `/system_ext/framework/Miui-WindowManager-Shell.jar` 已只读拉取，SHA-256
`b07bdbd819db7f3b2cf8d40d2ffa66b353119b6d020db6c3e121bf11af185cad`；DEX 中有
`SurfaceTransition$Params`，排除实际系统包完全缺少该类型，但尚不能证明首版
安装时 ClassLoader 正确解析了所有元数据。

首版存在诊断设计缺口：只有已安装且开关快照为 true 的命中回调才输出标签，
安装失败交给通用诊断，成功无启动标签；无法从空标签分辨安装失败、设置快照
未更新、日志被覆盖、回调未命中等情况。修正版增加：

- 直接记录安装解析、成功/失败与相关配置启停，不依赖原始启动日志保留。
- 通过已有 BackgroundDim/SystemUI dump Hook 输出 `clockTransition` 行：
  安装状态、Hook 数、模块/诊断开关内存值、已启用事件次数、最后事件/时钟数。
  dumpsys 只读取原子/volatile 缓存，不跨线程遍历 View，不新增 IPC 端点或轮询。
- 增加 `KeyguardViewMediatorInjector.keyguardGoingAway()` 和已证实的
  `KeyguardService$1.startAnimation(...)` 两个低频观察点，仍不修改系统结果。
- 每个进程最多 64 条事件详情，桌面新增 Hook 仍为 0；离线契约变为 15 项。

这是补齐可观测性和入口覆盖，不是已经确定首版失败根因。下一次需在修正版
新进程读取 dump 与标签后才能归因。按手机只读约束，本轮不安装或重启。

修正版 `clock-transition-probe-v2`：Release 构建与签名校验通过，390 项 JVM 测试
通过，15 项本地签名契约通过。APK SHA-256：
`c0205dfed579793b58cdc75510aaf962020a90f45faa6b80ac51cdea6358deed`。
新增低频元数据/计数审查完成；未做真机帧率/功耗测量，未验证修正版运行时。

## 16:22 重启后的 v2 真机验证与 16:27 实时采集

只读 SystemUI dump 确认 `state=installed, hooks=10, moduleEnabled=true,
probeEnabled=true, events=29, lines=29, lastSources=2`。首版/旧记录的空 logcat
不能再作为“Hook 未执行”的证据。设备 main/system 环形缓冲均只有 64 KiB；
随后启动主机侧限时 logcat 流，用户自行锁屏/解锁，成功捕获下列事件。
不修改手机日志大小、过滤配置、设置或进程。

以 16:27:05 的一次完整回调链为例（相对第一条的 uptime 差值）：

| 相对时间 | 回调 | 线程与时钟状态 |
| --- | --- | --- |
| 0 ms | Injector.keyguardGoingAway | 主线程，2 个 horizontal=true、shown=true 实例 |
| +12 ms | KeyguardService$1.startAnimation | 非主线程，未读取 View |
| +13 ms | Mediator.startKeyguardExitAnimation | 非主线程，未读取 View |
| +22 ms | Mediator.handleStartKeyguardExitAnimationInner | 主线程，实例仍 shown=true |
| +74 ms | Injector.keyguardUnlockHide | 主线程，实例仍 shown=true |
| +74 ms | Mediator.onKeyguardExitFinished | 主线程，实例仍 shown=true |

另两次回调链出现不同状态：

- 16:27:08：GoingAway 时 shown=true，+66 ms 的 inner 已 shown=false，
  +322 ms ExitFinished 时仍为 false。
- 16:27:10：GoingAway 时 shown=true，+70 ms 的 inner 已 shown=false，
  +338 ms ExitFinished 时仍为 false。

因此原型必须在 GoingAway 时、系统改变可见性前准备显示状态；不能依赖 inner
时原时钟仍可见，也不能将这些时间差当成完整视觉动画持续时间。新日志同时证实
原有 mediator start/inner/exit 观察点能命中，不能将之前无日志归因于它们必然失效。
`KeyguardService$1.startAnimation` 也在解锁链以外多次出现，不能单独用它启动时钟
过渡，需要核对 transition 类型、目标 home task 和会话取消/结束。

两个候选实例的全局可见矩形重合（例如 `[61,132,1159,1299]`），但子项不同：
一个 hour=null、minute 可见；另一个 hour 可见、minute 不可见。静态存在
`AllInOneHourClock`、`AllInOneMinuteClock` 与 secondary controller，当前日志没有
具体类名/实例角色，所以不能判定这是两套重复完整时钟，更不能任意丢弃一个。
这也说明现有 View 矩形不能用作小时/分钟的字形边界。

下一步锁屏侧实现准备：在一次 GoingAway 内解析候选的具体类型和角色、
通过 `TimeView.getTextBoundsWithPosition()` 配合完整父变换取得字形边界，
验证是否需要组合小时/分钟两路。缓存方法在安装阶段完成；不新增 onDraw Hook，
不在 Binder 线程读取 View，不改变系统认证或退出完成时机。
桌面真实数字目标和定向隐藏仍未解决，动画接管尚未实现。

本轮全部为只读取证与静态分析；无 APK 安装、重启、触摸或设置更改。

## 锁屏侧字形准备原型（GoingAway 单次捕获）

原生 `AllInOneHourClock.getClockInstanceName()` 返回 `HOUR_CLOCK`，
`AllInOneMinuteClock` 返回 `MINUTE_CLOCK`；它们继承同一个 AllInOneBase，所以
既有观察器可同时看到两路。新日志记录具体类型与角色，角色不根据重合的 View
边界猜测；未知类型保留 UNKNOWN 标签，不宣称可接管。

复核 TimeView.onDraw：

- 正常分支：`mTextPath` 在 `(mDrawTextX,mDrawTextY)` 处绘制。
- 冒号淡出分支：当 `mColonSplitIndex>0 && mColonAlpha<1`，先按
  `fontAlpha*mColonAlpha` 绘制 `mColonPath`，再额外平移 `mColonAdvanceWidth`
  以 `fontAlpha` 绘制 `mMinuteOnlyPath`。不能只复制完整 mTextPath，否则隐藏的
  冒号会重新出现，或分钟位置错误。

新增 `ClockGlyphSnapshot` 仅在主线程 GoingAway、系统改变可见性之前复制
最后一次原生绘制生成的 Path；按上述分支分别 offset 后执行
`View.transformMatrixToGlobal`，再计算各路径的屏幕坐标几何边界。
它保留全局可见矩形作为单独 clip 信息，不把父容器边界当成数字边界。
注意：这是字形几何边界，非阴影/玻璃渲染范围；可见矩形也不等同于完整非矩形
遮罩。View 全局矩阵不包含随后由窗口合成器施加的远程 Surface 变换。

快照最多 32 条路径，只持有复制的 Path、矩形、角色、alpha/材质标志等数值，
不持有 View、Context、Paint/Shader 或反射 owner；不改变原路径、Paint 或布局。
保留玻璃标记以避免未来把普通路径绘制误当成完整玻璃材质复现。字体刚更新但
尚未绘制时，捕获的是最后绘制路径，不能据此宣称与当前未提交状态完全一致。

目前只准备几何，不宣称 replay-ready，不创建可见 Overlay，不隐藏系统时钟，
不改变解锁完成回调。路径在退出完成/取消、源 detach、设置关闭时释放；另有
仅活动会话排队一次的 5 秒超时释放，既有会话结束会移除此任务，无常驻轮询。
异步结束操作按 generation 匹配，避免旧会话清理误删新会话快照。

诊断可持久查询（仅进程内存）：复用既有 dumpsys，新增 `lastCapture` 汇总及
最新 16 条事件（每条最多 3072 字符），从而不依赖 64 KiB logcat 缓冲保留。
事件历史仅在少量生命周期回调加锁；dump 复制后先释放锁再打印，不让打印 I/O
阻塞事件写入。不存时间文本、通知内容或手机应用清单。仍限制每进程 64 条
详细事件；关闭诊断时不执行几何捕获。

新增主机单元测试覆盖：原生冒号隐藏/渐隐与分钟分离、正常完整路径策略、
非法/不可见状态拒绝、两种独立角色、环形覆盖顺序/大小限制及并发读取一致性。
这类测试不执行 Android Path/Matrix；真正的坐标映射和原生字形一致性须真机验证。
离线契约包含新读取字段，共 27 项。新增 onDraw/桌面 Hook 为 0，总 Hook 仍为 10。

下一次人工安装后，用户只需正常锁屏解锁；只读 dump 即可看到 `lastCapture`：
实际类型、HOUR/MINUTE/SINGLE 角色、路径数量、真实字形矩形、材质与 alpha。
取消/结束后应显示 `released:...`，路径已释放，但纯数值汇总和有限历史仍可读取。

字形准备 v3 本地交付：Release 构建与 APK 签名校验通过，399 项 JVM 测试通过；
27 项字段/方法契约分别在本地两份匹配 SystemUI smali 中通过。
APK：`HyperModifier-v1.5.2-clock-glyph-probe-v3.apk`，SHA-256：
`2d45ade998fbdb15b246bbc4901fcfbe89b1c3481f7af5a8a24d3ade035291ec`。
本轮未安装到手机，实际字形边界、角色组合和会话释放仍待人工安装后的只读验证。
没有进行真机 CPU/分配、帧率或耗电测量，不声称性能已有提升。

## v3 一次性字形准备的真机结果

只读 SystemUI dump 确认：`installed, hooks=10, moduleEnabled=true,
probeEnabled=true, events=22, lines=22, lastSources=2`。
`captureState=released:onKeyguardExitFinished`，保存的纯数值汇总为：

| 来源 | 字形屏幕边界（px） | alpha | 玻璃标记 |
| --- | --- | --- | --- |
| AllInOneHourClock / HOUR_CLOCK/hour | `[135.60576,563.50714,508.98404,1236.1442]` | 1 | true |
| AllInOneMinuteClock / MINUTE_CLOCK/minute | `[545.2754,563.50714,1079.1239,1236.1442]` | 1 | true |

两者顶/底一致、横向不重合，联合几何边界约为
`[135.61,563.51,1079.12,1236.14]`，大小约 `943.52×672.64`。
这证实日志中的两个实例是小时/分钟分路，不能把重合的 View 容器当成重复时钟。
minute 路径可能包含冒号，路径数量本身不能推断当前冒号样式；不能据此擅自补冒号。

有限历史保留了准备、内部退出和结束前的 `preparedGlyphs=2`；dump 显示结束后
已释放。这一次保留的链中源 View 仍 shown=true，因此验证的是跨回调保留与
结束释放，尚未验证本版在源隐藏时的实际视觉复现（它也尚未绘制任何内容）。
例如 uptime `17024909 → 17024962 → 17025051`：准备、inner、结束入口，相差
0 / 53 / 142ms。这些仍是回调时差，不是动画逐帧持续时间。

**下一阶段材质约束**：两路均 `glass=true`，路径快照只证明几何准备成功。
普通 Canvas.drawPath 不会保留原生玻璃采样/RenderNode 状态，也不能把复制 Paint
当成完整材质迁移。原型下一关应先验证 SystemUI 临时承载能否正确复用原生
TimeView 的材质初始化与背景采样，再决定是否支持当前玻璃样式。未验证前
不展示伪装成完整复现的纯色时钟，不延长系统认证/退出完成过程。

下一关还需确认临时承载层生命周期、解锁目标确为桌面以及 MAML 数字的实际
目标边界；目前未增加桌面 Hook。本轮未安装、重启、触摸或修改手机设置。

## v4 原生玻璃材质独立承载实验

新增独立开关「时钟材质承载预览」，默认关闭，且依赖过渡诊断开关。启用后只尝试
一次；需再试时手动关闭再开启。它不是连续动画：只在解锁回桌面后，于保存的
锁屏时钟位置显示副本 0.5 秒，用于验证新窗口里的玻璃采样和原生字形。
不把测试副本移动到 MAML 位置，不宣称已经完成一镜到底。

### 为何复用原生 TimeView

当前匹配 SystemUI 中，TimeView.onDraw 自行应用字体轴、生成路径、设置玻璃
Paint 效果、字形裁剪，并调用 MiuiBlurUtils.setMiGlass 将 glassData 绑定到 View。
AllInOneBase 还通过 `setGlassEffectMethod(View,float[])` 初始化原生材质类型、
View blur mode 与 surface color type。普通 Path/Bitmap 不能替代这些状态。

实验因此通过缓存的原生 TimeView 构造器创建独立副本，复制相同的时间字符串、
字体/大小/字重/宽高参数、冒号参数、Paint 与 glassData，并调用原生材质初始化。
真正绘制继续走该类原有 onDraw。没有新增 onDraw Hook，也没有调用原 View.draw
或把原 View 从系统容器移出，不共享可变 Paint/数组，不改原 View alpha/可见性。
复制 Paint 可能保留其底层字体/Shader 资源，仅保留在有界实验会话内，不能把此
机制当成长期缓存。

### 时序与门控

1. 首次显式开启时，在现有设置回调中解析并缓存材质/任务元数据；未开启不做
   这批反射解析。缺少任意必要接口时报告 unavailable，不尝试纯色降级。
2. GoingAway 只复制数值、Paint、数组、矩阵和矩形；只接受已识别的一对小时/
   分钟实例及主显示。原 View 只保留弱引用，Context 仅保留 application context。
   原生 View 构造、WindowContext 注册和 addView 都移到系统退出回调完成之后。
3. 复用既有 RemoteTransition 观察点，在 Binder 线程只检查 TransitionInfo 数据。
   要求 going-away flag `0x100`、opening/to-front mode 1/3、activityType HOME=2、
   topActivity 为 com.miui.home、task user 与本进程用户匹配。元数据不明则跳过，
   不同步查询 ActivityManager、不访问 View。当前门控保守地只支持 SystemUI
   进程所在用户，不能据此宣称支持所有多用户切换。
4. 原 ExitFinished 返回后仅排队一次 16ms 的显示尝试；原控件仍可见且累计 alpha
   大于 0.01、旋转非 0、会话超过 1 秒或目标不是桌面，均放弃，不延迟系统完成。
   此条件是保守防重影门控，不证明任何合成器帧已完全结束。
5. 使用与显示绑定的 WindowContext 创建一个透明、硬件加速、不接收触摸/焦点的
   SystemUI 内部窗口（TYPE_STATUS_BAR_SUB_PANEL=2017），不新增权限或 Binder 端点。
   addView 被系统拒绝则记录并退出，不改系统安全策略或回退到其他窗口类型。
6. 副本按源全局矩阵放置，扣除新窗口真实屏幕原点，并使用保存的可见矩形裁剪。
   附加玻璃层/截图层为 0，新增逐帧动画驱动为 0；原生玻璃自身 GPU 成本仍存在。
   新的透明窗口本身需要合成缓冲，不能声称无 GPU/内存成本。
7. 仅活动会话有一次 1.5 秒准备 watchdog；显示成功后改为一次 500ms 移除任务。
   取消、新锁屏过渡、配置变化或设置关闭也会清理。没有轮询/重试直到成功。
   异步清理、显示按 candidate 身份匹配，防止旧任务干扰下一次会话。

普通 JVM 测试只覆盖目标与显示门控、默认关闭和既有回归，未执行 Android 窗口、
原生 Shader 或硬件渲染。成功提交原生 draw 也不证明材质视觉等效，须另做真机
视觉对照。临时窗口和两个副本可能带来启动/绘制/合成成本，本轮只有结构审查，
没有 CPU/分配、Perfetto、FrameTimeline 或功耗对照测量。

### 额外只读框架核对

从当前手机只读拉取 `/system/framework/framework.jar`，SHA-256：
`bfe477943ca7c58ea151897b2498f705ec4e9470ae727b6aa7d18305d342f5b8`。
DEX 确认以下成员存在且 public：TaskInfo.userId:I / getActivityType()I，
TransitionInfo.getFlags()I / getChanges()List，Change.getMode()I /
getTaskInfo()ActivityManager.RunningTaskInfo。它们虽未全部暴露在公开 SDK stub，
但本实验缓存反射元数据，不按 Android/桌面版本号猜测是否存在。
SystemUI smali 契约检查增至 44 项（原生构造器、字段与材质初始化入口）。

### 手动验证与后续只读采集

用户安装本地 v4 后手动重启系统界面，保留过渡诊断开关，并显式开启新的材质
承载预览开关。手动锁屏解锁返回桌面一次。预期可见原锁屏位置的大时钟短暂
出现后自行消失；不会移动到桌面组件。若门控未通过则可能完全不出现，不能
据此认定玻璃不可用。只读 dumpsys 会保留 materialPreview 的 metadata/state、
目标是否 home、shown/nativeDrawn 计数及 lastResult。视觉上需确认字体、材质、
颜色、采样背景和位置；记录为未经验证，直到用户反馈或录屏可供核对。

本轮手机仅执行 framework.jar 拉取；未安装、重启、触摸或修改设置。

### 本地交付验证

- `:app:assembleRelease :app:test` 成功；402 项 JVM 测试通过，0 失败。
- SystemUI 静态契约检查 44 项通过；新增 Hook 为 0，仍复用 10 个诊断观察点，桌面 Hook 为 0。
- Release APK v2 签名验证通过：`HyperModifier-v1.5.2-clock-material-preview-v4.apk`。
- SHA-256：`f6d24d82480a362c6de1ff0104b2e90e1f839e2fdc452d22318b457d46d378c8`。
- 以上属于构建、JVM 与静态验证；尚无本实验的真机窗口/材质视觉或性能测量。

## v5：补齐原生玻璃背景容器（2026-10-21）

用户视频 `VID_20261021_174613.mp4` 中，副本出现后字形带有鲜艳的桌面图标颜色；
用户确认可见约 0.5 秒后清理。只读 SystemUI dump：metadata=ready、shown=4、
nativeDrawn=4、closed:timeout、target home=true。计数为进程累计，不能据此推断帧率。

静态追踪发现 v4 仅复制 TimeView 字形材质，缺少 AllInOneBase /
MiuiBaseClock2.updateViewBackgroundBlurContainer 的原生采样容器链路。
v5 为同一对字形共享现有实验 Host，使用缓存的平台 Method：
setPassWindowBlurEnabled(true)、setMiBackgroundBlurMode(1)、
setMiGlassBlurRadius(radius,radius)、disableMiBackgroundContainBelow(true)，
并给每个字形 chooseBackgroundBlurContainer(host)。半径按当前 TimeView 的
clockStyleInfo.glassTransparency 使用系统公式 int(10+40*t)；不保存原 style 对象。
半径不一致、数值非法、接口缺失或调用失败均关闭本次预览。

此次只修正原生配置缺口，不能由视频与静态追踪证明它是全部视觉异常的根因。
跨窗口背景采样预期包含实际桌面内容，与锁屏壁纸背景不同；修正后仍需用同一
壁纸/位置对照确认模糊、色彩、折射和坐标，不以“颜色不同”单独判定采样错位。

性能范围：未新增 Hook、轮询、截图、动画驱动或额外窗口。新增元数据仅在显式
开启实验后缓存；源字段每次有界准备读取，容器方法只在显示时调用一次。
新增共享采样容器可能增加 GPU 模糊/采样成本，尚无真机性能前后对照；不得
宣称帧率或耗电改善。窗口移除沿用 v4 的 500ms/取消/关闭设置清理边界。
手机仅执行只读 dumpsys，未安装或操作手机。

### 视频暴露的时序限制

原时钟仍随系统解锁缩小淡出，副本在 ExitFinished 后才显示；这是 v4 的材质
实验时序，v5 保留它用于单变量对照，不能称作连续过渡。后续必须在原时钟
开始退出时准备并交接显示，协调原控件的可见性，避免原时钟和副本分别播放。
原值恢复、取消返回锁屏、设置关闭及其他模块共同修改的边界需单独设计；
目前不通过提前隐藏原 View 来掩盖材质问题，也未接入未经验证的桌面目标坐标。

### v5 本地验证与交付

最终 `:app:assembleRelease :app:test` 成功（1m7s），402 项 JVM 测试通过；
两份本地 SystemUI smali 基线的静态契约均为 51 项通过。Release v2 签名通过。
APK：`HyperModifier-v1.5.2-clock-material-sampling-v5.apk`，SHA-256：
`1529750ad6e34e487fc8fcd272ae695e3a2d0f609814275b7d009c4dffae4ce7`。
尚未安装到手机；本次测试未执行真实 Android 原生采样调用及合成。

手动安装后重启系统界面，保持过渡诊断，关闭再开启材质承载预览，解锁一次。
对照 v4 观察字形内颜色、模糊/折射、采样位置和是否意外影响整个背景；仍是
解锁结束后出现 0.5 秒。下一阶段的时序交接不属于此包。

## v6：自动重复预览与窗口附着修正（2026-10-21）

用户要求无需每次开关预览，并反馈 v5 不显示。当前手机只读 dumpsys：
`enabled=true metadata=ready state=closed:window-IllegalStateException:pass-window-refused
shown=0 nativeDrawn=0 target=going-away home=true lastResult=sampling-radius=10`。
因此这次是代码在初始化阶段提前清理，不是用户未开启预览。

对当前 framework.jar 的 View.setPassWindowBlurEnabled(Z)Z 反汇编确认：
没有 ViewRootImpl 时会设置 mLateSetPassBlurEnabled / mLateEnable，然后返回 false；
已处于请求状态时也返回 false。v5 把 false 等同失败不成立。
v6 将配置移到 Host.onAttachedToWindow，调用后用缓存的
View.mNeedPassWindowBlur 字段核对最终启用状态。接口/字段缺失仍失败关闭，
不绕过平台允许列表。初始化完成前不绘制子字形，失败先隐藏再异步移除，
避免在 ViewRoot 的附着遍历里同步移除窗口。采样成功后才启动 500ms 展示期限。
这是针对已确认错误语义的修正，实际玻璃输出仍待真机验证。

按用户要求移除独立“时钟材质承载预览”设置及仅一次 attempted 限制。
材质预览随现有过渡诊断启用，每次符合条件的解锁准备一个有界会话；旧设置
存储键不再读取，避免升级后保存的 false 阻止自动启用。诊断默认仍关闭，当前
用户已开启的诊断保持有效。关闭诊断/模块仍清理窗口。已有会话期间不会准备
第二份，没有轮询或失败后主动重试；下一次解锁由系统事件重新触发。

性能审查：自动重复会增加每次解锁的窗口/副本/采样成本，未测量帧率或功耗。
诊断日志达到既有预算后，重复预览只准备所需原生参数，不再复制诊断用 Path、
拼接字形汇总或安排诊断快照的 5 秒过期任务；材质仍无绘制 Hook、逐帧反射、
IPC 或截图。平台采样状态每个窗口附着时读取一次。测试不覆盖原生窗口附着。
本轮 ADB 仅执行只读 dumpsys；未安装、触摸、重启或修改手机设置。

### v6 本地交付

最终 `:app:assembleRelease :app:test` 成功（2m46s），402 项 JVM 测试通过，
0 失败/错误/跳过；SystemUI 静态契约 51 项通过，当前 framework 的附着延后
行为与 mNeedPassWindowBlur 字段已离线核对。无新增 Hook，桌面 Hook 为 0。
APK：`HyperModifier-v1.5.2-clock-material-auto-v6.apk`，v2 签名通过；SHA-256：
`d9591cb94a1b0be3dec4f73f1997fe3e101baa510dbbe33652dbb1b7a97df43d`。
未安装到手机，窗口附着与采样效果/性能仍待用户手动验证。用户安装并重启
系统界面后，现有过渡诊断已开启即可直接解锁测试，无独立预览开关。

## v7：提前交接与原时钟恢复（2026-10-21）

用户确认 v6 已显示且玻璃采样正确，授权进入下一步。当前阶段只验证交接，
不猜测 MAML 的目标坐标，不宣称已完成一镜到底动画。

- 在已有 RemoteTransition.startAnimation 观察点识别当前用户的 opening HOME
  后，立即向主线程排队创建同一原生材质窗口；不再等 ExitFinished 才创建。
  Binder 回调只读取 TransitionInfo，仍不读取/更改 View。
- 窗口附着后沿用已验证的采样初始化，注册一次首帧提交回调。回调只排队到
  主线程：确认同一会话有效后，给源 TimeView 取得 transitionAlpha 的短期控制权。
  源普通 alpha、可见性、缩放、时间、父容器及系统动画不改；不移走原 View。
- 只接管 transitionAlpha 恰好为 1 的源字形；写入 0 后回读确认。此前已有淡出
  或其他模块控制则拒绝接管。多字形任一失败，清理副本并恢复已经取得的字形。
- 原 ExitFinished 返回后，只对副本做 160ms alpha 淡出，然后清理并恢复源值。
  若系统完成时首帧尚未交接，则放弃，避免重现“解锁结束后才弹出副本”。
  尚未加入目标位置的平移/缩放，仍是起始位置的交接实验。
- 关闭诊断/模块、解锁取消、新锁屏过渡、源 AllInOne 分离、配置变化或单次
  1.5 秒 watchdog 均清理。比较恢复只在源 transitionAlpha 仍为本模块写入的 0
  时写回 1；其他模块改成不同值时保留。无法区分其他模块恰好再次写入同一个
  0 的情况，这是比较恢复的限制，不能宣称对所有模块组合完全无冲突。
- 透明度控制对象仅弱引用源字形，缓存访问器只持有 Method。普通 JVM 测试覆盖
  取消恢复、重复恢复、外部后续写入、已有淡出拒绝、写入后抛异常的回滚。

### 窗口层级与时序证据

本轮只读 dumpsys window：NotificationShade 类型 2040，mBaseLayer=171000。
只读拉取当前 `/system/framework/services.jar`，SHA-256：
`bfd88ca65771d9e6c4229f9dc30c2b5916c0fe1b310afa77819c637de777a0f8`。
离线 WindowManagerPolicy.getWindowLayerFromTypeLw(IZZ) sparse-switch 解析：
2017 -> 分支 0x65 -> layer 18，2040 -> 0x39 -> layer 17。
沿用现有 2017 窗口，无提升权限/替换窗口类型；层级关系不证明实际策略可见性。
当前 framework.jar 确认 View.getTransitionAlpha()F / setTransitionAlpha(F)V。
首帧提交表示缓冲提交，不证明两个窗口在 SurfaceFlinger 中原子交接；可能存在
一帧重叠或空隙，必须用真机视频验证，不以回调成功代替视觉验收。

### 性能与验收边界

无新增 Hook（仍 10 个），桌面 Hook 为 0。没有新增截图、模糊层、轮询或常驻
定时器。提前窗口构造/采样配置仍在主线程，每次解锁一次；把它提前到原生
退出阶段可能影响解锁首帧，需要后续同条件 Perfetto/FrameTimeline 对照。
新增一次提交回调和一次 160ms ViewPropertyAnimator 淡出；绘制循环仍无模块
反射/IPC。transitionAlpha 每个源字形只在准备、交接、清理处通过缓存访问器
读取/设置，不在动画帧里强写或观察。清理窗口发生 RuntimeException 后仍尝试
所有源字形恢复。当前只有结构审查与 JVM/静态验证，没有性能改善结论。

手动安装/重启 SystemUI 后，保留已开启的诊断，直接解锁即可。观察大数字是否
在系统退出期间保持原位置，结束后淡出；不应在退出完成后重新弹出。也需手动
验证取消/锁回去之后原时钟可见，以及关闭诊断后恢复系统动画。只读 dumpsys
新增 handoffs 累计计数、handoff-delay-ms 与 exit-after-handoff-ms，以核对交接
早于完成。所有视觉和 Android 生命周期验收目前均待真机。

### v7 本地交付验证

最终 `:app:assembleRelease :app:test` 成功（1m4s），405 项 JVM 测试通过，
0 失败/错误/跳过；SystemUI 静态契约 51 项通过，框架方法与窗口层级离线核对。
Release v2 签名通过；APK `HyperModifier-v1.5.2-clock-handoff-v7.apk`，SHA-256：
`7d2f2f37f6aac7e0953f6d177d4d4171201f14c29cf78662a9e4c78c0497c293`。
本轮手机只读执行窗口 dumpsys 与 services.jar 拉取；未安装、重启或触摸手机。
未测试真实 GPU 首帧提交、窗口策略可见性、源值恢复及动画视觉，也无前后性能
对照；以上仍需用户手动安装后验证，不能由 JVM 结果推断已实现无缝交接。

## v8 透明预备与接管透明度修正（2026-10-21）

用户视频 VID_20261021_182911.mp4 及后续箭头标注说明问题是先淡后亮，不能仅
按是否存在完全空白帧判断。30fps 摄像不能测量更短的显示空隙。当前只读 dump
最后一次成功会话记录 handoff-delay-ms=27 / exit-after-handoff-ms=66；不能
证明就是该视频同一次解锁。较早快照的 alpha 在接管时恢复，会有亮度回弹风险。

把窗口构造/附着/原生字形准备提前至 GoingAway 后的单次主线程排队任务；
不阻塞原 GoingAway 调用继续执行。窗口初始 alpha=0，不隐藏源、不接收输入。
仍需 RemoteTransition 批准返回当前用户 HOME，才安排一次 OnPreDraw 激活。
激活前读取源自身/父链的普通与 transition alpha，以及 TimeView.fontAlpha；
副本 View alpha=min(初始值,当前有效 View alpha*当前 fontAlpha/快照 fontAlpha)。
已不可见、完全淡出或无效的源直接放弃，避免重新弹亮。提交回调只注册于首次
可见帧，不能把透明预备帧误认为可见交接。仅激活这一帧读取缓存元数据，后续
动画帧无反射/父链遍历。窗口成本和目标到达时序新增进程内诊断字段。

本地 assembleRelease/test 已通过，408 项测试，透明预备不等于批准显示、
普通/字体透明度补偿及不可见/无效源不回闪均有 JVM 回归。尚未经真机验证，
也不能声称两个窗口已实现合成器原子交接。预备可能增加一次不可见窗口绘制，
且返回应用时也可能短期创建透明窗口，仍在 1.5 秒期限内清理；无常驻轮询。

用户要求本轮直接实现 MVP，并明确接受“当前桌面校准版”，而非必须自动定位
和隐藏桌面原字形。因此下一提交允许使用当前桌面实测终点，限定分辨率/密度，
末端重合淡出；桌面原时钟仍可见，其他页面/布局不作准确性承诺。

## v9 当前桌面校准版 MVP（2026-10-21，离线完成，待真机验收）

用户明确接受当前页面校准版，并追加仅桌面第一页触发、仅去掉时钟本身解锁
入场动画。手机断开后没有执行任何 ADB 操作；本节实现结果不能当作真机验收。

### 轨迹和交接

断开前用户手动回桌面后，只读截图实测 1220×2656、520dpi。时间字形可见区域
为 (236,468)-(988,699)，不含日期/天气。ClockMvpGeometry 拒绝其他显示尺寸、
密度、无效/越界源字形；不是自动布局识别。起点使用 TimeView 实际字形 Path
包围盒加绘制偏移和全局矩阵的并集，分隔符/分钟分拆按原绘制策略计算。

保留 v8 透明预备、当前有效透明度上限及首个可见帧提交后源字形租约交接。
采样窗口不移动，仅内部 GlyphLayer 的 RenderNode 做 420ms 平移与非等比缩放，
插值 (.22,0,.15,1)。移动完成且系统退出完成后，在校准终点淡出 120ms。
桌面原时钟保持显示，因此途中可能同时看到两套字形；末端是重合淡出，并未
实现任意主题字体形变、桌面字形精准隐藏或 SurfaceFlinger 原子交接。
接管后不因原锁屏 View 分离而中断副本移动；接管前分离仍放弃。取消、新锁屏、
关闭功能、配置变化和 1.5 秒超时继续清理并比较恢复源 transitionAlpha。

### 最小桌面介入与第一页门控

本功能新增 3 个 Dart AOT 叶子 Hook，SystemUI 观察 Hook 仍为 10 个：

- Workspace 当前屏幕 getter 0xa071a8：只缓存具体 receiver 的 class ID，
  不保存堆指针。实际当前页读取 Workspace+103 -> Rx+19 的 Smi。
- UnlockAnimGetxController 状态 setter 0xd6742c：在原 setter 通知之前，
  用当前 isolate 的静态表 +10720 解析 Workspace。校验已观察 class ID，
  发布页索引和递增 epoch 的单个 64 位快照；未初始化/非 Smi/越界返回未知。
- _UnlockWidgetState 入场资格谓词 0x13c8b6c：同时校验本次页快照及实时页索引
  为 0，只对实际 ItemInfoModel id=493、screenId=1、cell=(0,0)、span=(4,2)、
  container=-100 返回 Dart false，跳过这一项准备/播放。其他项直接走原方法。
  id 来自只读桌面 dump；appWidgetId=101390060 是不同字段，不能混用。

原调用点 0x1431a34 先执行 reset(0x142ffd8)，把该项透明度/缩放恢复为 1、位移
恢复零，再于 0x1431a7c 检查资格；false 分支跳过准备。0x1431b34 的相同谓词
false 分支跳过播放。未修改全局解锁动画时长、其他图标、MAML 绘制或存储数据。
叶子汇编不调用 C/Dart，不跨 Dart 调用建立未登记栈帧；只在短暂 x15 栈空间
保存/恢复 x9..x14 和 NZCV，x16/x17 是已核对的临时寄存器，其余寄存器/SIMD
不改。关闭时只有廉价启用判断和原 trampoline 跳转。

使用当前实际 libapp.so 的完整文件指纹及 3 个入口指令双重校验；不是桌面版本号
比较。当前文件 SHA256 6f5274f435cc07aab165ce5315d1d35afab559a659afd6200379d309c9d08b91，
大小 29983600，FNV-1a64 278dda665a7e3677。未知二进制保留系统动画。本 MVP 的
能力范围刻意限定已校准设备，不声称适配任意桌面更新。Hook 安装单次尝试，
部分失败回滚；沿用原安装 worker、库通知去重和 eventfd，不新增轮询线程。
设置只在此功能值变化时唤醒安装，重复载入或其他设置刷新不反复安装。

原设置桥通过公开 sender package 验证 com.android.systemui 后才接收 MVP
开关和实际 UID。现有只读 status socket 额外允许这个同用户已认证 UID；原
system UID 权限保留，不向任意应用开放。只回传 primitive 状态，不接受命令。
SystemUI 校验服务端实际 launcher UID。准备与 HOME 远端启动时各做一次后台
查询，要求新的 epoch、当前页 0，以及目标时钟跳过计数增加后才显示副本。
查询缺失/过早/旧值/不匹配会跳过，不凭 HOME 窗口猜测第一页；dump 保留前后
快照。首次启用时若 Workspace class 尚未被观察，本次不接管，避免准备与播放
中途改变判断；后续解锁再尝试。时钟移动/重加导致 id 或位置改变后需要重新校准。

### 性能检查与验证边界

- 高频资格 Hook 无 IPC、反射、日志、动态内存、锁、系统调用、Dart getter 调用；
  只有固定字段读取、身份/布局检查和 primitive 更新。元数据不强引用 View/Context。
- 新增后台查询每个候选最多 2 次，去重远端回调；单工作线程、最多 4 个排队任务、
  空闲 5 秒释放线程。AF_UNIX 使用非阻塞 connect，读取 timeout=80ms，响应上限
  2048 字节，过载直接未知。当前 framework 的 LocalSocket timeout-connect 重载
  实际抛 UnsupportedOperationException，故未使用该重载；getInputStream 初始化
  fd 后 fcntl 切换非阻塞，connect 成功再恢复阻塞与读取超时。
- 文件指纹扫描只在功能开启后的安装 worker 单次执行；新增约 30MB 文件读取
  和 FNV 运算。主线程仍有每次候选的 TimeView 构造、复制、附着和一次采样配置，
  及一帧透明度核对；窗口预备可能触发不可见绘制。没有新增模糊/截图层，但不
  能把复用原生玻璃等同于没有 GPU 成本。只让 RenderNode 移动仍需真机确认
  采样坐标、材质、字形缩放与原桌面时钟重合程度。
- Mac ARM64 实际执行生产叶子汇编，768 个组合覆盖寄存器、NZCV、全部 SIMD、
  第一/其他页、未知/未初始化状态、非 Smi、不同 item/位置/尺寸/容器。只改
  ELF/Mach-O 符号与 relocation 拼写，没有执行 Dart VM 或 Hook 后端。
- 原生安装器 ASan/UBSan 验证完整镜像/入口、占用/缺 trampoline、部分回滚、
  实时关闭和 10000 次重复安装不重复调用后端。原安装器回归另覆盖 80000 次
  并发库通知；旧搜索汇编 4608 用例、小组件选择器 96 用例保持通过。
- 这些是静态/本机 ABI/单元测试与结构审查，没有 Perfetto、FrameTimeline、
  帧率、卡顿、内存驻留或功耗前后对照。不能声称已无缝或性能改善。

安装 Release 后需用户手动重启系统界面与系统桌面，或重启手机；沿用已开启的
时钟过渡诊断，无新增开关。验收：第一页横排时钟完整移动到小组件；其他页与
返回应用不播放；目标时钟不再从外侧甩入，其他图标仍正常；取消/锁回/关闭功能
后原时钟可见。新增桌面接口、跨进程时序与最终视觉均待真机验证，早于查询的
解锁状态可能使本次保守跳过，不能以本地测试成功替代手机验证。

v9 交付构建：`:app:assembleRelease :app:test` 成功（2m8s），415 项 JVM 测试
0 失败/错误/跳过；SystemUI 契约 51 项通过。Release v2 签名通过，文件
`HyperModifier-v1.5.2-clock-mvp-v9.apk`，SHA256：
`4014620c3776b90c530aa4a87804c2c41b8b76461650805034d74d1ef82f49f4`。
新 APK 未安装到手机；用户已明确授权通过飞书 CLI 向本人交付测试包及说明。
