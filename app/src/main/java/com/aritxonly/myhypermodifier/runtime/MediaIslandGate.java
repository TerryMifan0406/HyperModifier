package com.aritxonly.myhypermodifier;

import android.view.View;
import android.view.ViewParent;

/** The restart gate wins over shared material controls on media/island-owned surfaces. */
final class MediaIslandGate {
    private MediaIslandGate() {}

    static boolean protectedClass(String name) {
        return name.startsWith("miui.systemui.dynamicisland.")
                || name.startsWith("com.android.systemui.statusbar.notification.mediacontrol.")
                || name.startsWith("com.android.systemui.statusbar.notification.mediaisland.")
                || name.startsWith("miui.systemui.controlcenter.panel.main.media.")
                || name.equals("com.mi.widget.view.MusicBgView");
    }

    static boolean protectedView(View view) {
        if (!ModuleSettings.disableMediaIslandHooks) return false;
        if (FocusNotificationGlassTintHooks.tracked(view)) return true;
        // No reflection, resource-name lookup, IPC or allocation; parent ownership is dynamic.
        for (View current = view; current != null;) {
            if (protectedClass(current.getClass().getName())) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
}
