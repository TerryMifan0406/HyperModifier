# 米家 11.9.621 底栏图标兼容

## 输入与定位

- 用户提供 `base(2).apk`，包名 `com.xiaomi.smarthome`，versionCode `110916211`。
- SHA-256：`9c9c64d76844bbebcbfd209b1553d06da60408eeb9363dfc522dcc488e69601f`。
- 本地 apktool 反汇编确认 `TabPageIndicatorNew$DeviceAnimTabView` 仍使用
  `mAnimView: com.airbnb.lottie.LottieAnimationView`；`mImageView`、`getIndex()`、
  `getCurrentJson()` 及 Lottie 播放 API 保留。
- `getCurrentJson()`、初始化样式读取和组合加载回调表明动画资源字段发生混淆重命名。
  旧实现只能读到当前资源，无法取得另一选择状态的资源对，因此退回到静态图标路径。

| 含义 | 11.8.605 | 11.9.621 |
| --- | --- | --- |
| 普通选中，置顶 | OooOoOO | o0OOo0o |
| 普通选中，非置顶 | OooOoo0 | o0OOo0oO |
| 普通未选中 | OooOoo | o0OOo0oo |
| 首页选中，置顶 | OooOooO | o0OOoO00 |
| 首页选中，非置顶 | OooOooo | o0OOoO0 |
| 首页未选中 | Oooo000 | o0OOoO0O |
| 已加载资源 | Oooo00O | o0OOoO0o |
| 置顶标志 | Oooo00o | o0OOoO |

## 修改边界与性能

`MiHomeLottieFields` 校验完整字段布局及基本类型，并按宿主类缓存访问元数据。
不混用两个版本的混淆字段，不持有 View、Activity 或 Context。读取图标时仍校验
资源为 raw，并检查实际 View 支持 Lottie；商城继续沿用原生 Drawable 路径。
保留原版图形、选中/未选中动画及原有着色，不替换成通用图标。

只在既有图标刷新入口读取实时资源及置顶状态，不新增 Hook、轮询、截图、IPC、
采样或渲染层。字段解析从每次查找改为每个宿主类一次；未知布局的失败也缓存。
用户要求取消米家主页底部渐进效果，故移除米家底栏容器的 `navigationEdgeMask()`，
保留玻璃本体、阴影、navigation inset 和抬高值。其他应用的渐进效果保持现有设置。

## 验证边界

JVM 回归覆盖新版两套动画资源对、旧版兼容、实时置顶与资源变更、继承布局、
不完整布局的安全退出。本次共 374 项 JVM 测试通过，Release/R8 构建成功，
APK v2 签名校验通过。Lint 报告仍有 17 项已有错误、205 项警告；未声称全量 Lint 通过。
未进行真机渲染、动画帧或耗电测量，不据静态检查宣称流畅度提升。
