# 锁屏到桌面时钟过渡实验归档

2026-10-21 用户反馈一镜到底效果不佳，要求回滚并保留版本，转向研究桌面原生
时钟的 MiGlass 材质。当前生产分支回到实验前 `9d98805` 的实现，保留更早的
桌面搜索布局兼容、时钟前导零和其他模块修复。

保留入口：

- Git 分支 `codex/archive-clock-transition-mvp-v10`，指向 `eb7ee93`。
- 实验设计、设备证据和各阶段记录在该分支的
  `docs/plans/LOCKSCREEN_DESKTOP_CLOCK_TRANSITION.md`。
- 签名 Release 保留在仓库外
  `/Users/aritxonly/Builds/MyHyperModifier/2026-10-21/HyperModifier-v1.5.2-clock-native-font-v10.apk`。
  SHA256 `7894a504df3db8a53816c64f8cebe091bf255a8631374d3da6c04a0cd93f059c`。
  APK 未提交 Git，已通过飞书发送给用户；不依赖临时目录恢复代码。

2026-10-21 按用户要求，将根目录全部 7 个 APK 移至上述仓库外目录，逐一校验
SHA256 后移除根目录副本；后续交付产物继续放仓库外，不再发送飞书。Gradle
中间产物使用常规 `app/build/` 目录，不在仓库根目录额外复制 APK。

查看归档源码可使用 `git show codex/archive-clock-transition-mvp-v10:<path>`。
未来恢复建议从归档分支建立新的工作分支，在当前系统版本重新验证接口和效果；
保留分支不代表其效果已达到可发布水平。

本次完整移除实验新增的 10 个 SystemUI 观察 Hook、3 个桌面 Dart Hook、
临时采样窗口、字形副本/字体 worker、页码查询和过渡设置；不在主分支仅隐藏
开关而让这些实验继续运行。旧偏好即使保留也不会再读取或生效。

手机端遵循只读约束，构建回滚 APK 不等于已在手机回滚。用户安装回滚包后需
手动重启系统界面与系统桌面（或重启手机），才能卸载旧进程里的实验 Hook。
仅重启 SystemUI 不会卸载桌面进程的旧 Dart Hook 和其内存开关。

回滚验证：`:app:assembleRelease :app:test` 成功（2m33s），390 项 JVM 测试
0 失败/错误/跳过。Release v2 签名通过。回滚 APK SHA256：
`c439ea0c3f4fb379fdd88bacbe8b766c1cc7bdf13d1ca0ded21373ab2d35a9e6`。
