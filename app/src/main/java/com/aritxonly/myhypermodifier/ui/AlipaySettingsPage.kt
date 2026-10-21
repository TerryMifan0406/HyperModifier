package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

@Composable
internal fun AlipaySettingsPage(
    padding: PaddingValues,
    settings: ModifierSettings,
    update: (ModifierSettings) -> Unit,
    onScroll: (Float) -> Unit,
) = SettingsScrollPage(padding, onScroll) {
    SettingsSection(topLabel = "支付宝 · 经典模式") {
        SettingsSwitchItem(
            "柔光玻璃悬浮底栏", "保留支付宝默认图标与原色，使用静态普通态和选中态",
            settings.alipayFloatingNavigationEnabled,
            { update(settings.copy(alipayFloatingNavigationEnabled = it)) },
        )
    }
    SettingsSection(topLabel = "兼容性") {
        SettingItem("当前适配版本", "12.12.36.8000（经典模式，待真机验证）")
        SettingItem("首次启用", "在 LSPosed 作用域中选择支付宝并重启支付宝；后续开关实时生效")
    }
}
