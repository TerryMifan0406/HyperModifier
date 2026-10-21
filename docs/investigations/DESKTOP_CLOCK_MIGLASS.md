# 桌面原生时钟接入 MiGlass 的初步取证

2026-10-21。用户要求终止一镜到底实验，保留版本，并研究默认经典桌面时钟能否
接入原生柔光玻璃。当前结论：**已有原生材质与路径轮廓两个候选通道，值得做
定向原型；尚未证明二者组合可使普通 DateTime 获得玻璃笔画效果。** 本轮不新增运行时
Hook、不生成宣称已支持 MiGlass 的设置开关，不安装/重启/触摸或改写手机资源。

## 回滚与归档

主分支通过 `adbcea2` 回滚到实验前 `9d98805` 的运行时代码与设置；这两者的
`app/` 与 `tools/tests/` 没有差异。此前搜索兼容、前导零及其他功能保留。
完整实验在 `codex/archive-clock-transition-mvp-v10`（`eb7ee93`），v10 签名 APK
保留。入口和回滚验证见 [归档说明](CLOCK_TRANSITION_ARCHIVE.md)。

回滚只移除本次实验 10 个 SystemUI / 3 个桌面 Hook；模块其他既有 Hook 不变。
回滚 APK 需用户手动安装并重启系统界面和桌面；当前手机旧进程不会因本地 Git
回滚自动卸载 Hook。本轮签名 Release 和 390 项 JVM 测试通过；原生安装器
ASan/UBSan、4608 个搜索与 96 个小组件选择器汇编回归通过。未测帧率/耗电。

## 当前设备及可复现证据

只读 `dumpsys package/activity` 确认桌面更新包为 8583，无 DEX，Rust + Dart AOT。
默认经典时钟仍是 id=493、MAML id=101390060、4×2、productId
52b8238d-848f-4608-a0e6-28458d4b1f2c、资源 40130。这些身份用于取证，不建议将
可变化的运行时 id 当作未来用户配置的永久匹配规则。

从设备只读拉取图形库，MAML 库复用早前保存文件并对照设备实时 SHA256：

| 文件 | SHA256 |
| --- | --- |
| librust_maml_sdk.so | 82f65d035e75fad138c692e42753acbb9e9c1254c1fbf683c0ec7db22b1e5e63 |
| libhyper_os_flutter.so | 96611d690393c5e274f90acfc1357ad9dbfd089f641986eaa518e1d4f688ff59 |
| libhwui.so | ccd182a0052278d61dd4a6ae920b452a3f724d0d7a37af659b1ccc9ca5cbd844 |

原始 APK、系统库、解出的 `.gnu_debugdata`、时钟 manifest 和分析 JSON 保存在
忽略目录 `reference/desktop-miglass-20261021/`，不提交 Git。40130 manifest
SHA256 为 3ab3bb24be5296f2e4f138b889e9365ea87b49929bd7fd062d454c358021724b。
它来自之前按设备官方资源地址下载的同版本包；本轮普通 shell 没有读取私有
资源目录，不能据此宣称设备私有文件逐字相同。

离线复现：

```sh
python3 -B tools/analysis/inspect-maml-material.py \
  reference/desktop-miglass-20261021/maml.so \
  reference/desktop-miglass-20261021/libhyper_os_flutter.so \
  reference/desktop-miglass-20261021/clock-40130-manifest.xml
```

脚本读取 ELF 符号、解压 mini debug、解析真实 BL 调用点与 XML；缺少或歧义
契约时退出。不调用 Android/Dart，不安装 Hook，也不把静态符号存在当作效果验证。

## 已确认的原生路径

| 位置 | 当前库 RVA | 含义/证据 |
| --- | --- | --- |
| HyperMaterialHolder::load | 0x4e02ac | 原生材质 XML 解析入口；直接调用点 0x4bd8d4 属于 ElementGroup::on_create_child |
| GlassMaterialConfig::resolve_pass_window | 0x4d1a2c | 玻璃材质存在独立跨窗口配置解析 |
| LayerScreenElement::update_view_with_materials | 0x498a30 | Layer 更新材质；LayerView::on_draw 的 0x497ee8 与 LayerScreenElement::tick 的 0x4d1110 直接调用 |
| FFIMaterialProperties::from_data | 0x53fc0c | 生成宿主属性；FrameLayout::create_view 和 update_view_properties 调用 |
| TextScreenElement::draw_text | 0x48a6d0 | 当前文字绘制入口，沿 rust_maml_canvas / Dart TextPainter 绘制，不是 Java TextClock |
| ImageFilter::initGlassV7 的 FFI dispatcher | 0xe896fc | Flutter 引擎编译了原生玻璃滤镜；方法本体可能内联，不能把 dispatcher 当普通 C 函数调用 |
| impeller::MiGlassContents::Render | 0xd5d860 | 引擎具备原生玻璃渲染，并含 SDF、v13 F16 shader 与 MiHyperMaterialEngine 路径 |

MAML 库中还存在 HyperMaterialModel、HyperMaterialVariableUpdater、MaterialToken、
GlassConfig、BackgroundBlurConfig、BloomStrokeConfig，以及 token、params、
smallBlurRadius、bigBlurRadius、disableGlass、rustPassWindow 等解析相关字串。
HyperMaterialHolder::load 对 MaterialToken、enable 及子配置作处理；完整 XML
schema、Rust enum/Option 布局和 FFI 所有权仍需逐项核对，不能仅凭字串编造配置。

桌面 AOT 存在 MamlGlassMaterialConfig 的结构化调试输出。0x16833c8 的 toString
实现依次读取 sigmaReflect（对象+7）、sigmaBgColor（+15）、thicknessPx（+23）、
reflectOffsetPx（+31）四个 double，并打印字段名。这证明 Dart 侧有 MAML 玻璃
配置模型；对象地址含 Dart tag，不能把这些偏移作为普通 C 结构直接操作。

HWUI 也有 SkCanvas::drawRectMiGlass、BackgroundBlurBlendProperties 等原生符号，
但桌面当前路径是独立 Flutter 引擎。两套 SkCanvas/SkPaint 不保证相同 ABI，
不能把 Flutter 或 Rust 包装对象直接传给 libhwui 的同名接口。已有 SystemUI
View.setMiGlass / Paint.setGlassEffect 经验只能参考材质语义，不能照搬 Java Hook。

## 默认经典时钟为什么不能只开一个材质布尔值

当前 40130 资源是普通 Group/DateTime/Text 组合：frameRate=0，更新器为
DateTime.Minute，supportBackgroundBlur=false。解析结果包含 5 个 DateTime，
**没有 Layer、MaterialToken 或 GlassConfig**。主时间 center_classic_plus 的
formatExp 为 HH:mm/h:mm，字体来自 font_path_style_plus，字号 96*scale。

因此，现成的层级材质通道与当前 DateTime 绘制之间尚有接入缺口。不能因引擎
支持玻璃，就声称改 supportBackgroundBlur=true 能使数字变玻璃；它也不能证明
跨窗口壁纸采样、材质输入色、透明区域处理已经正确。MAML 既有 Picture/图像
缓存与直接绘制分支需要按目标实例区分，不能把所有 MAML 一概断言为最终位图。

当前已验证的材质更新是 Layer/FrameLayout 通道；普通 DateTime 是否能走同等
文字材质、或需要利用其 alpha/字形作为材质裁剪，仍未确认。给整个 4×2 小组件
加矩形玻璃背景不等于玻璃数字，本方向不能用该结果代替验收。

## 后续原型路线与停止条件

1. 先追 MaterialToken/GlassConfig 从 Layer 到 Dart MamlGlassMaterialConfig、
   实际 ImageFilter 的传递，核对 disableGlass、跨窗口采样及 capability gate。
   保持仅目标资源/元素范围，优先加载或相关配置变化时安装/更新，不 Hook 全局
   Paint/Canvas 每帧遍历，也不强开所有 MAML 的材质能力。
2. 验证能否以原时间文字轮廓作裁剪，同时保留时间格式、主题字体、点击语义、
   原色、日期等内容。先做一个目标元素的最小原型；不能定向或不能验证 alpha
   裁剪时停在证据阶段，不发布冒充玻璃笔画的矩形卡片。
3. 能力和裁剪通过后，才接模块偏好/定向规则/原值恢复与实时启停。对象、token
   和缓存生命周期必须明晰；不保留 Dart 堆对象跨 GC，不从原生任意线程调用
   Dart 函数，不用未经核对的 Option/FFI 布局写字段。
4. 验证原 0fps/分钟更新下的采样刷新，再覆盖滑页、解锁、动态壁纸、主题字体、
   小组件移除/重加与系统材质关闭。若需要逐帧截图、CPU 读回或常驻轮询才能
   更新壁纸，先停下评估成本。优先复用原生合成与采样缓存。

本轮只有静态反汇编、ELF 直连与资源取证。研究代码不进入模块运行时，没有新
Hook、轮询、IPC、窗口、截图或 GPU 层。未来原型的背景采样/滤镜/遮罩可能增加
GPU 带宽和渲染 pass，须用同设备同操作的 Perfetto/FrameTimeline/分配采样验证；
本轮不能给出可用性、帧率或功耗改善结论。

## 第二轮：区分路径裁剪与字形折射

用户要求继续做玻璃时钟后，只读再次核对设备，默认经典时钟仍为上述 493 /
101390060 / 40130。读取现有日志未命中材质能力或 MAML 裁剪诊断，不能据此
判断能力关闭或该路径不存在。本轮没有清日志、安装、重启或操纵界面。

### 新确认的路径轮廓入口

`ElementGroup::on_create_child` 同一 Layer 构建分支先加载 HyperMaterialHolder，
再扫描 `ViewOutlineProvider` 子元素。不是仅从二进制里找到两个不相干的字符串：

| 当前 MAML RVA | 原生读取与后续处理 |
| --- | --- |
| 0x4bd8d4 | 调用 HyperMaterialHolder::load |
| 0x4bd90c / 0x4bd954 | 引用 ViewOutlineProvider，按 19 字节比较子标签 |
| 0x4bdd44 / 0x4be238 | 比较 rect / path 两种类型；分别写入类型 1 / 2 |
| 0x4be37c / 0x4be388 | 引用 pathData，helper 0x5d5ad0 以长度 8 尾调 Element::get_attribute |
| 0x4be3b0 / 0x4be6b4 | 将 pathData 解析成表达式，再创建 RootExpression |
| 0x4be6cc | 读取 scaleToFit；当前路径解析为数值枚举，不能猜写字符串 fit/center |
| 0x53fd74 | FFIOutlineProperties::from_data 转换类型、边界、半径、路径指针/长度和缩放策略 |

这为字形轮廓裁剪提供了具体候选入口。`pathData` 是表达式属性，静态 SVG 路径
也要作为字符串表达式处理；不能把裸 `M...L...Z` 当成已验证的有效表达式。
上述 RVA 用于复现反汇编，不作为新运行时 Hook 的固定地址。

桌面 Dart AOT 中 0x13662c8 所在轮廓计算例程包含实际 `[MAML][CLIP] outline
computed` 输出（字符串引用 0x136663c）；邻近 0x136700c 例程使用 SVG 路径解析
逻辑（调用 0x194a83c、0x194b344），并有路径缩放处理。这比普通 IconConfig
路径字符串更接近目标 Layer，但**尚未核对完整 Flutter widget 嵌套与裁剪顺序**，
不能宣称已经证明玻璃背景会被该轮廓裁剪。

### 引擎 SDF/Mask 不等同于文字遮罩

补充脚本现可复现两组直接调用：

- `Canvas::SaveLayer` 的 0xc2aadc 调用 `DrawHyperMaterial`（0xc314e8），后者的
  0xc31620 调用 `FindGlassFilter`。当前确认的是背景层材质路径。
- `MiGlassContents::Render` 在 0xd5d9e4 / 0xd5dbf0 调用
  `MiRenderHelper::GetOrCreateSdfTexture`（0xd6429c）。该函数接受矩形和三个
  float，按几何尺寸/半径等值构造缓存键；查看这条实现没有获得文字路径输入的证据。
- 同一 Render 的 0xd5de60 / 0xd5e010 调用 `TryCacheCreateLightMask`，
  V13 shader 的 `BindUMaskTex` 调用位于 0xd5f290。该遮罩与 IconLightMaskParams
  及光照缓存相关，不能凭 UMaskTex 名字认定为任意字形 alpha 输入。

直接调用扫描不涵盖间接/虚调用与内联，因此结论是**已定位路径没有证明文字 SDF**，
而非断言整套引擎绝不支持。即使外部 ClipPath 可以裁出数字，圆角矩形 SDF 也
不会因此自动变为数字笔画距离场；需要逐项验收洞口透明、笔画边缘高光与折射方向。

完整调用点、当前库散列及轮廓属性引用已纳入 `inspect-maml-material.py` 输出。
原始反汇编保留在忽略目录 `reference/desktop-miglass-20261021/`，不提交原始库。
当前没有可交付的玻璃时钟 APK；不加一个暂未接通的设置开关，也不修改原字体、
分钟更新器或帧率。下一阶段的最小验证和性能边界见
[原型计划](../plans/DESKTOP_CLOCK_MIGLASS.md)。

## 2026-10-21：字形材质实验实现（尚未在手机运行）

新增 `clock_glass.cpp` / `clock_glass_render.h`，采用普通 DateTime 的实际排版字形，
暂不注入先前研究的矩形 Layer。`TextScreenElement::draw_text` 参数由调用点
0x4854a4..0x485504 核对为 x0=self、x1=canvas、s0=offset。元素名称由
0x48668c 的 marquee_log_name 核对：存在位 self+0x4d4 bit0，LeanString 在 +0x430；
其 as_str 0x41b6b4 返回借用的 x0/x1 字符串与长度。

Flutter ParagraphPainter::drawTextBlob 0xb373e4 接收 x0=this、x1=&blob、
x2=&variant、s0/s1=坐标；variant index +0x50 为 1 时使用 DlPaint 索引。
Painter+8 是 Builder，+0x10 指向 vector<DlPaint>，+0x18 为 Impeller 标记。
DlPaint 大小 0x138。原函数自身在 colorSource 非空时已走 GetPath → DrawPath；
本原型复用其同引擎字形轮廓提取，保留 blob bounds 的坐标补偿。

重要的 LTO ABI 差异：GetPath 0xb7bc14 用 x0 传出 16 字节 SkPath、x1 传 blob；
名为 SkPath::~SkPath 的 0x87b008 实际接收内部 SkPathRef*，不是 SkPath*。
DlPath 构造 0xc271bc / 析构 0x7ed600 使用正常对象地址；不由模块自行析构外来 STL。

SetAttributesFromPaint 0x7e0328 的 PassBlur 位为 19、混合参数位 20、BloomStroke 位 27。
普通 DrawText / DrawPath 的属性掩码不包括这些位，故单独改 paint 后调用原 DrawText
不能可靠带入材质。实现使用字形 DrawPath，并补齐属性位。Builder 材质字段在对应
DlPaint 标量字段偏移 +0xc8；保存其原材质并显式记录恢复，因为 save/restore 不恢复
画笔属性。不修改原 DlPaint，不从自定义复制对象析构借用 shared_ptr。

MAML Build ID dc795c18e8fee60294ebb8040be92bc94e4ed4c05；Flutter Build ID
c014a14bb9ddab4431a887c98c6d56ce305b0573。15 个入口/布局相关函数全段 FNV64
代码契约见 `clock_glass_profile.h`；主机测试对真实 ELF 和逐项代码变异运行生产校验。
这只是静态 ABI 与回退行为的验证，不证明背景纹理可用、TLS 绘制链在设备同步、
BloomStroke 能按任意字形计算高光，或等效系统锁屏 MiGlass。验证计划另见阶段文档。

## v1 实机失败后的消费链核对与 v2 修正

用户反馈 v1 两档均为不透明且锯齿的字形。不能把上节“字形 DrawPath 已记录材质属性”
解释为材质已经被引擎消费。普通字形进入 DlDispatcherBase::drawPath 0xc487a0，
其 SimplifyOrDrawPath 0xc487d0 只能把特定矩形路径简化为 DrawRect；普通字形继续
Canvas::DrawPath。该字形分支没有走 PassBlur 的窗口纹理消费链。

可消费的链在 DlDispatcherBase::drawRect 0xc46b88：+0x51 判断 PassBlur；取 Canvas
与 ContentContext 的 PassBlurState；0xc46d1c 调用 ConsumePendingAHB 0xc310e8；
无状态或纹理则直接跳过绘制。0xc46f98 读取 radius，0xc46fec 调用
MiRefineGaussianBlurHelper::getBluredTexture 0xd4e328，随后经 MiBlendHelper 和
TextureContents 绘制。这里存在实际的高斯纹理分支，不只是画笔字段。尚不能仅凭
这些静态证据断言默认经典时钟所在绘制上下文在真机一定有背景纹理。

v2 改为 GetPath → AA ClipPath（交集）→ PassBlur DrawRect；复制画笔的 AA 位显式
开启；只使用一层显式 20% source-over tint（随原字色），其余层为 0xff 禁用。
纯背景模糊在纯色壁纸上无法辨识，保留少量着色以保证对比度；不会继承默认不透明混合色。Builder 的原 AA 和材质均显式记录恢复。
纹理由 native 拥有和导入，不跨 libhwui 交换 C++ 对象。半透明文字不进入新分支，
避免 PassBlur 纹理不消费原文字 alpha 而破坏淡入淡出。

新增仅匹配本功能标记的矩形回放 Hook，按原函数同样的 Canvas/上下文/状态链检查
纹理，并在缺失时暂时关闭该次 PassBlur 来保留可见文字。状态/纹理指针均不保留。
矩形记录中使用固定专用 blendId，同时作路由和原生缓存键。复查 getBluredTexture
0xd4e328 的查找键为 id/namespace，0xd4e67c..0xd4e6d8 比较半径/尺寸等，失配会
替换同槽；因此不能改用默认 id 100，与系统其他半径互相驱逐。固定键在该 Context 中
复用一个专用原生缓存槽，不按帧/实例生成新 id。诊断仅按首次状态唤醒既有工作线程，至多三次；
工作线程记录有无背景源，不将“记录了 clip”当作“已成功显示模糊”。

BloomStroke 有独立的矩形 drawBloomStroke 接口 0xc50dc8；简单设其画笔字段不能
得到字形 MiGlass。柔光玻璃档暂保留系统原绘制，并在 UI 明示未接通。字形 SDF/遮罩
与原生玻璃材质的真正连接仍未实现，不能用一个被裁剪的矩形边缘冒充字形折射。

ABI 契约更新为 19 项，并纳入 ClipPath 本体、DrawRect 回放与 AHB 导入。主机测试
验证缺失背景回退及标记/AA 恢复；不验证 GPU 采样、实际背景可用性或视觉质量。
手机本轮只读读取日志；未安装修改后的版本。


## 2026-10-22：v2 模糊确认与原文字 alpha 遮罩

用户已确认 v2 模糊成功，但仍有锯齿，且偏暗、数字不清晰。此前的 AA ClipPath
请求不保证当前 stencil/MSAA 管线得到与 native text atlas 相同的覆盖率；具体 GPU
硬边成因尚未采样，不将这项推测写成已证实的驱动结论。

v3 不再调用 GetPath/DlPath/ClipPath。ParagraphPainter 原函数在 source 的
colorSource(+0xf8) 为空时走原生文字管线。核对该函数仅访问 painter+8 的 builder、
+0x10 的 vector<DlPaint> 和 +0x18 的 Impeller 标志；variant+0x50==1 代表索引。
模块以栈上的同步借用 painter/vector/paint/variant 视图调用原 trampoline，白色文字
保留原 blob、字体轴和坐标。视图不运行外来 STL/shared_ptr 析构，不修改原对象。

新增调用 DisplayListBuilder::SaveLayer 0x7cbb94，签名为 optional<Rect> const&、
DlPaint*、DlImageFilter*、optional<int64>、optional<uint64>。Rect 的 present byte
在 +0x10；两个可空 64 位标量分别以 x4/x5、x6/x7 传递。使用空画笔/滤镜和未出现的
标量，使离屏结果按普通 SrcOver 合成；层 bounds 为原 blob bounds+坐标并外扩 1。
该 wrapper 调用 0x7d0c40；后者 LTO 后 x2 接收 options 指针，因此不能把其符号名
当成按值 ABI 直接调用。restore 调用完整本体 0x7ca500，不只校验跳转 thunk。

先绘制白色原文字 alpha，再用全 bounds 的 PassBlur 矩形执行 SrcIn（5），使采样的
预乘颜色/alpha 乘以文字覆盖；矩形涵盖空白部分，从而保留数字洞口透明。
在原生 PassBlur 回放分支 0xc472b0 读取 dispatcher+0x44 的混合模式，并在
0xc472ec 写入 entity，已确认该值进入纹理绘制分支。这是静态调用链证据，最终
atlas 覆盖与混合结果仍需真机验证。缺背景时原有回退仍执行 SrcIn 的原字色矩形。

原生 profile 更新为 13 项：移除未再使用的路径辅助函数，加入 SaveLayer wrapper、
SaveLayer 本体、Restore 本体；保留文字、属性、矩形和 AHB 导入全函数契约。
初始化仍为三处事务 Hook，不增加系统进程范围。文字 alpha 动画仍走原绘制。

显色默认从 20% 提到 36%，半径默认 12；UI 暴露半径与显色强度。
新 i32 参数经现有可信 SystemUI 广播、可选 Bundle_get_i32 读取，不引入新服务。
主机覆盖可选接口缺失、可信/非可信发送者、错误 Result 和参数归一化。

性能变化是移除每次字形轮廓提取/包装分配，增加时钟范围内一个 GPU 合成层。
绘制不读文件、不发 IPC、不解析规则、不逐帧日志，没有新增定时刷新；配置缓存只在
变化时写。高斯输入仍可能为全窗口纹理，新增层也有 GPU 成本；尚无 Perfetto、
FrameTimeline 或功耗对照，不能声称总体性能改善或锯齿已在实机修复。
