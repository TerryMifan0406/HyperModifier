package com.aritxonly.myhypermodifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aritxonly.deadliner.model.AdvancedMaterialTuning
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun NavigationMaterialRadiusSlider(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = SettingsItemDefaults.HorizontalPadding, vertical = 12.dp),
    ) {
        Text(stringResource(R.string.settings_advanced_material_blur_size),
            style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            enabled = enabled,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = AdvancedMaterialTuning.MinBlurRadius..AdvancedMaterialTuning.MaxBlurRadius,
            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
            showKeyPoints = true,
            keyPoints = listOf(AdvancedMaterialTuning.DefaultBlurRadius),
            modifier = Modifier.fillMaxWidth(),
        )
        if (enabled) BlurDefaultKeyPointMarker()
        if (!enabled) Text(stringResource(R.string.settings_material_miuix_preset_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp))
    }
}

/** The native track's point is covered by the thumb at default; keep its position visible below. */
@Composable
private fun BlurDefaultKeyPointMarker() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(7.dp)) {
        val fraction = (AdvancedMaterialTuning.DefaultBlurRadius - AdvancedMaterialTuning.MinBlurRadius) /
            (AdvancedMaterialTuning.MaxBlurRadius - AdvancedMaterialTuning.MinBlurRadius)
        val inset = SliderDefaults.MinHeight.toPx() / 2f
        val x = inset + (if (rtl) 1f - fraction else fraction) * (size.width - inset * 2f).coerceAtLeast(0f)
        drawLine(color, Offset(x, 2.dp.toPx()), Offset(x, 5.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
    }
}
