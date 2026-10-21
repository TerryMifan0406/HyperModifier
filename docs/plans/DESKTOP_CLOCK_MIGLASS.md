# 默认经典 MAML 时钟材质原型

2026-10-21。实现起点 `a85d460`；一镜到底仍留在归档分支，不恢复。
研究证据见 [调用链取证](../investigations/DESKTOP_CLOCK_MIGLASS.md)。

目标是保留默认经典 4×2 时钟主时间的字形、字体轴、格式、前导零、位置及点击语义，
仅替换其绘制材质，日期/天气不变。按用户最新要求提供三个选项：

| 选项 | 原型实现 | 当前验证边界 |
| --- | --- | --- |
| 默认 | 系统原绘制；首次默认状态不安装新增绘制 Hook | 静态及主机回归 |
| 模糊 | 原排版 SkTextBlob → 原字形 SkPath → 原生 PassBlur | 背景采样、洞口及视觉效果待真机验证 |
| 柔光玻璃 | 同一路径 + 原生 BloomStroke 参数 | 边缘高光/折射待真机验证，未证实等同锁屏 MiGlass |

这里的模糊指字形内的模糊背景采样。两档均为实验功能；不能把库内属性存在、
原生字形提取或测试通过表述成已实现完整的 MiGlass 字形折射。

## 阶段

- [x] 保留并回滚一镜到底，核对当前 MAML / Flutter 库和默认经典资源。
- [x] 追踪原生 Layer、轮廓和滤镜接口；矩形 Layer 方案暂不接入普通 DateTime。
- [x] 改走原文字排版链：窄范围 MAML draw_text → 同线程 ParagraphPainter → 原生字形路径。
- [x] 加入默认/模糊/柔光玻璃三档选择、现有设置桥接和独立缓存。
- [x] 按原生库 Build ID + 15 个代码契约校验 ABI；无桌面版本号判断。
- [x] 延迟安装、安装事务回退、失败不重试；不匹配和不支持的画笔保留系统绘制。
- [x] 主机 ASan/UBSan 验证嵌套/线程作用域、坐标、透明度、生命周期、原画笔不变、
  Builder 材质精确恢复、互斥材质及异常回退；验证真实 ELF 和代码变异拒绝。
- [ ] 真机确认目标作用域与文字绘制同步、模糊背景来源、0/6/8/9 洞口和滑页/解锁效果。
- [ ] 模糊档成立后，再验证柔光玻璃的真实边缘几何与折射；若只有矩形或无效果，
  记录限制并调整引擎路径，不把它当作完成的玻璃时钟。
- [ ] 同条件采集 Perfetto / FrameTimeline / CPU 与分配数据，决定是否保留和扩大范围。

## 原型边界

仅匹配原元素名 `center_classic_plus`，不按可变实例 id 或时间字符串猜测。
字形从同一 Flutter 引擎中的已排版文本提取，构造/记录/析构均由该引擎完成；
不跨 Android HWUI 传递 C++ 对象，不保存 Dart 堆对象、View、Context 或字体指针。
不支持的颜色源、滤镜、描边、非 Impeller 路径及已有玻璃材质直接调用原绘制。

设置即时传到桌面，并在下次原生时钟重绘时使用最新档位；不主动拉高刷新率或
强制触发桌面刷新。可能需要等下一分钟或桌面本身重绘才能看见变化。选择默认后
下一次重绘恢复原绘制，已安装的两个窄 Hook 保留廉价退出，不运行时卸载正在执行的函数。

## 性能检查

没有新增进程作用域、叠加窗口、截图、CPU 像素扫描、轮询或动画定时器。
默认不安装这两个绘制 Hook；开启后非目标段落仅判断 TLS，MAML 文字只读名称比较。
指纹计算、安装与元数据解析在已有工作线程完成；绘制回调没有磁盘、IPC、反射或日志。
设置只有档位变化才更新本功能缓存/排队安装；复用原有设置广播，不新增桥接服务。

目标重绘会由原生引擎提取路径、分配 SkPath/DlPath 并记录材质属性，GPU 采样/高光也有
新增成本。目前不缓存裸 SkTextBlob 身份以免跨生命周期错误复用，也没有新增持续重绘。
需实测解锁/滑页是否反复重绘 MAML，确认实际分配频率与 GPU pass。主机回归不构成
帧率、耗电或流畅性改善证据；本轮未在真机执行该测试包。

## 手动验收

先选模糊，再对比柔光玻璃与默认。检查跨分钟、12/24 小时、前导零、字体轴/粗细变更、
亮暗壁纸、滑页/解锁、编辑/移动/删除/重加、关模块和其他 MAML 不受影响。
`MHMClockMaterial` 标签只在配置变化和安装时输出，`target/path` 为累计诊断计数；
等一次重绘后切回默认，可从日志判断是否经过目标文字与字形路径，不逐帧打印。

手机继续只读；产物放仓库外 `/Users/aritxonly/Builds/MyHyperModifier/`，不发送飞书。

## 本轮验证记录

- `./gradlew :app:assembleRelease :app:testDebugUnitTest`：通过，390 tests / 0 failure；
  最后仅修改原生恢复路径后再次 `:app:assembleRelease` 通过。
- `clock-glass-render-test.cpp`：clang++ C++17 + ASan/UBSan 通过。
- `python3 -B tools/tests/clock-glass-install-test.py <maml.so> <flutter.so>`：真实库代码
  契约、15 处单独变异、互斥 Hook、缺失 trampoline、回退及失败不重试通过。
- `python3 -B tools/tests/native-install-loop-test.py <maml.so>`：原安装器回归通过，
  包括 10000 次重复安装与 80000 次并发加载通知；新增 Flutter 通知只接受一次。
- apksigner：Release v2 签名有效。
- ADB 只读复核设备 OS4.0.4.0.XPCCNXM 的 `/system_ext/lib64/` 两份库 SHA256，
  与研究基线相同；未安装包、重启、触屏或修改设置。SystemUI 静态分析仍使用既定
  OS4.0.2.0 基线，不混用本机新版本包。
- APK：`/Users/aritxonly/Builds/MyHyperModifier/2026-10-21/MyHyperModifier-clock-material-prototype.apk`；
  SHA256 `652a3a20b69238cdb257fe781cd681450080a974e60777d2bf56094aa025c70f`。
