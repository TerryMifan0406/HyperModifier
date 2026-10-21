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
| 3 | 锁屏侧连续过渡原型 | 在确认窗口生命周期后，复用数字显示路径，验证独立横纵缩放和取消恢复 | 一次性字形准备已实现，显示接管未实现 |
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
