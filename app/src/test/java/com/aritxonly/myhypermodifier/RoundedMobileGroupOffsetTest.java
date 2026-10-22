package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.res.Configuration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33, manifest=Config.NONE, application=android.app.Application.class)
public class RoundedMobileGroupOffsetTest {
    private FrameLayout group(int dpi) {
        Context base = RuntimeEnvironment.getApplication();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.densityDpi = dpi;
        return new FrameLayout(base.createConfigurationContext(config));
    }

    @Test public void fractionalOffsetMovesWholeGroupWithoutAccumulatingAndRestoresNativeBase() {
        for (int dpi : new int[]{160, 320, 480}) {
            FrameLayout group = group(dpi);
            TextView label = new TextView(group.getContext());
            label.setText("5G");
            ImageView bars = new ImageView(group.getContext());
            group.addView(label); group.addView(bars);
            group.setTranslationY(2f);
            float delta = .1f * group.getResources().getDisplayMetrics().density;
            RoundedMobileGroupOffset.setSupported(group, true, true, .1f);
            assertEquals(2f + delta, group.getTranslationY(), .00001f);
            assertEquals(0f, label.getTranslationY(), 0f);
            assertEquals(0f, bars.getTranslationY(), 0f);
            for (int i = 0; i < 10; i++) RoundedMobileGroupOffset.setSupported(group, true, true, .1f);
            assertEquals(2f + delta, group.getTranslationY(), .00001f);
            RoundedMobileGroupOffset.refresh(false, .1f);
            assertEquals(2f, group.getTranslationY(), 0f);
            RoundedMobileGroupOffset.setSupported(group, true, true, .1f);
            RoundedMobileGroupOffset.setSupported(group, false, true, .1f);
            assertEquals("Unsupported resources restore original position", 2f, group.getTranslationY(), 0f);
        }
    }

    @Test public void densityRefreshRecomputesDeltaFromBaseRatherThanPreviousOffset() {
        FrameLayout group = group(320);
        group.setTranslationY(-1f);
        RoundedMobileGroupOffset.setSupported(group, true, true, .1f);
        assertEquals(-.8f, group.getTranslationY(), .00001f);
        group.getResources().getDisplayMetrics().density = 3f;
        RoundedMobileGroupOffset.setSupported(group, true, true, .1f);
        assertEquals(-.7f, group.getTranslationY(), .00001f);
        RoundedMobileGroupOffset.refresh(true, -.2f);
        assertEquals(-1.6f, group.getTranslationY(), .00001f);
        RoundedMobileGroupOffset.refresh(false, .1f);
        assertEquals(-1f, group.getTranslationY(), 0f);
    }

    @Test public void externalTranslationOverrideIsPreservedUntilNextEnableCycle() {
        FrameLayout group = group(320);
        RoundedMobileGroupOffset.setSupported(group, true, true, .1f);
        group.setTranslationY(9f);
        RoundedMobileGroupOffset.refresh(true, .1f);
        RoundedMobileGroupOffset.refresh(true, .1f);
        assertEquals(9f, group.getTranslationY(), 0f);
        RoundedMobileGroupOffset.refresh(false, .1f);
        assertEquals(9f, group.getTranslationY(), 0f);
        RoundedMobileGroupOffset.refresh(true, .1f);
        assertEquals(9.2f, group.getTranslationY(), .00001f);
        RoundedMobileGroupOffset.setSupported(group, false, false, .1f);
        assertEquals(9f, group.getTranslationY(), 0f);
    }
}
