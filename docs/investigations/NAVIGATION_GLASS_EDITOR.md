# 底栏柔光玻璃参数调节

2026-10-20。在主页应用底栏分组顶部增加「底栏柔光玻璃参数调节」。旧抬高滑块移入新页，
继续使用 `hyper_glassify_hidden_navigation_lift`，不迁移/清空已有值。

## Deadliner 来源与本地适配

源项目 `/Users/aritxonly/Codes/Android/Deadliner`，HEAD
`5499df09383e84a120e19bd0c629c6e6ffa7ef0d`，读取本地工作树。
本次只读取源项目，未修改源项目文件。目标沿用 MIUIX 0.9.4-rc01、Shapes 1.2.0，
Compose BOM 2025.01.00；源项目 Compose BOM 2025.08.00。

迁入依赖闭包：

- `ui/settings/AdvancedMaterialPreview.kt`：同一张背景、内容专用 backdrop、顶部材质和
  底部柔光玻璃胶囊，默认启用自研管线。新增 bottomLiftDp 仅用来预览抬高位置。
- `AdvancedMaterialSettings.kt` 内半径 Slider 和默认刻度，同步最新 16–168 范围、默认 84、
  haptic/key point/RTL marker。该源文件在源项目工作树有修改，本轮迁入其中的滑块闭包。
- `AdvancedMaterialFineTuningPanel.kt`：完整面板、范围、格式、即时开关、松手保存、重置和导出按钮。
- `ui/base/Slider.kt`、`Switch.kt`、所需字符串和背景资源。沿用现有材质/阴影/按压依赖。
- 不迁入 GlobalUtils、Appearance 数据流、官方材质开关和业务解锁手势；由模块设置适配。
  原面板导出按钮适配为复制模块 JSON 预设，重置仅修改本页参数和抬高值。
- 保留 Deadliner MIT 来源与许可。背景图放在 drawable-nodpi，避免 2560×1600 资源按高屏幕
  密度额外放大；图像像素和预览的 ContentScale.Crop 保持不变。

源文件核对 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| AdvancedMaterialSettings.kt | 80f4204f54eabab4d9377dbac8025f2ba54a711be293df3b19a6a0e5b30b994b |
| AdvancedMaterialPreview.kt | 12fa3a4369323f4bc39bd2b3964198af8c540b1ce3953702ea01494e1200e599 |
| AdvancedMaterialFineTuningPanel.kt | 1e650377fd0fac744e04e6e50f8ce2bf964d2f58d6fd1e75193e684bbd046172 |

## 保存和运行时

`navigation_glass_parameters` 为版本化数值配方，包含半径与完整 AdvancedMaterialFineTuning。
空值沿用最新版默认参数；未知版本、错误长度/类型、NaN/Infinity 回退默认，有限越界值经
canonical normalized() 处理。保存、Provider Bundle 和整包备份均包含此键。

拖动只更新本页草稿/预览，松手提交；布尔开关即时提交。抬高预览也不逐帧写偏好。
`NavigationGlassRuntime` 仅由底栏宿主按需初始化，不在所有 ModuleSettings 进程加载 Compose。
它比较配方与抬高两个相关键，合并主线程更新，变更时才解析配方和构建不可变材质。
无关设置不重放底栏 UI，不在绘制/触摸回调解析规则或 JSON。

所有注入底栏读取同一可观察材质；抬高直接观察同一配置，避免等待应用自身下一次绘制。
Spotify 原生播放胶囊同步更新材质、shader/effect 缓存和 tint/描边，并约束高级透明度范围，
避免 alpha 乘积超出 1 后在 Color.argb 中溢出。

GPU / PixelCopy / 视频外观路径共用材质。NativeGpuBackdrop、WindowGpuBackdropSampler 和
旧采样器用弱观察者刷新模糊核/折射所需采样边缘，按需重录或丢弃旧裁剪帧；只调整 tint
等不影响裁剪的参数时，不额外触发采样。观察者不强持有 Activity/View，dispose 移除。
应用商店仍默认关闭；抖音仍排除底部渐变；本页不改变各应用的采样方案和启用开关。

## 说明与归属

页面备注：「自研柔光玻璃管线，参考 Apache-2.0 开源项目 AndroidLiquidGlass 的 Shader 设计」。
开源条目可打开 https://github.com/Kyant0/AndroidLiquidGlass 。许可证已核对上游 LICENSE，
并补入 THIRD_PARTY_NOTICES、Shader 来源注释和 Apache-2.0 许可资产。
该说明指 Shader 设计参考，不把完整本地管线描述为直接使用该库。

## 验证边界

逻辑回归覆盖高级参数/布尔开关序列化、畸形/非有限数据回退、范围归一化和参数与旧抬高键
的备份往返。代码审查覆盖所有底栏 Provider、原生 Spotify 胶囊、弱观察者、相关键刷新和
采样边界更新。性能判断为结构性检查，无真机帧耗时或耗电对照。
遵照用户要求不截图；尚未通过真机确认布局、滑动预览和跨应用实时更新效果。

最终验证：369 项 JVM 测试全部通过，Release/R8 与 APK v2 签名验证通过；
Lint 报告保留 17 个既有错误，新增页面/参数代码未新增 Lint error。
`git diff --check` 通过。最终构建日志 `/private/tmp/hmc-navigation-editor-final.log`。


## 2026-10-20 默认值再次同步

读取同一 Deadliner HEAD 的最新本地工作树，完整同步 `AdvancedMaterialFineTuning` 与
`MiuixModalMaterialTuning`，并同步 `AdvancedMaterialTuning` 的默认模糊度 84、上限 168
和超过最高预设等级后的饱和度边界处理。保留模块自己的设置接口，不迁入源应用 Appearance 依赖。

- 高光透明度：0.9；高光灰度：0.98826814。
- 对话框/底部面板背景透明度：0.70237434 / 0.69986033。
- 默认、空配置和恢复默认共用这些值。已有显式配方继续保留原值，不做强制覆盖。
- 历史 DefaultsV6/V5 等基线同步冻结，避免构造器默认变化污染历史迁移参考值。

本次参数源 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| AdvancedMaterialFineTuning.kt | 02aeccd41c4034bda666d7661439eb2a5074d6007ea433b8cdca16ce059258a8 |
| AdvancedMaterialTuning.kt | 8c7dcbdbd9de8bba42bcebbf0dd7bc6f17a528fac186a4bde34102864ea71635 |
| MiuixModalMaterialTuning.kt | 36b61f470db58752c6677a343f08a93256cce5362c85e1e8e71ebc38d54911c4 |

性能检查：本次只修改配方、范围及其边界计算，没有新增启动 Hook、IPC、绘制层或轮询。
更大的默认模糊半径可能增加 GPU 模糊及采样边缘成本；现有按需采样和缓存路径保持不变。
未做真机帧耗时或耗电测量，不声明性能改善。

本次默认值同步验证：376 项 JVM 测试通过，Release/R8 构建与 APK v2 签名校验通过。
Lint 报告仍有 17 项已有错误、205 项警告；日志 `/private/tmp/hmc-deadliner-defaults.log`。
