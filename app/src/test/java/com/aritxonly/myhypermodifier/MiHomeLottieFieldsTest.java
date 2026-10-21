package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MiHomeLottieFieldsTest {
    private static class NewTab {
        int o0OOo0o = 11, o0OOo0oO = 12, o0OOo0oo = 13;
        int o0OOoO00 = 21, o0OOoO0 = 22, o0OOoO0O = 23, o0OOoO0o = 31;
        boolean o0OOoO;
    }

    private static class OldTab {
        int OooOoOO = 11, OooOoo0 = 12, OooOoo = 13;
        int OooOooO = 21, OooOooo = 22, Oooo000 = 23, Oooo00O = 31;
        boolean Oooo00o = true;
    }

    @Test public void newVersionKeepsHomeAndOrdinaryAnimationPairsDistinct() {
        NewTab tab = new NewTab();
        MiHomeLottieFields fields = MiHomeLottieFields.resolve(tab.getClass());
        assertEquals(12, fields.selected(tab, false));
        assertEquals(13, fields.unselected(tab, false));
        assertEquals(22, fields.selected(tab, true));
        assertEquals(23, fields.unselected(tab, true));
        assertEquals(31, fields.loaded(tab));
    }

    @Test public void cachedMetadataReadsLivePinAndResourceChanges() {
        NewTab tab = new NewTab();
        MiHomeLottieFields fields = MiHomeLottieFields.resolve(tab.getClass());
        tab.o0OOoO = true;
        assertEquals(11, fields.selected(tab, false));
        assertEquals(21, fields.selected(tab, true));
        tab.o0OOoO00 = 42;
        assertEquals(42, fields.selected(tab, true));
        tab.o0OOoO = false;
        assertEquals(22, fields.selected(tab, true));
    }

    @Test public void oldVersionRemainsCompatible() {
        OldTab tab = new OldTab();
        MiHomeLottieFields fields = MiHomeLottieFields.resolve(tab.getClass());
        assertEquals(11, fields.selected(tab, false));
        assertEquals(21, fields.selected(tab, true));
        tab.Oooo00o = false;
        assertEquals(12, fields.selected(tab, false));
        assertEquals(22, fields.selected(tab, true));
        assertEquals(13, fields.unselected(tab, false));
        assertEquals(23, fields.unselected(tab, true));
    }

    @Test public void inheritedLayoutIsSupported() {
        NewTab tab = new NewTab() { };
        assertEquals(22, MiHomeLottieFields.resolve(tab.getClass()).selected(tab, true));
    }

    @Test public void incompleteOrUnrelatedLayoutDoesNotGuessResourceRoles() {
        class PartialTab { int o0OOo0o = 11; }
        PartialTab tab = new PartialTab();
        MiHomeLottieFields fields = MiHomeLottieFields.resolve(tab.getClass());
        assertEquals(-1, fields.selected(tab, true));
        assertEquals(-1, fields.unselected(tab, false));
        assertEquals(-1, fields.loaded(tab));
    }
}
