package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class UnifiedGlassSettingsTest {
    @Test fun copiedBaselineRemainsIndependentOfItsSourceAndEnableSwitch() {
        val original = ModifierSettings()
        val scope = GlassParameterScope.Unified
        val source = GlassParameterScope.HeadsUp.values(original, false)
        val copied = scope.setParameters(original, false, source)
        source[19] = 0f
        assertFalse(copied.unifiedGlassEnabled)
        assertEquals(64f, scope.values(copied, false)[19], 0f)
        assertEquals(original.headsUpGlassParameters, copied.headsUpGlassParameters)
        assertEquals(original.shadeCardGlassParameters, copied.shadeCardGlassParameters)
        val enabled = scope.setEnabled(copied, true)
        assertTrue(scope.enabled(enabled))
        assertFalse(scope.enabled(enabled.copy(disableShadeGlassHooks = true)))
        assertArrayEquals(scope.values(enabled, false), scope.values(enabled, true), 0f)
        assertEquals(enabled.unifiedGlassParameters, scope.systemDefault(enabled).unifiedGlassParameters)
    }

    @Test fun disabledConfigEditsDoNotTriggerRefreshAndEqualOpticsDoNotReplay() {
        val refresh = GlassRecipeRefreshState()
        val original = UnifiedGlassPolicy.sanitize(ShadeCardGlassPolicy.defaults())
        val different = original.copyOf().apply { this[19] = 10f }
        assertTrue(refresh.changed(false, original))
        assertFalse(refresh.changed(false, different))
        assertTrue(refresh.changed(true, different))
        assertFalse(refresh.changed(true, different.copyOf()))
        assertTrue(refresh.changed(false, different))
    }
}
