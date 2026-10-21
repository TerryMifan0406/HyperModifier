# 状态栏与通知中心时钟格式

2026-10-20：新增状态栏「24 小时制隐藏小时前导零」，同时应用到通知中心时钟；通知中心另设「时间显示秒数」，与状态栏显秒独立。新开关默认关闭，接入保存、恢复默认、导出/导入及系统界面修改状态提示。

## 只读设备证据

- `adb shell getprop ro.build.version.incremental`：`OS4.0.4.0.XPCCNXM`。
- `adb shell pm path com.android.systemui`：`/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk`。
- `adb pull` 另存为 `reference/MiuiSystemUI-OS4.0.4.0.XPCCNXM.apk`，没有覆盖 4.0.2 默认基线。
- SHA-256：`d3628f6c3b85d16b1e74aef13ee227b41b6a855a01873c7f50364e274637f571`，与 4.0.2 基线 SystemUI APK 完全一致；系统版本升级不代表此 APK 已变化。
- `apktool d -r` 输出独立目录 `reference/systemui-4.0.4.0/`；布局与字符串使用本次 APK 的 `aapt2 dump xmltree/resources` 核对。APK/反汇编保持 Git 忽略。
- 手机端仅查询版本、包路径及拉取文件；未安装、重启 SystemUI、修改设置或注入触摸。

## 系统路径与行为

复用 `StatusBarClockSecondsHooks` 已有 `MiuiClock.updateTime`、attach/detach 三个 Hook，不增加 Hook 或作用域。

- 状态栏：`id/clock`，时钟模式 0。
- 通知中心竖屏：`MiuiNotificationHeaderView` 的 `id/big_time`，类型 `MiuiNotificationHeaderClock` 继承 `MiuiClock`，模式 0，无独立 `updateTime`。
- 通知中心横屏：同父布局的 `id/horizontal_time`，模式 2（时间＋日期）。显秒保留系统 `status_bar_clock_date_time_format[_12]` 的语言、日期顺序和字面量；只插入秒字段、按需去除小时字段补零。
- `id/date_time` 等纯日期模式、锁屏时钟不纳入本功能。
- 不显秒时，先运行系统更新时间，再仅在 24 小时制移除小时的一个零；支持日期在时间前和非 ASCII 数字，分钟/秒补零保留。演示模式不覆盖。
- 显秒时，两处开关分别控制各自时钟，共享一个按墙上时间对齐的非唤醒 ticker。共同的小时前导零开关决定 `HH`/`H`。
- 关闭功能、关闭模块或进入 SystemUI 兼容模式时，经现有设置刷新通道调用系统 `updateTime` 恢复当前系统文本。两端开关相同时输出相同时间格式，继续使用系统转场路径；实际动画连续性需真机验证。

## 性能检查与验证边界

- 仅隐藏前导零不注册亮灭屏接收器、不启动定时器；两端显秒共用现有接收器与 ticker。
- ticker 在息屏或没有开启显秒的可见时钟时停止排队；每次 render 先检查开关、亮屏、attach 和可见性，之后才读取演示模式字段。
- 使用弱 View 引用和 detach 清理；不强引用 Activity/View。稳定反射字段、方法在安装时缓存；资源 ID 仅 attach 查找，语言/时区值仍动态刷新。
- 无新增模糊层、截图、GPU 渲染路径、IPC、磁盘读写、轮询或每帧格式计算。格式器、Date、StringBuffer、FieldPosition 继续复用，显秒仍产生最终显示字符串。
- 只有相关时钟开关变化才触发系统文本重建；无关设置不回放时钟，同一次设置刷新合并主线程排队。
- JVM 回归覆盖原有显秒生命周期门控/秒对齐、24/12 小时制、午夜和分钟进位，以及新增小时零识别、阿拉伯数字、日期前缀、横屏本地化日期与引号字面量。默认设置测试覆盖两个新增开关均关闭。
- 本次验证为静态路径检查、JVM 测试与本地构建。遵照只读手机要求，未进行安装后的实时切换、兼容模式恢复、与其他模块共存、转场动画或 Perfetto/功耗测量；不声称帧率或耗电改善。

验证结果：`:app:testDebugUnitTest :app:assembleDebug --offline` 通过，343 项测试，0 失败/错误/跳过；`git diff --check` 通过。Debug APK 未安装到手机。
