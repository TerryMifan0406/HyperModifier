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
| 2 | 仅 SystemUI 的默认关闭诊断包 | 记录横向时钟边界及解锁事件顺序，不改画面 | 本地实现，真机待验证 |
| 3 | 锁屏侧连续过渡原型 | 在确认窗口生命周期后，复用数字显示路径，验证独立横纵缩放和取消恢复 | 待阶段 2 真机证据 |
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
