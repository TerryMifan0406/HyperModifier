package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.aritxonly.deadliner.model.AdvancedMaterialTuning
import top.yukonga.miuix.kmp.basic.Slider

internal object SettingsItemDefaults { val HorizontalPadding = 24.dp }
@Composable internal fun SettingsHeroSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    SettingsSection(modifier = modifier, content = content)

@Composable
internal fun NavigationGlassSettingsPage(
    padding: PaddingValues, settings: ModifierSettings,
    update: (ModifierSettings) -> Unit, onScroll: (Float) -> Unit,
) {
    val saved = remember(settings.navigationGlassParameters) { NavigationGlassParameters.decode(settings.navigationGlassParameters) }
    var preview by remember(saved) { mutableStateOf(saved) }
    var lift by remember(settings.hyperGlassifyHiddenNavigationLift) { mutableFloatStateOf(settings.hyperGlassifyHiddenNavigationLift) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    fun commit(value: NavigationGlassParameters = preview, liftValue: Float = lift) {
        preview = value.normalized()
        update(settings.copy(navigationGlassParameters = preview.encode(),
            hyperGlassifyHiddenNavigationLift = liftValue.coerceIn(0f, 48f)))
    }
    SettingsScrollPage(padding, onScroll) {
        SettingsSection {
            SettingItem("自研柔光玻璃管线", "参考 Apache-2.0 开源项目 AndroidLiquidGlass 的 Shader 设计")
        }
        SettingsSection(topLabel = "模糊度") {
            NavigationMaterialRadiusSlider(value = preview.blurRadius, enabled = true,
                onValueChange = { preview = preview.copy(blurRadius = it) },
                onValueChangeFinished = { commit() })
        }
        AdvancedMaterialPreviewCard(preview.blurRadius, preview.fineTuning, true, bottomLiftDp = lift, useOfficialMiuixGlass = false)
        SettingsSection(topLabel = "底栏位置") {
            SettingItem("底栏额外抬高", "${lift.toInt()} dp · 在系统导航栏间距之外增加")
            Slider(value = lift, valueRange = 0f..48f, steps = 47,
                onValueChange = { lift = it }, onValueChangeFinished = { commit() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
        }
        SettingsSection(topLabel = "高级参数调节") {
            AdvancedMaterialFineTuningPanel(
                value = preview.fineTuning,
                onValueChange = { preview = preview.copy(fineTuning = it.normalized()) },
                onValueChangeFinished = { commit() },
                onImmediateChange = { commit(preview.copy(fineTuning = it)) },
                onExport = {
                    val json = org.json.JSONObject().put("format", "myhypermodifier-navigation-glass")
                        .put("version", 1).put("parameters", preview.encode()).put("liftDp", lift).toString(2)
                    clipboard.setText(AnnotatedString(json))
                    Toast.makeText(context, "参数已复制", Toast.LENGTH_SHORT).show()
                },
                onReset = {
                    lift = ModuleDefaultValues.HYPER_GLASSIFY_HIDDEN_NAVIGATION_LIFT
                    commit(NavigationGlassParameters.Default, lift)
                },
            )
        }
        SettingsSection(topLabel = "开源参考") {
            SettingItem("AndroidLiquidGlass · Apache-2.0", "Shader 设计参考 · 查看开源项目", onClick = {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/Kyant0/AndroidLiquidGlass")))
            })
        }
    }
}
