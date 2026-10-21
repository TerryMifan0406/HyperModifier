package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class NavigationBarInsetPolicyTest {
    @Test public void consumedTreeInsetsUseNativeHomeInset() {
        NavigationBarInsetPolicy policy = new NavigationBarInsetPolicy();
        assertEquals(72, policy.resolve(1, 0, 0, 72));
        assertEquals(72, policy.resolve(1, 0, 0, 0));
    }

    @Test public void rotationDropsPortraitInsetUntilNewNativeDispatch() {
        NavigationBarInsetPolicy policy = new NavigationBarInsetPolicy();
        assertEquals(72, policy.resolve(1, 0, 0, 72));
        assertEquals(0, policy.resolve(2, 0, 0, 0));
        assertEquals(24, policy.resolve(2, 0, 0, 24));
    }

    @Test public void freshWindowValuesReplaceTransientCachedValue() {
        NavigationBarInsetPolicy policy = new NavigationBarInsetPolicy();
        assertEquals(72, policy.resolve(1, 72, 0, 0));
        assertEquals(72, policy.resolve(1, 0, 0, 0));
        assertEquals(48, policy.resolve(1, 0, 48, 0));
        assertEquals(0, policy.resolve(2, -1, -1, -1));
    }
}
