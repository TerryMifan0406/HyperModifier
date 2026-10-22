package com.aritxonly.myhypermodifier

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class MediaIslandSettingsTest {
    @Test fun disabledFeaturesRetainSavedValuesAndRuntimeGateWinsOverThem() {
        val context = RuntimeEnvironment.getApplication()
        val saved = ModifierSettings().copy(disableMediaIslandHooks = true, mediaLayoutPreset = "compact",
            mediaEnabled = true, islandEnabled = true, superIslandWhitelistDisabled = true,
            dynamicIslandOpacityEnabled = true, dynamicIslandNotificationOpacityEnabled = true,
            statusBarWifiMarginLeft = .35f, statusBarWifiMarginRight = -.45f)
        ModifierSettingsStore.save(context, saved)
        val loaded = ModifierSettingsStore.load(context)
        assertTrue(loaded.disableMediaIslandHooks)
        assertTrue(loaded.mediaEnabled)
        assertEquals("compact", loaded.mediaLayoutPreset)
        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(ModifierSettingsStore.backupValues(context)),
            ModifierSettingsStore.backupSchema())
        assertEquals(true, decoded["disable_media_island_hooks"])
        assertEquals(.35f, decoded["status_bar_wifi_margin_left"])
        assertEquals(-.45f, decoded["status_bar_wifi_margin_right"])
        val bundle = ModifierSettingsStore.toBundle(loaded)
        assertTrue(bundle.getBoolean("disable_media_island_hooks"))
        assertEquals(.35f, bundle.getFloat("status_bar_wifi_margin_left"), 0f)

        val field = ModuleSettings::class.java.getDeclaredField("remotePreferences").apply { isAccessible = true }
        val load = ModuleSettings::class.java.getDeclaredMethod("load").apply { isAccessible = true }
        val previous = field.get(null)
        try {
            field.set(null, context.getSharedPreferences("modifier_settings", Context.MODE_PRIVATE))
            assertEquals(true, load.invoke(null))
            assertTrue(ModuleSettings.disableMediaIslandHooks)
            assertFalse(ModuleSettings.mediaEnabled)
            assertFalse(ModuleSettings.islandEnabled)
            assertFalse(ModuleSettings.islandProgressBar)
            assertFalse(ModuleSettings.superIslandWhitelistDisabled)
            assertFalse(ModuleSettings.dynamicIslandOpacityEnabled)
            assertFalse(ModuleSettings.dynamicIslandNotificationOpacityEnabled)
            assertEquals(0, ModuleSettings.activeMediaPresetHeight())
            assertEquals(.35f, ModuleSettings.statusBarWifiMarginLeft, 0f)
            ModifierSettingsStore.save(context, loaded.copy(disableMediaIslandHooks = false))
            assertEquals(true, load.invoke(null))
            assertTrue(ModuleSettings.mediaEnabled)
            assertTrue(ModuleSettings.superIslandWhitelistDisabled)
            assertEquals(84, ModuleSettings.activeMediaPresetHeight())
        } finally {
            ModifierSettingsStore.save(context, ModifierSettings())
            load.invoke(null)
            field.set(null, previous)
        }
    }
}
