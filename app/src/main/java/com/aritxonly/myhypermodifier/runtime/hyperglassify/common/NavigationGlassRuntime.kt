package com.aritxonly.myhypermodifier

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aritxonly.deadliner.ui.theme.AdvancedMaterialSpec
import com.aritxonly.deadliner.ui.theme.createAdvancedMaterialSpec
import java.lang.ref.WeakReference
import kotlin.math.ceil

internal interface NavigationGlassObserver { fun onNavigationGlassChanged() }

/** Loaded only by navigation hosts. No Compose initialization in unrelated target processes. */
internal object NavigationGlassRuntime {
    private data class Configuration(val material: AdvancedMaterialSpec, val lift: Float)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var lastRaw = ModuleSettings.navigationGlassParameters
    private fun spec(raw: String): AdvancedMaterialSpec {
        val value = NavigationGlassParameters.decode(raw)
        return createAdvancedMaterialSpec(true, value.blurRadius, value.fineTuning)
    }
    private var configuration by mutableStateOf(Configuration(spec(lastRaw), ModuleSettings.hyperGlassifyHiddenNavigationLift))
    @Volatile private var lastLift = configuration.lift
    val material get() = configuration.material
    val lift get() = configuration.lift
    private val observers = ArrayList<WeakReference<NavigationGlassObserver>>()
    private val refresh = Runnable {
        val raw = ModuleSettings.navigationGlassParameters
        val previous = configuration
        val material = if (raw == lastRaw) previous.material else spec(raw)
        lastRaw = raw
        val lift = ModuleSettings.hyperGlassifyHiddenNavigationLift.let { if (it.isFinite()) it.coerceIn(0f, 48f) else 0f }
        lastLift = lift
        val next = Configuration(material, lift)
        if (next != previous) {
            configuration = next
            if (material != previous.material) observers.toList().forEach { it.get()?.onNavigationGlassChanged() }
            observers.removeAll { it.get() == null }
        }
    }
    init {
        ModuleSettings.onChanged {
            // One queued update; reading/parsing only the navigation keys, never replaying all UI.
            if (lastRaw != ModuleSettings.navigationGlassParameters || lastLift != ModuleSettings.hyperGlassifyHiddenNavigationLift) {
                main.removeCallbacks(refresh)
                main.post(refresh)
            }
        }
    }
    fun observe(observer: NavigationGlassObserver) { observers.add(WeakReference(observer)) }
    fun remove(observer: NavigationGlassObserver) { observers.removeAll { it.get() == null || it.get() === observer } }
    fun paddingPx(density: Float, minimumDp: Float = 32f): Int {
        val material = material
        val tuning = material.fineTuning
        val radius = material.resolvedBlurRadius * tuning.glassBlurRadiusMultiplier
        val lens = if (tuning.refractionEnabled) tuning.refractionAmountDp + tuning.chromaticAberrationDp else 0f
        val post = if (tuning.postRefractionBlurEnabled) tuning.postRefractionBlurRadiusDp * 3f else 0f
        return ceil(maxOf(minimumDp * density, radius * 3f + (lens + post) * density)).toInt()
    }
}

@Composable internal fun navigationGlassMaterial(): AdvancedMaterialSpec = NavigationGlassRuntime.material
