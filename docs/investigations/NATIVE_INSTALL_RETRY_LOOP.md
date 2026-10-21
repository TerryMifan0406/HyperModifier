# 原生设置桥安装失败后的反馈循环

## 反馈与代码核对

2026-10-19 用户提供截图：另一模块先占用 receiver 后，报告同一 TID 持续交替
出现 Dobby 日志和 `MHMDesktopClock: BRIDGE install result=1 ready=0`，各约
6 万行，线程占满一个 CPU 核。次数、CPU 时间为对方报告，未在其设备独立测量。

代码确认有安装反馈环。当前 `onLoaded()` 只唤醒 worker，不直接调用
`install()`；但安装中的 `dlopen(RTLD_NOW | RTLD_NOLOAD)` 仍可能触发 LSPosed
加载通知，重新设置 `installRequested` 并写 eventfd。receiver 失败时原实现仅靠
`originalReceiver != nullptr` 判断是否完成，没有独立的已尝试状态。

启动阶段虽有 60 秒等待上限，之后还会进入无时间间隔的事件处理循环。
只要每次失败安装都再次排队一次安装，队列就不会空，不能依靠启动超时停止。
Calendar Hook 在后端失败且未提供 trampoline 时也有同类重复尝试条件。

## 修复

- receiver 和 Calendar 在调用后端前记录进程内已尝试状态。后端失败、符号
  缺失、句柄无法获取、缺少回滚 API、缺少 trampoline 或 ABI 不兼容均进入
  终态；后续库通知、设置请求和诊断读取不能重新安装这个失败目标。
- worker 的完成条件使用这些终态。被其他模块占用时保留失败状态，并只记录
  一次失败，不抢占目标，也不调用 unhook 去移除其他模块的 Hook。
- 安装过程有 TLS scope；同步 `dlopen` / `dlclose` / Hook 引发的加载回调不再
  给当前安装排队。即使通知异步送到其他线程，每个相关 Runtime 库也只接受
  首次外部通知，防止同一库/句柄反复回调形成第二条反馈路径。
- 原生库通知去重是固定的四位原子标记，不持有 View/Context、不维护增长的
  handle 集合。重复通知只读已见标记，无锁、分配、磁盘或 IPC。
- 首次真正晚加载仍可在启动等待超时后尝试。显式设置工作不经过库通知去重，
  原有搜索功能延迟启用、设置热刷新和小组件独立降级路径保留。
- 后端报告成功却未提供 trampoline 时，只尝试回滚这次明确属于本模块的安装。
  无回滚 API 则不尝试安装 receiver/Calendar；避免留下无法转发的 detour。

四个目标是该桌面进程常驻的 Runtime 库；成功 Hook 和失败终态均按进程管理。
动态卸载/重新加载这些库、释放其他模块占用后再次安装，需要重启桌面进程，
不会以常驻重试来探测其他模块是否已经释放地址。

## 验证与性能范围

`python3 tools/tests/native-install-loop-test.py` 将生产 `desktop_clock.cpp` 的
安装、worker、加载回调及状态直接编入主机测试，仅替换 Android/ELF/loader/
Hook/等待等平台依赖。ASan/UBSan 开启；pending wake 不推进假时钟，反馈
循环会被判为持续自唤醒，不能靠假超时悄悄通过。

验证包括：

- receiver 被占用且后端返回 1；即使失败时写入非空 original 指针，状态仍失败。
- 正常安装、缺少导出、句柄不可用、无回滚 API、成功但缺少 trampoline、
  Calendar profile 拒绝和 Calendar 后端失败。
- 强制 10,000 次安装及重复 NOLOAD 通知，receiver/Calendar 后端各调用一次，
  receiver 安装日志一条，dlopen 共三次且全部配对关闭，无外部目标回滚。
- 8 个线程共 80,000 次重复通知只接受一次；TLS 不屏蔽其他线程的真实首次通知。
- 首次库加载超过 60 秒仍可安装；安装完成后显式设置工作仍被处理；原 receiver
  转发路径保留。

性能检查覆盖进程启动、加载回调、安装排队、日志、锁和持久工作线程。结构上
消除了失败后的重复后端调用、NOLOAD 和自唤醒；未增加线程、轮询或计时器。
原有 500 ms / 60 秒的有限启动等待及阻塞诊断 socket 保留。绘制、触摸、
动画、GPU 和设置载体内容均未改动。上述为代码审查及主机确定性回归，不是
对反馈设备的 CPU、帧率或耗电前后测量。

## 交付记录

Release 构建（Android Native、R8、Lint）和 v2 签名验证通过，327 项 JVM
测试通过。时钟格式 ASan/UBSan、4608 组搜索入口、96 组 picker 入口、搜索
结构定位的 128 组重定位/267 组破坏用例及两份真实镜像回归均通过。

修复包：`app/build/outputs/apk/release/MyHyperModifier-v1.5.2-native-install-loop-fix.apk`。
SHA-256：`1178d52d0107ee981227052ee61adc5789be4577a37f677c7555e5c82671a5ca`。
交付时 ADB 无设备连接，未安装到设备，未在反馈者的多模块组合上复测 CPU。
安装包后需由用户手动重启系统桌面加载新的原生库；未执行 ADB 输入、设置
修改或进程重启，遵守此前“仅只读与 Release 安装”的设备操作约束。
