package com.aritxonly.myhypermodifier

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Deadliner 5499df0: ImmersiveMiuixCompactNavigationScaffold.FloatingNavigationEdgeMask.
 * Same surface alpha ramp over 54dp + 4dp + 32dp + navigation inset + lift.
 * Fold the solid surface + DstIn mask into one cached gradient in the existing dock host:
 * identical compositing, with no additional full-width offscreen layer or backdrop capture.
 * Apply BEFORE inset/horizontal padding, outside the content-only backdrop recorder.
 */
@Composable
internal fun Modifier.navigationEdgeMask(
    enabled: Boolean = true,
    coverageHeight: Dp? = null,
): Modifier {
    if (!enabled) return this
    val surface = MiuixTheme.colorScheme.surface
    return drawWithCache {
        // The main app keeps its existing dock measurement and touch region. Its mask extends
        // upward from the bottom edge, just like Deadliner's independently overlaid edge mask.
        val height = coverageHeight?.toPx()?.coerceAtLeast(0f) ?: size.height
        val top = size.height - height
        val brush = Brush.verticalGradient(
            listOf(surface.copy(alpha = 0f), surface), startY = top, endY = size.height,
        )
        onDrawBehind {
            if (height > 0f) drawRect(brush, topLeft = Offset(0f, top), size = Size(size.width, height))
        }
    }
}
