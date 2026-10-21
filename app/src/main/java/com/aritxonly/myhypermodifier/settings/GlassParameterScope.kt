package com.aritxonly.myhypermodifier

/** One editor, with separate persisted recipes and JSON formats for each target surface. */
internal enum class GlassParameterScope(val presetFormat: String) {
    Unified("myhypermodifier-unified-glass"),
    HeadsUp("myhypermodifier-heads-up-glass"),
    ShadeCards("myhypermodifier-shade-card-glass");

    fun defaults(dark: Boolean): FloatArray = when (this) {
        HeadsUp -> if (dark) HeadsUpGlassParameters.darkDefault else HeadsUpGlassParameters.regularDefault
        Unified, ShadeCards -> ShadeCardGlassPolicy.defaults()
    }

    fun enabled(settings: ModifierSettings): Boolean = when (this) {
        Unified -> settings.unifiedGlassEnabled && !settings.disableShadeGlassHooks
        HeadsUp -> settings.headsUpGlassParametersEnabled
        ShadeCards -> settings.shadeCardGlassParametersEnabled && !settings.disableShadeGlassHooks
    }

    fun serialized(settings: ModifierSettings, dark: Boolean): String = when (this) {
        Unified -> settings.unifiedGlassParameters
        HeadsUp -> if (dark) settings.headsUpGlassDarkParameters else settings.headsUpGlassParameters
        ShadeCards -> settings.shadeCardGlassParameters
    }

    fun values(settings: ModifierSettings, dark: Boolean): FloatArray =
        HeadsUpGlassParameters.parseSerializedOrDefault(serialized(settings, dark), defaults(dark))

    fun setEnabled(settings: ModifierSettings, enabled: Boolean): ModifierSettings = when (this) {
        Unified -> settings.copy(unifiedGlassEnabled = enabled)
        HeadsUp -> settings.copy(headsUpGlassParametersEnabled = enabled)
        ShadeCards -> settings.copy(shadeCardGlassParametersEnabled = enabled)
    }

    fun setParameters(settings: ModifierSettings, dark: Boolean, values: FloatArray): ModifierSettings {
        val serialized = HeadsUpGlassParameters.serialize(values)
        return when (this) {
            Unified -> settings.copy(unifiedGlassParameters = HeadsUpGlassParameters.serialize(UnifiedGlassPolicy.sanitize(values)))
            HeadsUp -> if (dark) settings.copy(headsUpGlassDarkParameters = serialized)
                else settings.copy(headsUpGlassParameters = serialized)
            ShadeCards -> settings.copy(shadeCardGlassParameters = serialized)
        }
    }

    fun moduleDefault(settings: ModifierSettings): ModifierSettings = when (this) {
        HeadsUp -> ModifierSettingsPresets.headsUpGlassModuleDefault(settings)
        Unified, ShadeCards -> setEnabled(setParameters(settings, false, defaults(false)), true)
    }

    fun systemDefault(settings: ModifierSettings): ModifierSettings = when (this) {
        HeadsUp -> ModifierSettingsPresets.headsUpGlassSystemDefault(settings)
        Unified, ShadeCards -> setEnabled(settings, false)
    }

    fun importPreset(settings: ModifierSettings, preset: HeadsUpGlassPresetJson.Preset): ModifierSettings {
        val configured = if (this == HeadsUp) setParameters(setParameters(settings, false, preset.regular), true, preset.dark)
            else setParameters(settings, false, preset.regular)
        val next = setEnabled(configured, true)
        // Shared blur belongs to its own page. Legacy presets still parse, but their blur
        // fields must not change other surfaces while importing a material recipe.
        return next
    }
}
