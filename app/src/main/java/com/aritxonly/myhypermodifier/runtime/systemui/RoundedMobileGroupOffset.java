package com.aritxonly.myhypermodifier;

import android.view.View;
import java.util.WeakHashMap;

/** Main-thread resource/settings callbacks only. Values never retain a View or Context. */
final class RoundedMobileGroupOffset {
    private static final WeakHashMap<View, State> GROUPS = new WeakHashMap<>();

    private RoundedMobileGroupOffset() {}

    static void setSupported(View group, boolean supported, boolean active, float offsetDp) {
        if (group == null) return;
        State state = GROUPS.get(group);
        if (state == null) {
            if (!supported) return;
            state = new State();
            GROUPS.put(group, state);
        }
        state.supported = supported;
        apply(group, state, active && supported, offsetDp);
    }

    static void refresh(boolean active, float offsetDp) {
        for (var entry : GROUPS.entrySet()) {
            View group = entry.getKey();
            if (group != null) apply(group, entry.getValue(), active && entry.getValue().supported, offsetDp);
        }
    }

    private static void apply(View group, State state, boolean active, float offsetDp) {
        float current = group.getTranslationY();
        if (!active) {
            if (state.owned && Float.compare(current, state.applied) == 0) group.setTranslationY(state.base);
            state.owned = false;
            state.overridden = false;
            return;
        }
        if (state.overridden) return;
        if (state.owned && Float.compare(current, state.applied) != 0) {
            // An animation/module replaced our property. Preserve it until the next enable cycle.
            state.owned = false;
            state.overridden = true;
            return;
        }
        if (!state.owned) {
            state.base = current;
            state.owned = true;
        }
        float target = state.base + offsetDp * group.getResources().getDisplayMetrics().density;
        if (Float.compare(current, target) != 0) group.setTranslationY(target);
        state.applied = target;
    }

    private static final class State {
        float base, applied;
        boolean supported, owned, overridden;
    }
}
