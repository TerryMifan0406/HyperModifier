package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.aritxonly.myhypermodifier.ControlCenterBatteryPanelState.*;

public class ControlCenterBatteryPanelStateTest {
    @Test public void staysVisibleAfterTheOpeningAnimationCompletes() {
        ControlCenterBatteryPanelState state = new ControlCenterBatteryPanelState();
        assertFalse(state.showing());
        assertTrue(state.update(EXPANDING));
        assertTrue(state.showing());
        assertFalse(state.update(EXPANDED));
        for (int i = 0; i < 10; i++) {
            assertFalse(state.update(EXPANDED));
            assertTrue(state.showing());
        }
    }

    @Test public void closingDoesNotReenableThePercentageForItsAnimation() {
        ControlCenterBatteryPanelState state = new ControlCenterBatteryPanelState();
        state.update(EXPANDING);
        state.update(EXPANDED);
        assertTrue(state.update(EXPANDING));
        assertFalse(state.showing());
        assertFalse(state.update(EXPANDING));
        assertFalse(state.update(COLLAPSED));
        assertFalse(state.showing());
    }

    @Test public void canceledOpeningAndClosingFollowTheActualFinalState() {
        ControlCenterBatteryPanelState state = new ControlCenterBatteryPanelState();
        state.update(EXPANDING);
        assertTrue(state.update(COLLAPSED));
        assertFalse(state.showing());
        state.update(EXPANDED);
        state.update(EXPANDING);
        assertTrue(state.update(EXPANDED));
        assertTrue(state.showing());
    }

    @Test public void pluginOrViewRecreationCanSeedTheCurrentState() {
        ControlCenterBatteryPanelState state = new ControlCenterBatteryPanelState();
        assertTrue(state.update(EXPANDED));
        assertTrue(state.showing());
        assertTrue(state.update(COLLAPSED));
        assertFalse(state.showing());
        assertTrue(state.update(EXPANDING));
    }

    @Test public void nativeTransientBooleanCannotDriveTheOverrideBackwards() {
        ControlCenterBatteryPanelState panel = new ControlCenterBatteryPanelState();
        ControlCenterBatteryPercentPolicy policy = new ControlCenterBatteryPercentPolicy();
        panel.update(EXPANDING);
        int style = policy.resolve(0, true, panel.showing());
        assertEquals(3, style);
        panel.update(EXPANDED); // Native mIsControlCenterExpanding becomes false here.
        style = policy.resolve(style, true, panel.showing());
        assertEquals(3, style);
        panel.update(EXPANDING); // Native boolean becomes true again while closing.
        style = policy.resolve(style, true, panel.showing());
        assertEquals(0, style);
        panel.update(COLLAPSED);
        assertEquals(0, policy.resolve(style, true, panel.showing()));
    }
}
