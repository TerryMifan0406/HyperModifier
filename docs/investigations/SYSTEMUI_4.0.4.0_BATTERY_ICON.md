# 本地 4.0.4.0 标记包：状态栏电池图标静态分析

分析日期：2026-10-21。仅分析 APK、Smali 和资源，没有修改模块运行时代码或安装到设备。

## 样本边界

`reference/MiuiSystemUI-OS4.0.4.0.XPCCNXM.apk` 与 `reference/MiuiSystemUI-OS4.0.2.0.XPCCNXM.apk` 的 SHA-256 均为 `d3628f6c3b85d16b1e74aef13ee227b41b6a855a01873c7f50364e274637f571`，两者字节内容相同。因此以下结论适用于本地标记为 404 的样本，不能据此认定 404 相对 402 有电池实现变化，也不能仅凭文件名独立确认提取来源。

代码路径均相对 `reference/systemui-4.0.4.0/`。原目录通过不解码资源的方式反汇编，XML 是二进制；本次另用 apktool 2.12.0 仅解码资源到 `/private/tmp/myhypermodifier-systemui404-res/`，没有覆盖基线。

## 层级与模式

实际小米状态栏路径是 `com.android.systemui.statusbar.views.MiuiBatteryMeterView`，不是包中同时存在的 AOSP BatteryMeterView / unified battery 类。

`res/layout/battery_digital_view.xml` 的结构：

```text
MiuiBatteryMeterView (LinearLayout)
├─ battery_icon_container (FrameLayout)
│  ├─ battery_image: MiuiBatteryMeterIconView
│  └─ hollow_battery_image: MiuiHollowBatteryMeterIconView
├─ battery_charge_out_image (ImageView，外部充电标识)
└─ battery_percentage_container (LinearLayout，外部百分比)
   ├─ battery_percentage_view (TextView，数字)
   └─ battery_percentage_mark_view (TextView，百分号)
```

`MiuiBatteryMeterView.onBatteryStyleChanged`（Smali 第 1705 行）选择图形；`updateChargeAndText`（第 3613 行）选择外部数字和充电标识。

| style | 图形 | 外部百分比 |
| --- | --- | --- |
| 0 | MiuiBatteryMeterIconView，无内部数字 | 隐藏 |
| 1 | MiuiHollowBatteryMeterIconView，有内部数字 | 隐藏 |
| 2 | 图标容器隐藏 | 隐藏（历史顶部电量条模式需结合 BatteryIndicatorView 分析） |
| 3 | MiuiBatteryMeterIconView，无内部数字 | 显示 |

这指有效的 `mBatteryStyle`。原始设置另存于 `mStoreRealStyle`；刘海、超级岛、AOD 等分支可能临时改变有效模式，例如有刘海时将 style 2 转为 0。因此做后续功能时不能只依据设置整数假设当前显示形态。

## 无内部数字：描边外壳 + 独立电量条

`MiuiBatteryMeterIconView`，默认 `battery_meter_use_legacy_drawable=false`。

- `battery_meter_bg*.xml`：圆角横向外框和右侧凸起电池正极。
- `battery_meter_progress_*.xml`：内部独立的实心圆角电量条；与外框之间留有空隙。
- `battery_meter_progress_radius.xml`：移动圆角遮罩；通过 DST_IN 合成电量区域。
- `onDraw`（第 1702 行）：画缓存的 `mProgressBackgroundBitmap`，然后叠加 `mTintBgDrawable` 外框。
- 电量从左向右增长，横向遮罩位移为 `-16.599976 × (1 - level/100)` dp，再按设备密度取整；0% 有额外位移处理。并非简单把整个图标缩窄。

默认 View 及矢量画布为 28 × 20 dp。外轮廓约 x=2.6..25.212、y=4.116..15.884；内部电量条约 x=4.954..21.515、y=6.47..13.531。这些是矢量坐标，不是截图测量。

## 内部数字：实心电池形状 + 电量分区 + 镂空数字

`MiuiHollowBatteryMeterIconView`。Hollow 指数字镂空效果，不代表它采用普通模式的细描边外框。

- `firstBackground`：`hollow_battery_meter_normal*.xml`，完整电池轮廓，普通状态 fillAlpha=0.4，表示剩余未填充区域的底色。
- `secondBackground`：`hollow_battery_meter_progress_*.xml`，与完整电池同形状的实色电量层。
- `hollow_battery_meter_radius.xml`：圆角遮罩；默认位移 `-23 × (1 - level/100)` dp，0% 特殊处理。
- `updateFirstBitmap`（第 1810 行）：将实色电量层与移动遮罩用 DST_IN 合成。
- `updateSecondBitmap`（第 2421 行）：再用 SRC_OUT 合成未填充的底色部分。
- `onDraw`（第 884 行）：依次绘制两个缓存 Bitmap、性能模式标记（如适用）、数字。

数字不是 TextView，也不在电量图片内预先烘焙。由同一个图标 View 调用 `Canvas.drawText(String.valueOf(level), ...)`，显示 0..100，不带百分号。

正常状态先使用 `hollowTextPaint` 的 CLEAR 混合模式擦出数字，再叠加 `textPaint` 的低透明度黑/白文字（资源为 #1A000000 / #1AFFFFFF）。因此数字的主要可见颜色来自后面的背景；它并非固定黑色数字贴在白色电池上。充电、快充、省电、性能模式会跳过这一步镂空，使用白色数字；低电量本身不在跳过镂空的判断中，但会令文字颜色切成白色，需要单独验证其最终效果。

字高默认 9.599976 dp；水平位置 `(viewWidth - batteryLevelTranX - measuredTextWidth)/2`，`batteryLevelTranX` 默认 1.3399963 dp；垂直基线根据 ascent/descent 居中。优先采用系统 MiSans 可变字体，字重 620，并保留 mipro-bold 回退。

完整电池轮廓约 x=2.6..25.594、y=3.88..16.12，比无数字外框略宽、略高。视觉上为完整圆润电池色块，剩余区为较淡底色，电量区为实色，数字居中。

## 颜色、充电和兼容路径

原包提供浅/深色及 tint 分支。默认浅色图标颜色白色，深色图标颜色 #BF000000；不要把深色模式误认为完全不透明黑色。状态颜色资源包括充电 #1DCD3A、低电量 #FA382E、省电 #FF9F05、性能 #3482FF；实际哪些层采用哪个颜色由各自状态分支决定。

内部数字模式充电时还可能显示独立的外部充电 ImageView（style 1/2 且未加入电池岛）。无内部数字模式的充电/快充标记由其图形资源分支处理；不能概括为两种模式都只有一个统一的闪电层。

旧式路径由 `battery_meter_use_legacy_drawable` 控制：从 `res/raw*/stat_sys_battery*.webp` 图集生成 LevelListDrawable，BatteryIcon 持有资源及等级缓存。默认现代路径使用上述矢量与位图合成，后续修改需保留旧式与主题覆盖边界。

## 外观对照

本地预览：`reference/systemui-4.0.4.0/battery-analysis/comparison.png` 及同名 SVG。使用 APK 原始矢量路径重建 20/50/80/100% 普通状态，放大显示。字体为替代字体，透明度合成、Android 像素取整和运行时 tint/主题覆盖可能不同；该图用于辨认组成与形状，不是真机截图或逐像素复现。

## 性能检查范围

本次仅做静态审查，没有改变系统进程启动、Hook 安装、主线程任务、IPC、GPU 层或耗电行为。

已有实现将 Drawable 与中间 Bitmap 缓存，尺寸不变时复用；合成时仍会新建 PorterDuffXfermode。内部数字的 onDraw 每次执行数值转字符串、measureText 和 getFontMetrics()，后者会返回 FontMetrics 对象，是后续若高频刷新需要关注的分配点。两种 View 都有 attach/detach、布局变化与资源刷新路径，新增功能应复用这些状态和缓存，避免在 draw 中反射、读设置、重建图形或引入计时器。

上述只是潜在热点的静态定位，尚无频率、CPU、分配采样、帧率或功耗前后对照，不能据此宣称性能问题或性能改善。未运行构建测试，因为未修改运行时代码；未做真机外观回归。

## 模块实现：无数字电池使用实心框

设置入口：状态栏 → 状态栏电池 → 无数字电池使用实心框，默认关闭。键为 `status_bar_solid_battery_frame`，已接入模型、预设复位、SharedPreferences、设置 Bundle、备份 schema 和 SystemUI 修改状态标识。

仅 Hook `MiuiBatteryMeterIconView` 自己的 onDraw、onDarkChangeInternal、attach、detach（4 个方法）；不改变 `mBatteryStyle`，不操作外部充电 ImageView，也不影响原内部数字 View。这样无内部数字的 style 0/3 使用新框，style 3 的外部百分比仍由系统显示。旧式图集和竖向主题绘制保留系统实现。

`BatteryFrameGeometry` 采用本地样本的内部数字电池轮廓；原系统普通充电、快充及双箭头性能标识经过缩放或位移完全放进电池主体，并从主体中镂空。性能与充电可以同时显示；快充标识优先于普通充电。电量层保持系统状态颜色优先顺序：充电/快充 → 省电 → 性能 → 低电量 → 普通；tint 与 MIUI 优化关闭分支另按系统语义处理。基色跟随资源及系统 tint，动态值在系统状态/取色回调后刷新。

静态图形预览为 `reference/systemui-4.0.4.0/battery-analysis/solid-frame-preview.png`（及 SVG），用于检查标识排列；不等同于设备主题、取色、透明度和抗锯齿的最终效果。六种单独/组合标识通过离线光栅边界检查，均完整落在电池轮廓内。

性能边界：路径在首次启用时解析一次；主体及 6 种标识组合静态缓存；电量分区仅在电量、标识或增长方向变化时更新。绘制回调只有开关判断、弱引用表查询、缩放与两次 drawPath，没有反射、字符串/路径解析、模块主动对象分配、IPC、位图、saveLayer 或定时任务。状态回调缓存反射字段与 MethodHandle，取色调用使用 invokeExact，避免反射 varargs 数组。弱键的值仅持有绘图对象与资源 ID，不持有 View/Context。设置刷新合并队列并比较有效启用值，无关设置不遍历图标。

系统原有状态与位图更新仍照常运行，未宣称消除其成本；关闭开关、停用模块或进入兼容模式时直接走原 onDraw 并刷新当前图标，无需回写或重建系统设置。冲突边界：其他模块若同时完全替换该 onDraw，最终行为仍受 Hook 顺序影响；本实现不修改其字段或挂全局 Canvas Hook。

验证：390 项 Debug JVM 测试通过（0 失败/错误/跳过），包括新增的电量边界、双方向、主题兼容与性能/快充组合选择，默认设置关闭检查；Debug APK 构建通过。未进行真机 Hook 命中、实时启停、深浅色、系统主题、充电/性能切换、超级岛动画或功耗/帧率测试。

补充构建验证：最终源码再次运行 `:app:testDebugUnitTest :app:assembleRelease --offline` 成功，390 项测试通过，Release lintVital/R8/打包通过；`apksigner verify --verbose` 确认 APK v2 签名有效。产物为 `app/build/outputs/apk/release/app-release.apk`。尚未安装到设备。


## 2026-10-21 外观细调（最终方案）

根据用户真机截图与后续更正，仅将图形高度压到上一版的 98%，宽度与 View 占位保持不变，缩放中心为画布 (14, 10)。

普通与快充闪电直接沿用原版 `battery_meter_bg_charge`、`battery_meter_bg_quick_charge` 的第一条 pathData，已逐字比对一致；保留原版形状，居中后分别适配到 94% / 92%，性能与充电组合时采用 70% 充电标识，双箭头在左、闪电在右。图标填色取自与电池基色相同的系统资源 / DarkIconDispatcher tint，跟随状态栏切换深浅及透明度，不固定为深色。

镂空间隙沿用系统数字电池的 CLEAR 合成语义：缓存 Paint 使用 `BlendMode.CLEAR`、`FILL_AND_STROKE` 与 0.85 矢量单位宽的圆角描边，擦除标识及其外围，再以状态栏取色绘制标识填充，形成真正透明的间隙而非白色描边。

静态证据：`MiuiHollowBatteryMeterIconView` 构造函数为 hollowTextPaint 设置 PorterDuff CLEAR；`MiuiBatteryMeterIconView.onAttachedToWindow` 原生设置 LAYER_TYPE_HARDWARE。原版无数字闪电周围的留白主要已写入资源矢量，没有单独可复用的描边计算方法。本实现沿用其镂空机制与当前 View 已有的隔离层，不调用原方法冒充对任意 Path 的支持，也不新增 saveLayer。若其他模块改变层类型，退回系统绘制，避免 CLEAR 擦到父背景。

性能检查：复用路径和标识缓存；电量分区只在电量或方向变化时计算。普通状态两次 drawPath，有标识时追加 CLEAR 与填充两次 drawPath。无新增离屏层、位图、轮询、反射查找或主动每帧对象分配。原有系统状态回调与兼容/关闭恢复边界保留。

静态深浅色预览：`reference/systemui-4.0.4.0/battery-analysis/final-clear-preview.png`。它用于检查图形排列、透明间隙和主题色变化，不是真机 Hook / GPU 合成验证。此前的白色描边预览为迭代过程资料，不代表最终实现。未做真机帧率、耗电对照或外观回归，不宣称性能改善。

最终验证：390 项 JVM 测试通过（0 失败/错误/跳过），Release lintVital、R8 与构建通过，APK v2 签名验证通过。普通/快充闪电路径与原版资源逐字相同。测试包 `app/build/outputs/apk/release/HyperModifier-v1.5.2-battery-frame-height98.apk`，尚未安装设备验证。


## 2026-10-21 真机反馈后的高度与镂空调整

按用户反馈扩大透明间隙，将 CLEAR 描边宽度从 0.85 提到 1.25 矢量单位，单侧留白由 0.425 增至 0.625。基于原始矢量几何，将电池高度比例从 0.98 改为 `(15.884 - 4.116) / (16.12 - 3.88)`，即约 0.961438，缩放中心仍为 (14, 10)。实心框的可见高度由此对齐原版无内部数字外框的 11.768 dp，宽度、View 占位和标识原始形状不变。标识随整体垂直变换一并收低，颜色继续跟随状态栏。

这是根据 APK 路径尺寸的静态对齐，不保证跨主题、设备像素取整或抗锯齿后逐像素一致。继续复用缓存路径、CLEAR Paint 和系统已有绘制层，仅改变常数，没有新增绘制次数、缓存、Hook、IPC 或轮询。

本次验证：原始几何上下边界对齐计算通过（4.116..15.884），390 项 JVM 测试通过，Release 构建与 APK v2 签名验证通过，`git diff --check` 通过。产物：`app/build/outputs/apk/release/HyperModifier-v1.5.2-battery-stock-height.apk`。未进行本次改动的真机外观或性能测量。


## 2026-10-21 继续试调

按用户指定，CLEAR 镂空描边宽度改为 2 矢量单位，图形高度缩放 94%、宽度缩放 98%，中心仍为 (14, 10)，View 占位不变。闪电形状、状态栏取色、缓存、实时启停与兼容回退逻辑不变。仅修改绘制常数，不新增绘制次数、层、Hook、IPC 或轮询；本次仍无真机帧率/功耗测量。

本轮验证：390 项 JVM 测试通过（0 失败/错误/跳过），Release 构建、APK 签名及 `git diff --check` 通过。测试包：`app/build/outputs/apk/release/HyperModifier-v1.5.2-battery-h94-w98-gap2.apk`，尚未进行本轮真机外观验证。


## 2026-10-21 充电标识上下穿出

经用户确认，普通闪电采用原始路径的 110%，快充采用 105%，均围绕 (13.25, 10) 等比放大。性能组合状态保持同样的闪电高度，仅沿用横向偏移，保留左侧性能双箭头。电池主体高 94%、宽 98%、中心 (14, 10)、CLEAR 描边宽度 2 及状态栏占位不变，不增加整体上下位移或改变横向间距。

静态几何估算：普通闪电上下各穿出电池主体约 0.34 dp，快充约 0.29 dp；含镂空间隙的范围仍在 28 × 20 View 内。路径生成仍仅在缓存初始化时发生，没有新增每帧计算、绘制次数、绘制层、对象分配、Hook、IPC 或定时任务。尺寸与裁切检查属于静态验证，尚无本轮真机外观与性能测量。

本轮验证：普通/快充原版路径逐字一致检查、静态上下穿出与 View 范围检查通过；390 项 JVM 测试通过（0 失败/错误/跳过），Release 构建、APK 签名及 `git diff --check` 通过。测试包：`app/build/outputs/apk/release/HyperModifier-v1.5.2-battery-bolt110-quick105.apk`。静态深浅色预览见 `reference/systemui-4.0.4.0/battery-analysis/bolt110-quick105-preview.png`。尚未进行本轮真机验证。
