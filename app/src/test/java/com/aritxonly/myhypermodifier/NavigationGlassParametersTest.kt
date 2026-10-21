package com.aritxonly.myhypermodifier

import com.aritxonly.deadliner.model.AdvancedMaterialFineTuning
import com.aritxonly.deadliner.model.AdvancedMaterialLevel
import com.aritxonly.deadliner.model.AdvancedMaterialTuning
import org.junit.Assert.*
import org.junit.Test

class NavigationGlassParametersTest {
    @Test fun freshAndResetRecipesUseLatestDeadlinerDefaults() {
        val defaults = NavigationGlassParameters.decode("")
        assertEquals(84f, defaults.blurRadius)
        assertEquals(0.9f, defaults.fineTuning.edgeHighlightAlpha)
        assertEquals(0.98826814f, defaults.fineTuning.edgeHighlightGray)
        assertEquals(0.70237434f, defaults.fineTuning.dialogBackgroundAlpha)
        assertEquals(0.69986033f, defaults.fineTuning.bottomSheetBackgroundAlpha)
        assertEquals(defaults, defaults.normalized())
    }
    @Test fun extendedBlurRangeRetainsStrongestSaturationInsteadOfWrapping() {
        val saturation = AdvancedMaterialLevel.entries.last().blurSaturation
        assertEquals(saturation, AdvancedMaterialTuning.saturationForBlurRadius(100f))
        assertEquals(saturation, AdvancedMaterialTuning.saturationForBlurRadius(168f))
    }
    @Test fun roundTripPreservesAdvancedOpticsAndFallbackSwitches() {
        val value = NavigationGlassParameters(81f, AdvancedMaterialFineTuning.Default.copy(
            noiseCoefficient = 0.17f, navigationTintAlpha = 0.31f,
            glassBlurRadiusMultiplier = 0.88f, refractionEnabled = false,
            chromaticAberrationDp = 3.5f, fallbackEdgeWidthDp = 1.4f,
            postRefractionBlurEnabled = false,
        ))
        assertEquals(value, NavigationGlassParameters.decode(value.encode()))
    }
    @Test fun malformedUnknownAndNonFiniteRecipesFailClosedToCanonicalDefault() {
        val valid = NavigationGlassParameters.Default.encode()
        for (text in listOf("", "2" + valid.drop(1), valid + ",0", valid.split(',').toMutableList().apply { this[1] = "NaN" }.joinToString(","),
            valid.split(',').toMutableList().apply { this[1] = "Infinity" }.joinToString(","), "x".repeat(4097))) {
            assertEquals(NavigationGlassParameters.Default, NavigationGlassParameters.decode(text))
        }
    }
    @Test fun importedOutOfRangeValuesNormalizeBeforeShaderUse() {
        val raw = NavigationGlassParameters(900f, AdvancedMaterialFineTuning.Default.copy(
            refractionAmountDp = 1000f, glassBlurRadiusMultiplier = -5f,
        )).encode()
        val actual = NavigationGlassParameters.decode(raw)
        assertEquals(168f, actual.blurRadius)
        assertEquals(32f, actual.fineTuning.refractionAmountDp)
        assertEquals(0.1f, actual.fineTuning.glassBlurRadiusMultiplier)
    }
    @Test fun backupRoundTripPreservesRecipeAndExistingLiftKey() {
        val values: Map<String, Any> = mapOf("navigation_glass_parameters" to NavigationGlassParameters.Default.encode(),
            "hyper_glassify_hidden_navigation_lift" to 12f)
        val restored = SettingsBackupCodec.decode(SettingsBackupCodec.encode(values), values)
        assertEquals(values, restored)
        assertEquals(NavigationGlassParameters.Default, NavigationGlassParameters.decode(restored["navigation_glass_parameters"] as String))
    }
}
