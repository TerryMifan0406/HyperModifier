package com.aritxonly.myhypermodifier;

import android.view.View;
import android.widget.FrameLayout;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE, application = android.app.Application.class)
public class MediaIslandGateTest {
    @After public void reset() { ModuleSettings.disableMediaIslandHooks = false; }

    @Test public void sharedMaterialsRespectDynamicSurfaceOwnershipWithoutAStaleParentCache() {
        var context = RuntimeEnvironment.getApplication();
        FrameLayout media = new com.mi.widget.view.MusicBgView(context);
        FrameLayout ordinary = new FrameLayout(context);
        View child = new View(context);
        media.addView(child);
        assertFalse(MediaIslandGate.protectedView(child));
        ModuleSettings.disableMediaIslandHooks = true;
        assertTrue(MediaIslandGate.protectedView(child));
        assertFalse(MediaIslandGate.protectedView(ordinary));
        media.removeView(child);
        ordinary.addView(child);
        assertFalse(MediaIslandGate.protectedView(child));
    }

    @Test public void optOutBypassesPresetXmlAndDedicatedInstallersBeforeResolvingOemClasses() throws Exception {
        ModuleSettings.disableMediaIslandHooks = true;
        String original = ModuleSettings.mediaLayoutPreset;
        try {
            ModuleSettings.mediaLayoutPreset = "compact";
            assertEquals(0, ModuleSettings.activeMediaPresetHeight());
            assertEquals(0, MediaConstraintCustomizer.requestedMediaIslandHeight(null));
            MediaConstraintCustomizer.patchMediaConstraintSet(null, new Object(), false);
            MediaConstraintCustomizer.patchMediaConstraintSet(null, new Object(), true);
            MediaIslandHostHooks.install(null, null);
            MediaIslandHostHooks.installLoaded(null, null);
            MediaIslandHostHooks.installExpandedLoaded(null, null);
            DynamicIslandGlassHooks.install(null, null);
            SuperIslandWhitelistHooks.installSystemUi(null, null);
            SuperIslandWhitelistHooks.installXmsf(null, null);
            SpotifyMediaCardHooks.install(null, null);
            SpotifyMediaCardBinding.install(null, null);
        } finally { ModuleSettings.mediaLayoutPreset = original; }
    }
}
