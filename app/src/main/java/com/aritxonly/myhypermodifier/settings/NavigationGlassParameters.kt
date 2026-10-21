package com.aritxonly.myhypermodifier

import com.aritxonly.deadliner.model.AdvancedMaterialFineTuning
import com.aritxonly.deadliner.model.AdvancedMaterialTuning

/** Versioned bottom-navigation recipe. Parsing is confined to settings changes. */
internal data class NavigationGlassParameters(
    val blurRadius: Float = AdvancedMaterialTuning.DefaultBlurRadius,
    val fineTuning: AdvancedMaterialFineTuning = AdvancedMaterialFineTuning.Default,
) {
    fun normalized() = copy(blurRadius = AdvancedMaterialTuning.normalizeBlurRadius(blurRadius),
        fineTuning = fineTuning.normalized())
    fun encode(): String {
        val value = normalized()
        return listOf("1", value.blurRadius.toString(),
            value.fineTuning.saturationMultiplier.toString(),
            value.fineTuning.noiseCoefficient.toString(),
            value.fineTuning.brightness.toString(),
            value.fineTuning.contrast.toString(),
            value.fineTuning.topBarTintAlpha.toString(),
            value.fineTuning.navigationTintAlpha.toString(),
            value.fineTuning.dialogBackgroundAlpha.toString(),
            value.fineTuning.dialogBlurRadiusDp.toString(),
            value.fineTuning.bottomSheetBackgroundAlpha.toString(),
            value.fineTuning.bottomSheetBlurRadiusDp.toString(),
            value.fineTuning.glassBlurRadiusMultiplier.toString(),
            value.fineTuning.glassNoiseMultiplier.toString(),
            value.fineTuning.glassTintAlphaMultiplier.toString(),
            value.fineTuning.glassTintLightGray.toString(),
            value.fineTuning.glassTintDarkGray.toString(),
            (if (value.fineTuning.refractionEnabled) "1" else "0"),
            value.fineTuning.refractionHeightDp.toString(),
            value.fineTuning.refractionAmountDp.toString(),
            value.fineTuning.refractionDepthEffect.toString(),
            value.fineTuning.chromaticAberrationDp.toString(),
            value.fineTuning.edgeHighlightAlpha.toString(),
            value.fineTuning.edgeHighlightGray.toString(),
            value.fineTuning.darkHighlightMultiplier.toString(),
            value.fineTuning.fallbackEdgeWidthDp.toString(),
            value.fineTuning.fallbackLightAlpha.toString(),
            value.fineTuning.fallbackDarkAlpha.toString(),
            (if (value.fineTuning.postRefractionBlurEnabled) "1" else "0"),
            value.fineTuning.postRefractionBlurRadiusDp.toString(),
        ).joinToString(",")
    }
    companion object {
        val Default = NavigationGlassParameters()
        fun decode(raw: String): NavigationGlassParameters {
            if (raw.isBlank() || raw.length > 4096) return Default
            return runCatching {
                val parts = raw.split(',')
                require(parts.size == 30 && parts[0] == "1")
                fun number(index: Int) = parts[index].toFloat().also { require(it.isFinite()) }
                fun flag(index: Int): Boolean { require(parts[index] == "0" || parts[index] == "1"); return parts[index] == "1" }
                NavigationGlassParameters(number(1), AdvancedMaterialFineTuning(
                    saturationMultiplier = number(2),
                    noiseCoefficient = number(3),
                    brightness = number(4),
                    contrast = number(5),
                    topBarTintAlpha = number(6),
                    navigationTintAlpha = number(7),
                    dialogBackgroundAlpha = number(8),
                    dialogBlurRadiusDp = number(9),
                    bottomSheetBackgroundAlpha = number(10),
                    bottomSheetBlurRadiusDp = number(11),
                    glassBlurRadiusMultiplier = number(12),
                    glassNoiseMultiplier = number(13),
                    glassTintAlphaMultiplier = number(14),
                    glassTintLightGray = number(15),
                    glassTintDarkGray = number(16),
                    refractionEnabled = flag(17),
                    refractionHeightDp = number(18),
                    refractionAmountDp = number(19),
                    refractionDepthEffect = number(20),
                    chromaticAberrationDp = number(21),
                    edgeHighlightAlpha = number(22),
                    edgeHighlightGray = number(23),
                    darkHighlightMultiplier = number(24),
                    fallbackEdgeWidthDp = number(25),
                    fallbackLightAlpha = number(26),
                    fallbackDarkAlpha = number(27),
                    postRefractionBlurEnabled = flag(28),
                    postRefractionBlurRadiusDp = number(29),
                )).normalized()
            }.getOrDefault(Default)
        }
    }
}
