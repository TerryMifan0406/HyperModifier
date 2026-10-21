package com.aritxonly.myhypermodifier;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClockMvpFontAccessTest {
    private static final class Glyph { private float height = 100f; private int incompatible = 1; }
    @Test public void cachedSetterUpdatesOnlyRequestedGlyphWithPrimitiveSignature() throws Throwable {
        MethodHandle setter = ClockMvpFontMorph.Access.setter(Glyph.class, "height");
        assertEquals(MethodType.methodType(void.class, Object.class, float.class), setter.type());
        Glyph target = new Glyph(), original = new Glyph();
        setter.invokeExact((Object) target, 23.5f);
        assertEquals(23.5f, target.height, 0f);
        assertEquals(100f, original.height, 0f);
        setter.invokeExact((Object) target, 100f);
        assertEquals(100f, target.height, 0f);
    }
    @Test public void incompatibleFieldCannotSilentlyAdaptToFloatSetter() throws Exception {
        try {
            ClockMvpFontMorph.Access.setter(Glyph.class, "incompatible");
            fail("Wrong native ABI must be rejected");
        } catch (NoSuchFieldException expected) {
            assertTrue(expected.getMessage().contains("expected-float"));
        }
    }
}
