package com.aritxonly.myhypermodifier;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, qualifiers = "xxhdpi", manifest = Config.NONE, application = android.app.Application.class)
public class StatusBarWifiMarginsTest {
    static class Root extends FrameLayout {
        int layouts;
        Root() { super(RuntimeEnvironment.getApplication()); }
        @Override public void requestLayout() { super.requestLayout(); layouts++; }
    }
    private void apply(Root root) throws Exception {
        var method = StatusBarNetworkMarginsHooks.class.getDeclaredMethod("apply", ViewGroup.class, int.class);
        method.setAccessible(true);
        method.invoke(null, root, 2);
    }
    @After public void reset() {
        ModuleSettings.statusBarWifiMarginLeft = ModuleSettings.statusBarWifiMarginRight = 0;
        ModuleSettings.moduleHooksEnabled = true;
        ModuleSettings.systemUiCompatibilityMode = false;
        StatusBarNetworkMarginsHooks.refresh();
        ShadowLooper.idleMainLooper();
    }

    @Test public void spacingKeepsVerticalGeometryAndRestoresStockOnDisableAndCompatibilityMode() throws Exception {
        Root root = new Root();
        root.setPadding(4, 5, 6, 7);
        ModuleSettings.moduleHooksEnabled = true;
        ModuleSettings.systemUiCompatibilityMode = false;
        ModuleSettings.statusBarWifiMarginLeft = 1;
        ModuleSettings.statusBarWifiMarginRight = -2;
        apply(root);
        assertEquals(7, root.getPaddingLeft());
        assertEquals(0, root.getPaddingRight());
        assertEquals(5, root.getPaddingTop());
        assertEquals(7, root.getPaddingBottom());
        apply(root);
        assertEquals(7, root.getPaddingLeft());
        ModuleSettings.systemUiCompatibilityMode = true;
        apply(root);
        assertEquals(4, root.getPaddingLeft());
        assertEquals(6, root.getPaddingRight());
        ModuleSettings.systemUiCompatibilityMode = false;
        apply(root);
        ModuleSettings.moduleHooksEnabled = false;
        apply(root);
        assertEquals(4, root.getPaddingLeft());
        assertEquals(6, root.getPaddingRight());
    }

    @Test public void zeroDefaultStillRegistersAnExistingWifiViewForLiveEnabling() throws Exception {
        Root root = new Root();
        root.setPadding(2, 3, 4, 5);
        ModuleSettings.moduleHooksEnabled = true;
        ModuleSettings.systemUiCompatibilityMode = false;
        ModuleSettings.statusBarWifiMarginLeft = ModuleSettings.statusBarWifiMarginRight = 0;
        apply(root);
        ModuleSettings.statusBarWifiMarginRight = -2;
        StatusBarNetworkMarginsHooks.refresh();
        ShadowLooper.idleMainLooper();
        assertEquals(-2, root.getPaddingRight());
        assertFalse(root.getClipChildren());
        ModuleSettings.statusBarWifiMarginRight = 0;
        StatusBarNetworkMarginsHooks.refresh();
        ShadowLooper.idleMainLooper();
        assertEquals(4, root.getPaddingRight());
        assertTrue(root.getClipChildren());
    }

    @Test public void refreshesCoalesceAndUnrelatedSettingsDoNotRelayoutWifi() throws Exception {
        Root root = new Root();
        ModuleSettings.moduleHooksEnabled = true;
        ModuleSettings.statusBarWifiMarginLeft = 1;
        apply(root);
        StatusBarNetworkMarginsHooks.refresh();
        ShadowLooper.idleMainLooper();
        root.layouts = 0;
        StatusBarNetworkMarginsHooks.refresh();
        StatusBarNetworkMarginsHooks.refresh();
        ShadowLooper.idleMainLooper();
        assertEquals(0, root.layouts);
        ModuleSettings.statusBarWifiMarginRight = 1;
        StatusBarNetworkMarginsHooks.refresh();
        StatusBarNetworkMarginsHooks.refresh();
        ShadowLooper.idleMainLooper();
        assertTrue(root.layouts > 0);
        assertEquals(3, root.getPaddingRight());
    }
}
