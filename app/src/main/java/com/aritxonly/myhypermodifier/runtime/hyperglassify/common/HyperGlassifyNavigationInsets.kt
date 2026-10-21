package com.aritxonly.myhypermodifier

import android.view.View
import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Always adds the configured visual clearance after the system navigation-bar inset. */
@Composable
internal fun hyperGlassifyHiddenNavigationLift(): Dp {
    return NavigationGlassRuntime.lift.dp
}

/** Keeps a panel at the same height when returning from a page briefly clears its insets. */
internal class StableNavigationBarInset {
    private val policy = NavigationBarInsetPolicy()

    fun bottomPx(panel: View, decor: View, nativeBottomPx: Int = 0): Int {
        val orientation = decor.resources.configuration.orientation
        val navigationBars = WindowInsets.Type.navigationBars()
        val panelBottom = panel.rootWindowInsets
            ?.getInsetsIgnoringVisibility(navigationBars)?.bottom ?: 0
        val decorBottom = decor.rootWindowInsets
            ?.getInsetsIgnoringVisibility(navigationBars)?.bottom ?: 0
        return policy.resolve(orientation, panelBottom, decorBottom, nativeBottomPx)
    }
}
