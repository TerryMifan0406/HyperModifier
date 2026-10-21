package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun UnifiedGlassSettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    UnifiedGlassSettings(settings, update)
}

/** Shares the existing material slider and exact-value dialog, without exposing color slots. */
@Composable
private fun UnifiedGlassSettings(settings: ModifierSettings, update: (ModifierSettings) -> Unit) {
    var selectingBase by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<HeadsUpGlassParameterTarget?>(null) }
    val scope = GlassParameterScope.Unified
    val indices = remember { UnifiedGlassPolicy.indices() }
    SettingsSection(topLabel = "统一玻璃材质") {
        SettingsSwitchItem(
            "统一光学参数",
            "作用域内的原生 Glass 共用边缘、反射和折射参数",
            settings.unifiedGlassEnabled,
            { update(settings.copy(unifiedGlassEnabled = it)) },
            enabled = !settings.disableShadeGlassHooks,
        )
        NavigationSettingItem(
            title = "从已有材质载入基准",
            summary = "覆盖下方 7 项参数，再独立微调；载入不会自动开启功能",
            enabled = !settings.disableShadeGlassHooks,
            onClick = { selectingBase = true },
        )
        Text(
            "保留各材质的底色、不透明度、明暗、方向光、背景和按压遮罩。统一参数优先于单独材质设置；模糊半径在上级页面单独设置。关闭后恢复各材质原有参数。仅支持原生 42 项 Glass，不转换其他渲染材质。",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
    val values = scope.values(settings, false)
    SettingsSection(topLabel = "光学参数") {
        indices.forEach { index ->
            HeadsUpGlassSliderItem(
                index = index,
                value = values[index],
                enabled = scope.enabled(settings),
                onValueChange = { value ->
                    val next = scope.values(settings, false)
                    next[index] = value
                    update(scope.setParameters(settings, false, next))
                },
                onEdit = { target = HeadsUpGlassParameterTarget(false, index) },
                scope = scope,
            )
        }
    }
    if (selectingBase) {
        DeadlinerMiuixDialog(
            show = true,
            title = "选择基准材质",
            summary = "仅复制光学参数，替换当前 7 项调整。已保存材质使用其保存值，不要求来源开关开启。",
            onDismissRequest = { selectingBase = false },
        ) {
            fun load(values: FloatArray) {
                update(scope.setParameters(settings, false, values))
                selectingBase = false
            }
            NavigationSettingItem("通知卡片原生基准", "恢复通知卡片基准中的光学参数", onClick = { load(ShadeCardGlassPolicy.defaults()) })
            NavigationSettingItem("通知 / 控制中心 · 已保存", "使用当前保存的卡片参数", onClick = { load(GlassParameterScope.ShadeCards.values(settings, false)) })
            NavigationSettingItem("悬浮通知 · 已保存亮色", "使用当前保存的亮色参数", onClick = { load(GlassParameterScope.HeadsUp.values(settings, false)) })
            NavigationSettingItem("悬浮通知 · 已保存暗色", "使用当前保存的暗色参数", onClick = { load(GlassParameterScope.HeadsUp.values(settings, true)) })
        }
    }
    HeadsUpGlassParameterDialog(
        target = target,
        settings = settings,
        onDismissRequest = { target = null },
        onSave = { update(it); target = null },
        scope = scope,
    )
}
