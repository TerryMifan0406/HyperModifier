# 超级岛通知专用深色 Glass 恢复

2026-10-19。基线为 `OS4.0.2.0.XPCCNXM`，与 `SYSTEMUI_4.0.2.0_BASELINE.md` 中的旧参考 APK 对照。

## 行为

“超级岛与媒体组件 → 超级岛背景”恢复“自定义通知背景”和 0–100% 的“通知背景不透明度”，与展开面板/下拉小窗设置独立。开关默认关闭，默认不透明度 70%；沿用原配置键，因此旧配置及备份中的自定义数值可继续使用。

覆盖四个系统效果：

| 系统效果 | 恢复配方 |
| --- | --- |
| FocusNotificationGlassEffect | focus_notification_glass_params_normal |
| FocusNotificationGlassOnKeyguardEffect | focus_notification_glass_params_on_keyguard |
| FocusNotificationGlassOnKeyguardLightWallPaperEffect | focus_notification_glass_params_on_keyguard |
| FocusNotificationGlassFullAodEffect | focus_notification_glass_params_full_aod |

三个配方共 126 个槽位在旧包与 4.0.2.0 中完全相同。生产代码缓存这些已核对的值，不依赖资源 ID、不在运行时解析资源。启用时替换整个专用配方，并按设置覆盖槽位 14，保留当前系统槽位 18 的动画透明度。锁屏/息屏使用各自配方；同一滑块控制它们的背景不透明度。

关闭开关或模块时回放该 View 最近一次收到的系统配方。clear、进入自定义图片效果时移除目标，已排队工作先核对目标身份，防止通知复用后回放旧材质。关闭专用开关后交还原有通用卡片设置；只有普通通知中心背景符合该设置范围，锁屏/AOD 不进入通用卡片覆盖。普通通知、应用图片背景、收起的小岛不参与本次专用覆盖。保留 4.0.2.0 的 NotificationRowBlurEffect 及背景 Drawable/混色路径，本次恢复的是 Glass 配方，不回退整个通知渲染实现。

## 性能与共存审查

- 只在 SystemUI 安装四对 apply/clear 和一个图片分支退出 Hook，共 9 个；不增加作用域或其他进程的安装开销。安装前解析元数据，部分失败时反向卸载已装 Hook。
- 复用现有 ShadeCardGlassHooks 的 setMiGlass 拦截点，不增加通用 setter Hook。没有目标时一个 volatile 判断即退出；目标写入时只有弱表查找、固定 42 项校验/复制及缓存数组改写，无堆栈采集、重复反射查找、IPC、磁盘或资源解析。
- 每个目标缓存两份 float[42] 和调用参数数组，重复写入复用数组；弱键映射的值不引用 View、Activity 或 Context。
- 配方和颜色解析不在帧回调内进行；getter 的缓存 Method.invoke 仅位于原生效果 apply/clear 生命周期。未添加绘制监听、轮询、定时任务、新模糊层或截图层。
- 设置只比较相关开关/不透明度的有效值；不变时不遍历。每个目标最多排队一个刷新任务，执行时读取最新值。原生 clear/效果切换使旧任务失效。
- 启用时专用目标在通用卡片配方分类前处理，避免额外堆栈分类及通用卡片回放；关闭时交还原有分类与回放，避免通用卡片设置被专用功能的关闭状态屏蔽。与工作区统一 Glass 的最终光学参数处理共存，其刷新不被再次保存为系统原值。
- 深色配方包含与白色配方不同的原生光学参数。未新增渲染通道不等于耗电相同；尤其 AOD、折射与材质动画的 GPU/功耗变化必须真机对照测量。

## 验证

- `tools/tests/focus-notification-glass-test.py` 对照旧/新 APK 的三套配方，校验四对 apply/clear、图片分支及 Row/Injector 访问契约。
- 单元测试覆盖整体深色替换、0/100% 不透明度、系统数组不被修改、最近原值恢复、120 次写入复用、锁屏/AOD/通知中心切换、动画透明度与旧备份导入。
- 当前 ADB 无连接设备，尚未验证实际 Hook 命中、界面外观、转场、功耗和帧时间。不能由静态检查推断帧率或耗电改善。

真机回归应包括通知中心、深/浅壁纸锁屏、AOD、锁屏往返、下拉小窗与图片通知复用；分别检查 0/65/100%、实时关闭恢复、兼容模式，以及统一 Glass 同时启用/关闭。首次安装新增 Hook 后需重启 SystemUI。

本地验证结果：340 项 JVM 测试通过，0 失败/错误；Release R8 构建通过，APK v2 签名验证通过。测试包 `app/build/outputs/apk/release/MyHyperModifier-v1.5.2-focus-glass-release.apk`，SHA-256：`b94658b2d80d92ac2faaee260378319444eed969bf9084a86f5b868dbda2e6c6`。该包包含当前工作区已有变更。

完整 Lint 未通过：17 个错误均位于本次未涉及的代码位置（API 级别兼容、既有私有 API、QUERY_ALL_PACKAGES）；本次 Focus Hook 的 SuspiciousIndentation 已修正，复查没有新增错误。未忽略规则或生成 baseline，也未将全量 Lint 标为通过。
