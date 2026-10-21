package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleHealthPolicyTest {
    private fun event(feature: String, warning: Boolean, pkg: String = "com.android.systemui") =
        HookDiagnostic(1L, pkg, feature, "", warning)

    private fun health(
        events: List<HookDiagnostic> = emptyList(),
        enabled: Boolean = true,
        compatibility: Boolean = false,
        connected: Boolean = true,
        supported: Boolean = true,
        modified: Int = 2,
        active: Int = 2,
    ) = ModuleHealthPolicy.resolve(enabled, compatibility, connected, supported, modified, active,
        HookDiagnosticPolicy.latestFeatures(events))

    @Test fun unavailableStatesOverrideHealthyScopesAndOldReports() {
        val installed = listOf(event("glass", false))
        assertEquals(ModuleHealthIndicator.Unavailable, health(installed, enabled = false).indicator)
        assertEquals(ModuleHealthIndicator.Unavailable, health(installed, connected = false).indicator)
        assertEquals(ModuleHealthIndicator.Unavailable, health(installed, supported = false).indicator)
        assertEquals(ModuleHealthIndicator.Unavailable, health(installed, compatibility = true).indicator)
        assertEquals(ModuleHealthIndicator.Unavailable, health(installed, active = 0).indicator)
    }

    @Test fun partialFailuresWarnAndAllReportedFailuresShowUnavailable() {
        assertEquals(ModuleHealthIndicator.Warning,
            health(listOf(event("glass", true), event("clock", false))).indicator)
        assertEquals(ModuleHealthIndicator.Unavailable,
            health(listOf(event("glass", true), event("clock", true))).indicator)
    }

    @Test fun automaticProtectionIsFatalEvenIfOtherHooksSucceeded() {
        assertEquals("SystemUI 自动保护中",
            health(listOf(event("自动兼容模式", true), event("clock", false))).title)
        assertEquals(ModuleHealthIndicator.Unavailable,
            health(listOf(event("自动兼容模式", true, "miui.systemui.plugin"))).indicator)
    }

    @Test fun successfulRetrySupersedesProtectionAndFeatureFailures() {
        assertEquals(ModuleHealthIndicator.Ready, health(listOf(
            event("自动兼容模式", false), event("glass", false),
            event("自动兼容模式", true), event("glass", true),
        )).indicator)
    }

    @Test fun missingReportsAndNoConfiguredFeaturesNeverMeanHookFailure() {
        assertEquals(ModuleHealthIndicator.Ready, health().indicator)
        assertEquals(ModuleHealthIndicator.Ready, health(listOf(event("模块注入", false))).indicator)
        assertEquals(ModuleHealthIndicator.Pending, health(modified = 0, active = 0).indicator)
        assertEquals(ModuleHealthIndicator.Warning, health(active = 1).indicator)
    }

    @Test fun successfulGuardCheckDoesNotHideAllFailedHooks() {
        assertEquals(ModuleHealthIndicator.Unavailable,
            health(listOf(event("自动兼容模式", false), event("glass", true))).indicator)
    }
}
