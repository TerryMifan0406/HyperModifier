# HyperMusicCover 一镜到底分支定位与独立扩展方案

日期：2026-10-22。需求是锁屏 AllInOneClock → 桌面第一页 MAML 时钟的连续动画，
作为另行安装的个人扩展提供。不是恢复已归档的 HyperModifier 截图过渡 MVP。

## 当前结论和完成边界

**尚未在作者当前公开分支中定位到可确认的锁屏 → 桌面时钟实现。**
不能把音乐封面时钟收缩、锁屏 → AOD 或封面副本退出动画当成目标功能。
没有生成占位 APK，没有恢复旧过渡 Hook，也没有加入一个没有实现的开关。
下一步需要定位实际实现的源码提交或进一步线索；作者原话/讨论链接可以帮助区分
未公开分支、已删除分支、另一仓库和功能语义，但不要求用户知道分支名。

## 公开引用核对

用 `git ls-remote --heads https://github.com/zyl6932/HyperMusicCover.git`
与 GitHub branches / matching-refs 两个公开 API 交叉核对了以下 8 个分支：

| 分支 | 本次读取的 HEAD |
| --- | --- |
| `main` | `00b445ff26332a68d31baad6883c1db4d31fdb4f` |
| `claude/awesome-gauss-d5h8il` | `b8cc1656e4f3988c0aa16318a0324e61c3380514` |
| `claude/zen-cannon-m7khd8` | `8e1058690bd4493dc957d29edf621fe4a9090eb5` |
| `lockscreen-lyrics-v2` | `87733059e178a124ce6352e9ec060de1b782e22a` |
| `notif-numstate` | `233bf5ecb92a1f4ce5e215156d57e7dd55122cba` |
| `shade-cover` | `f49f9aa285af4566cd82e8fd4e8c2604f6af9155` |
| `video-depth-cover` | `8e80f7156655a5443f6b536f174f9b3356326f8c` |
| `wallpaper-video-texture` | `606d15d113b843248607e0c399b383ed96510d5c` |

旧本地上游引用的 7 个非主线 HEAD 均与公开值相同。主线旧本地引用为 `375d1a6`，
因此单独下载了 `00b445f` 的源码快照，避免以过期工作区代替最新主线。
未切换或修改旁边 HyperMusicCover 仓库内的未提交工作。

查阅内容：各分支 Java/文档中的 clock、unlock、launcher、desktop、handoff、
transition、中文一镜/桌面时钟线索，以及可达提交历史中的相关改动；
主线还核对了 ClockCollapse、Main 的解锁/息屏/唤醒入口、模块 scope。
公开 PR 列表本次返回 17 条，未见桌面时钟连续过渡的题目/分支；近期公开活动
未给出对应实验分支。不能据此排除私有、未推送或 Git 已不可达的历史。

扩展查找了作者的公开仓库列表和 HyperTweak 主线目录，未得到目标线索。
公开 forks API 返回 17 个 fork，最初仅 2 个分支列表读取成功，其他读取遇到 GitHub
API 限额。随后经 GitHub 连接器补齐其余 15 个仓库的公开分支枚举，没有发现名称
明确对应目标的分支。这只完成分支枚举，**不代表审过所有 fork 的完整代码或已删除分支**。
逐文件 Git 部分克隆另遇到网络连接失败，最新主线已用提交固定的 codeload 快照
独立读取，因此没有用失败的扫描结果推断主线内容。

## 排除的近似实现

### ClockCollapse 是封面 / AOD 连续姿态

[2a2f3f46a3883cd98bdfd772788447651eaa5a21](https://github.com/zyl6932/HyperMusicCover/commit/2a2f3f46a3883cd98bdfd772788447651eaa5a21)
引入 OFF/ENTER/ON/EXIT/AOD 状态机和一个 spring；读取实时字形 ink box，以等比
缩放及位移映射封面小时钟与原生锁屏/AOD 时钟。它确实解决连续姿态、字体尾帧
与唤醒闪变，但提交及实现没有桌面时钟目标。

最新主线 `app/src/main/java/com/os4/musiccover/Main.java:1678` 在 SCREEN_OFF 且
clockHeld 时调用 `ClockCollapse.toAod()`；其他该广播收尾分支调用
`ClockCollapse.release(a)`。相同 receiver 注册了 ACTION_USER_PRESENT。
`ClockCollapse.release()` 最终交还 OEM 状态，未建立 MAML 桌面终点。

主线的 scope 为 SystemUI、MiWallpaper、AOD、插件及音乐/地图/智能助理/背屏相关
进程，不含 `com.miui.home`。这本身不能证明某种纯 SystemUI 实现不存在，但与
时钟调用链及桌面坐标/目标协议未发现的结果一致。

### 最近的 cover flight 退出不是时钟到桌面

`df8cd54` 和 `a05524a` 处理 square cover / cover flight 随解锁结束；
`fdef039` 处理 held AOD 保留上次绘制姿态。它们不能作为目标分支定位结果。

## 找到实际实现后采用的扩展边界

1. 独立 APK 与独立 applicationId，功能代码/资源仅在该包中；主 APK 不夹带上游
   实现，不使用一个空壳 APK 作为内置代码的解锁钥匙。
2. HyperModifier 的 HMC 扩展页在进入/恢复时异步查询固定包名、协议版本和签名；
   包不存在时不显示新功能入口。“强制启用 HMC”不能绕过该包的安装条件。
3. 优先让扩展自身作为 LSPosed 模块拥有 Hook 生命周期与设置；主模块只提供入口，
   不在 SystemUI 动态加载任意第三方 APK/Dex，不为检测启动常驻服务。
4. 扩展仅按需要声明作用域，默认关闭。先核对上游真实依赖，不能提前承诺只需要
   SystemUI 或一定支持所有布局/字体。桌面第一页、无目标/应用内解锁时的退出条件
   要作为实际功能契约验证。
5. 若与现有 HMC 字体轴扩展、桌面材质有交叉写入，明确唯一 owner、恢复原值及
   关闭/重新锁屏/重建/被打断/AOD/卸载后重启的处理；不同时启用归档 MVP。
6. 定位后固定源码提交，保留上游许可证、署名和修改说明。当前主线自有代码声明
   AGPL-3.0，第三方文件还有各自声明，必须以实际采用的分支/文件为准。
   独立安装与个人使用不是删除上游许可证/署名的理由，也不作“消除版权风险”的保证。
   未经用户要求，不推送源码、不发布包、不发送消息给作者或飞书。

## 性能与验证计划

本轮仅源码调研和文档，不修改运行时或作用域、不安装 APK、不操作手机；没有新增
启动、主线程、动画帧、分配、锁、IPC、GPU 或耗电开销，也没有进行性能测量。

后续移植先审计上游每帧 I/O、反射、全树遍历、截图、常驻轮询和多层渲染，
缓存元数据并保留廉价退出路径。若需要桌面目标同步，优先事件驱动且不逐帧 IPC。
动画期间的 GPU 层/窗口与跨进程生命周期需要相同设备、刷新率、温度、设置及操作
下的 Perfetto / FrameTimeline / 分配对照；源码复用与主机回归不等于真机流畅。

出处：
- [作者仓库与公开分支](https://github.com/zyl6932/HyperMusicCover/branches)
- [已核对的主线提交](https://github.com/zyl6932/HyperMusicCover/tree/00b445ff26332a68d31baad6883c1db4d31fdb4f)
- [主线 Main](https://github.com/zyl6932/HyperMusicCover/blob/00b445ff26332a68d31baad6883c1db4d31fdb4f/app/src/main/java/com/os4/musiccover/Main.java#L1678)
- [主线 ClockCollapse](https://github.com/zyl6932/HyperMusicCover/blob/00b445ff26332a68d31baad6883c1db4d31fdb4f/app/src/main/java/com/os4/musiccover/ClockCollapse.java)
- [主线 NOTICE](https://github.com/zyl6932/HyperMusicCover/blob/00b445ff26332a68d31baad6883c1db4d31fdb4f/NOTICE)


## 用户确认与 Live 图二次录屏（同日补充）

用户明确作者指的是“锁屏 → 桌面”，不是 AOD；并提示作者可能完全没有 Hook 桌面。
后续定位必须以此为目标，不能继续要求用户提供他本来不知道的分支名。
没有桌面 scope 不能排除目标实现：纯 SystemUI 接管、系统已有目标信息或预设/校准
终点都可能不需要桌面进程注入。这些只是候选架构，不是已确认的作者实现。

输入：`/Users/aritxonly/Downloads/SVID_20261022_012335_1.mp4`。
这是相册中 Live 图的回放录屏，画面含“实况”和下载/静音控件。
文件 1196×1920、约 4.331 秒，标称 r_frame_rate=60，但解码只有 144 个源帧，
时间戳不等间隔。分析按实际 PTS，不把插帧后的 60 fps 联系图当作设备帧时间证据。

逐帧看到的现象：

- 约 1.104 秒：细长大时钟，日期在上方，锁屏背景。
- 约 1.194 秒：背景正在变化，时钟仍为细长轮廓，位置/透明度已开始变。
- 约 1.303 秒：数字明显变矮，横向宽度保留得较多，冒号可见。
- 约 1.563 秒：已接近桌面常规 10:08 字形，图标正在各自的入场/回弹中。
- 时钟的字形变化与图标整组入场没有完全绑定；它不是简单保持同一宽高比缩小。

这与“原生字体轴/逐字形变 + 时钟独立的退出时序”相容，也与纯 SystemUI 方案相容。
无法凭录屏确认是原 TimeView、复制的原生字体渲染器，还是桌面侧本来就有的动画；
更不能据此断言使用了特定 HGHT/wdth 字段、跨进程目标发现或某种 Surface 类型。
素材还经过 Live 图回放和二次录屏，短暂空白/透明度不能直接归因于模块。

临时分析帧在 `/private/tmp/mhm-hmc-live-20261022/`，未提交用户视频或截图。
动画对照四帧使用源帧编号 54/58/63/72，对应 PTS 1.104/1.194/1.303/1.563。

同日 GitHub 连接器再次枚举作者 8 个分支，与 Git 及公开 API 一致。
提交搜索 `unlock` / `desktop clock` 返回的候选仍是封面、壁纸或 AOD 变化；
四个较活跃 fork 的 `clock transition` 提交搜索无命中。
公开可读范围内仍未定位视频对应的实现。需要取得该演示对应的源码提交、
测试 APK 或其他实际实现线索，才有依据继续制作可用扩展；不把未定位改写为不存在。

## 无测试 APK 后的构建记录补查与 10 月 15 日线索

用户没有演示版 APK，因此没有继续要求用户找文件。通过只读 `gh api` 读取了
作者仓库全部 396 条公开 Actions 记录（4 页），覆盖 UTC 2026-09-08 19:49:39
至 2026-10-21 20:58:41；本地记录含 299 个不同 head_sha。
与当前公开 Git refs 的可达提交对比后，有 31 个构建 head 不可达；逐个读取了
这些 head 的真实 commit message、文件列表及可用 patch，而非只按 Actions 的
PR 标题判断。候选分别是 Enhanced 的封面/通知修改、AOD 尺寸、歌词/材质及
claude 分支的探针/SDK 修复，未得到锁屏 → 桌面目标的正面证据。
这仍不能排除未运行 Actions、未推送或不在构建 head 上的其他历史代码。

用户进一步指出 Live 图日期是 10 月 15 日。将其作为优先级线索，把公开提交/
构建重点缩到北京时间 10 月 14—15 日。画面日期可能受系统时间或演示设置影响，
因此不是证明源码版本的唯一依据。

已自行定位两个仍公开的 Nightly，不依赖用户提供测试包：

| 产物 | 源提交 | 关联构建 |
| --- | --- | --- |
| nightly-20261014 / `HyperMusicCover-nightly-20261014-9df240d.apk` | `9df240d41130c23b2cad1980c2bf6d209b22b172` | Actions `38637342750`，北京时间 10-15 05:04 启动 |
| nightly-20261015 / `HyperMusicCover-nightly-20261015-375d1a6.apk` | `375d1a607dfc741d052c874268db3c1cadb7b6c6` | Actions `38803625079`，北京时间 10-16 04:38 启动 |

注意：Nightly 标签日期、GitHub Release created_at 和 Actions 实际启动时间并不
完全相同，不能只用标签日期或 Release 创建时间当作 APK 编译/演示时间。
已读取 release 元数据（各 APK 约 11.2 MB），没有下载或安装上述 APK。

若画面 10-15 10:08 反映实际录制时间，前一份 Nightly 是更直接的公开候选。
但核对 `9df240d` 对应源码，Main:1673—1680 在 SCREEN_OFF 时进 AOD、其他该
receiver 收尾调用 ClockCollapse.release(a)，注册了 ACTION_USER_PRESENT；
ClockCollapse:657—658 的 release 直接 releaseNow。其 Phase 仍只有
OFF/ENTER/ON/EXIT/AOD，未得到桌面目标交接的实现证据。因此不能把这个 Nightly
当成视频功能版，也不能仅因日期接近就打包“移植版”。

公开对照：
- [全部 Actions](https://github.com/zyl6932/HyperMusicCover/actions)
- [nightly-20261014](https://github.com/zyl6932/HyperMusicCover/releases/tag/nightly-20261014)
- [该候选的 Main](https://github.com/zyl6932/HyperMusicCover/blob/9df240d41130c23b2cad1980c2bf6d209b22b172/app/src/main/java/com/os4/musiccover/Main.java#L1673)
- [该候选的 ClockCollapse](https://github.com/zyl6932/HyperMusicCover/blob/9df240d41130c23b2cad1980c2bf6d209b22b172/app/src/main/java/com/os4/musiccover/ClockCollapse.java#L657)

本次仍仅调研与文档，无运行时代码、Hook、设置或作用域变化；不运行构建/测试来
冒充功能验证，也无设备操作和性能测量。

## 作者排除主线后：两个 ColorOS 分支的定向审查

用户转述作者明确排除主线、Releases 和 Nightly，并要求重点看 ColorOS 相关的两个
分支。因此后续不再用主线夜版当作演示候选。重点核对：

- `claude/awesome-gauss-d5h8il`，`b8cc1656e4f3988c0aa16318a0324e61c3380514`；
- `claude/zen-cannon-m7khd8`，`8e1058690bd4493dc957d29edf621fe4a9090eb5`。

本轮用认证的只读 GitHub API 重新获取分支 HEAD，仍是此前列出的 8 个分支。
从本地 Git 对象导出全部 7 个非主线分支到临时目录，检查仓库目录、时钟路径、
窗口/Surface 使用及 Probe 入口，而不只按“桌面”“一镜到底”字面筛选。
工作区有其他电池功能的未提交修改，本轮未修改或提交那些文件。

### 两个 ColorOS 分支共用同一份时钟实现

`git diff` 验证两分支的 ClockCollapse.java、LockHold.java 没有差异；
SHA256 前 16 位分别为 `fdb7d63a11d10c96`、`ddf66ae3f1d795dc`。
PageSpring.java 也相同（`382e92f669c0172f`）。Main.java 的差别是 8 行高德
AmapFocus/公交地铁 Probe 入口，ImmersiveHost 多 3 行注册 AmapTransitScene；
不是两套不同的时钟退出实现。

阅读的关键调用链（行号使用 zen 分支快照）：

1. `LockHold.take:68` / `give:83`：COVER 和 IMMERSIVE owner 的并集决定时钟
   是否保持收缩；由 ClockCollapse.enter/exit 接管，不是桌面目标的 owner。
2. `Main.applyY:3161`：通过已缓存的 notifStateChange Method 写入 notifY，
   `isDragOrFling=true` 使 OEM 直接应用当帧值，不额外启动第二套 Folme 动画。
3. `ClockCollapse` 文件说明及 frame/writePose：notifY 让 OEM 改变原生可变字体
   的高度、宽度和字重；姿态由实时 ink box 映射控制。`onGlyphDrawn:1576` 在
   原文字绘制后发现边界变化时重新摆放，避免始终使用上一帧测量。
4. `writePose:1994`：unit/m.unit 得到等比缩放，按 ink top 定位，日期单独跟随。
   但 holdWidth 的字形尾帧还有临时 X 轴收窄，不能概括为所有阶段都严格等比。
5. `Main:3038—3074`：USER_PRESENT 取消封面副本、隐藏 CoverCardLayer，最后调用
   ClockCollapse.release(a)。`release:597` 直接进入 `releaseNow:1453`，停止帧
   回调、移除 pre-draw、清空姿态和 hold、恢复材质；未见桌面终点续存状态。
6. `ImmersiveHost.onUnlocked:525`：启动沉浸页面释放倒计时，不是保留时钟直到
   桌面接手。PageSpring 的 ColorOS 对应关系是沉浸地图/倒计时页面的 Surface
   入场曲线，不能因此当作 ColorOS 锁屏到桌面时钟的一镜到底代码。

同样检查了 anim、oemtrans、hold/release、callclock、clockshot 等 Probe 分支：
分别是配置/位移报告、原生压缩调试、反射调用和设置预览采样开关，未定位专门的
解锁到桌面时钟演示入口。不在手机运行这些命令。

### 可借鉴部分与尚缺的实现

这两个分支提供了有源码依据的“原字体变形 + 实时字形边界跟随”底座，和视频
的视觉特征相容；它们能为后续扩展设计提供依据，不能单凭相容就认定找到演示版。
尚未定位的关键仍是：跨过锁屏窗口退出继续显示时钟、桌面目标的坐标/字体参数、
交接时隐藏或显现的时序，以及取消/重新锁屏/无目标场景的恢复。

性能审查发现：原版 onGlyphDrawn 的 Xp.callMethod/getTextBoundsWithPosition 在
高频路径，WeakHashMap<View, Float> 会对高度装箱；字形变化还可能在同一帧重入
frame。clockRoots 已缓存完整容器对，重建或未解析完整时重查，不能声称每帧都
全树扫描。不能把上游整个 Probe/记录与重绘机制原样搬进扩展，也不能把临时
X 轴收窄当作用户要求的原生字体轴调整。这里只做静态审查，无帧时间或功耗测量。

两个分支本身均带 AGPL-3.0 LICENSE/NOTICE。若实际采用其中实现，独立扩展需保留
对应声明；单独安装和隐藏入口只是技术交付边界，不构成免除授权要求的保证。

固定源码：
- [awesome 的 ClockCollapse](https://github.com/zyl6932/HyperMusicCover/blob/b8cc1656e4f3988c0aa16318a0324e61c3380514/app/src/main/java/com/os4/musiccover/ClockCollapse.java)
- [zen 的 ClockCollapse](https://github.com/zyl6932/HyperMusicCover/blob/8e1058690bd4493dc957d29edf621fe4a9090eb5/app/src/main/java/com/os4/musiccover/ClockCollapse.java#L1576)
- [zen 的 Main.applyY](https://github.com/zyl6932/HyperMusicCover/blob/8e1058690bd4493dc957d29edf621fe4a9090eb5/app/src/main/java/com/os4/musiccover/Main.java#L3161)
- [zen 的解锁收尾](https://github.com/zyl6932/HyperMusicCover/blob/8e1058690bd4493dc957d29edf621fe4a9090eb5/app/src/main/java/com/os4/musiccover/Main.java#L3074)

## 再复查与留档对照：先解决时钟独立接续

2026-10-22 再次只读获取作者全部公开分支：仍为上述 8 个，两个 ColorOS 分支
HEAD 未变。再次比较 ClockCollapse、LockHold、Main、ImmersiveHost，差异仍
只有后两文件的高德 Probe/场景注册，共 11 行。沿两个分支历史搜索解锁事件、
going-away 和 onUnlocked，命中的 12 个提交属于封面、地图、灵动岛、通知等
路径，未定位到能确认对应演示的时钟到桌面实现。这只描述已检查的公开证据，
不推断作者未公开或已删除的分支不存在。

用户指出录屏中小组件时钟的入场与图标不是同一层。将此作为下一轮方案的核心：
**时钟有独立的动画状态和交接时机，桌面图标照常入场。** 录屏支持独立轨迹，
但不能据此辨认它是 Flutter 绘制节点、RenderNode、独立窗口还是独立
SurfaceControl。后续不把“逻辑动画独立”直接写成“已经找到独立合成层”。

### 归档代码确认的缺口

对照 `codex/archive-clock-transition-mvp-v10`（eb7ee93）实际实现：

- v9/v10 的桌面资格 Hook 只跳过目标时钟的入场准备和播放，没有隐藏原桌面
  时间字形。副本在移动时，终点原字形仍可能可见；末端还额外淡出 120ms。
  因此“关闭时钟甩入”不等于完成一镜到底的目标接管。
- v8 的副本首个可见帧提交后才隐藏锁屏源；源仍受原父链的退出透明度影响。
  v10 现场记录 activation-alpha=0.344、handoff-delay=34ms，说明接管点可能
  已在淡出中段。两次 Window 提交没有原子性保证，不能用透明预备窗口宣称
  彻底消除了先暗后亮或显示空隙。
- v10 在候选解锁开始后准备 17 个字体布局采样点，未准备好时副本停留起点。
  这可能让独立轨迹晚于图标动画启动，仍需设备时序确认。
- 字体形变只覆盖 wdth/HGHT、字号和排字位置，保留原 weight 和 colonAlpha；
  终点来自校准矩形与源字体测量，没有读取桌面端实际 Typeface/字重。

这些是实现边界，并非已用对照实验确认的全部视觉问题根因。

### 两端字体不能只凭文件名或矩形等同

基线 `OS4.0.2.0` 的 `com/miui/clock/utils/FontUtils.getAllInOneFont` 从
`/product/fonts/` 加载 AllInOne 字体（例如 MiSansRCF.ttf），不是直接读取
SystemUI assets 中同名文件。TimeView 使用 wght/wdth/HGHT 覆写。

已拉取的小组件资源 40130 中，center_classic_plus 用 `96 * scale` 字号、
`font_path_style_plus` 和 `lock_font_weight`；字重默认 420，部分模板可从
clockWeight 读取。其 zip 内六个字体的 fvar 表均只有 wght（150..700，默认
330），没有 wdth/HGHT。尚未取得这次系统实际 `/product/fonts` 文件的轴表，
不能认定小组件字体与锁屏字体是同一个三轴模型，也不能只按同名推断相同字形。
目标字形是否能通过原生轴精确达到，需先验证实际字体实例和文字的墨迹边界。

### 新 MVP 的验证顺序与交付边界

1. **先做时钟可见性接续。** 维持锁屏端起点亮度，在其公共退出父层影响前
   建立可持续显示的承载路径；过渡中只保留一份可见时间。目标 MAML 时间在
   自身位置等待接手，禁止其随图标甩入。日期/天气等其他内容单独确认，不默认
   隐藏整个小组件。只有“源可交接、返回 HOME、第一页、目标已就绪”同时成立
   才接管；失败时整段保留系统动画，避免只有一侧被隐藏。
2. **再校准终点和同帧接手。** 先让静态终点的字形、冒号、基线、颜色、亮度
   与真实小组件吻合，再实现源→终点的连续轨迹。目标接手应绑定渲染就绪和
   本次会话完成，不能再靠固定延迟后移除副本；分别提交的跨进程操作仍需逐帧
   验证。不用两份半透明文字直接叠加来假定亮度恒定。
3. **最后接入字形形变。** 从真实两端参数决定 weight、wdth、HGHT、字号及
   colonAlpha；位置和整体大小仅等比变化，不用硬拉伸凑矩形。提前按字体、
   尺寸/布局变化准备有限缓存，不在解锁开始后首次求解，也不常驻轮询。

保持原来的横排、第一页、当前经典时钟范围。优先在 SystemUI 管理过渡生命
周期；桌面若没有现成的目标与可见性接口，允许最小的目标协作，不能为了
“零桌面 Hook”放弃第一页判断或留下双时钟。当前 Flutter `_UnlockWidgetState`
有逐项资格判断及 alpha/scale/translation 状态；这能作为单独排除时钟入场的
入口依据，不证明时钟已有可独立重挂父节点的 Surface。是否能只控制 MAML
时间字形而保留整个小组件的其他内容，需要继续沿原生绘制身份核对。

保留独立安装扩展包的交付方向，不恢复旧实验到默认生产路径；不把上游整套
Probe 或旧版 10+3 个 Hook 无差别搬回。动画帧禁止同步 IPC、反射查找、日志、
字形搜索和无界分配；目标信息在状态变化时刷新，清理时恢复原值并处理取消、
重新锁屏、页面切换和目标消失。读屏、文件/字体解析和参数求解不进入主线程
动画回调。

本轮仅静态代码、二进制留档和字体资源检查；未改运行时代码、未安装或操作
手机。没有 FrameTimeline/Perfetto/CPU/分配与功耗对照，不声称视觉或性能改善。
