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
