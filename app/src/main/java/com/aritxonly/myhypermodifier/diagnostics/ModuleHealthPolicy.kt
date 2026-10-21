package com.aritxonly.myhypermodifier

internal enum class ModuleHealthIndicator { Ready, Warning, Unavailable, Pending }

internal data class ModuleHealth(
    val indicator: ModuleHealthIndicator,
    val title: String,
    val summary: String,
)

/** Uses existing connection and diagnostic snapshots; never probes target processes. */
internal object ModuleHealthPolicy {
    fun resolve(
        hooksEnabled: Boolean,
        compatibilityMode: Boolean,
        connected: Boolean,
        supportedApi: Boolean,
        modifiedScopes: Int,
        activeScopes: Int,
        features: List<HookDiagnostic>,
    ): ModuleHealth {
        fun unavailable(title: String, summary: String) =
            ModuleHealth(ModuleHealthIndicator.Unavailable, title, summary)
        if (!hooksEnabled) return unavailable("模块 Hook 已停用", "功能设置已保留，可重新启用 Hook")
        if (!connected) return unavailable("模块未激活", "请确认已在 LSPosed 中启用模块")
        if (!supportedApi) return unavailable("LSPosed API 版本过低", "需要 API ${ModuleFrameworkState.MIN_SUPPORTED_API} 或更高版本")
        if (compatibilityMode) return unavailable("SystemUI 兼容模式", "系统界面 Hook 已跳过")
        if (features.any { it.feature == "自动兼容模式" && it.warning &&
                it.packageName in systemUiPackages }) {
            return unavailable("SystemUI 自动保护中", "启动保护已触发，进入兼容模式，可手动重试")
        }
        val installs = features.filter { it.feature != "自动兼容模式" }
        val failures = installs.count { it.warning }
        // Empty or missing reports are unknown, never evidence that every Hook failed.
        if (installs.isNotEmpty() && failures == installs.size) {
            return unavailable("已检测 Hook 均失败", "$failures 项功能安装失败，请查看诊断记录")
        }
        if (modifiedScopes > 0 && activeScopes == 0) {
            return unavailable("作用域未激活", "已修改的作用域均未激活，请检查 LSPosed")
        }
        if (failures > 0) return ModuleHealth(ModuleHealthIndicator.Warning,
            "部分 Hook 失败", "检测到 $failures 项兼容问题")
        if (activeScopes < modifiedScopes) return ModuleHealth(ModuleHealthIndicator.Warning,
            "作用域激活不完全", "$activeScopes/$modifiedScopes 个已修改作用域激活")
        if (modifiedScopes == 0) return ModuleHealth(ModuleHealthIndicator.Pending,
            "尚未配置修改", "从下方选择需要修改的功能")
        return ModuleHealth(ModuleHealthIndicator.Ready, "作用域已激活",
            if (installs.isEmpty()) "尚无功能安装记录，重启目标应用后查看" else "未发现 Hook 安装异常，实际效果请在对应界面验证")
    }

    private val systemUiPackages = setOf("com.android.systemui", "miui.systemui.plugin")
}
