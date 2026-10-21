package com.aritxonly.myhypermodifier;

import java.lang.reflect.Field;

/** Versioned DeviceAnimTabView layouts. Holds metadata only, never a native View. */
final class MiHomeLottieFields {
    // selected pin/plain, unselected, home selected pin/plain, home unselected, loaded, pinned.
    private static final String[][] LAYOUTS = {
            {"o0OOo0o", "o0OOo0oO", "o0OOo0oo", "o0OOoO00", "o0OOoO0",
                    "o0OOoO0O", "o0OOoO0o", "o0OOoO"}, // 11.9.621
            {"OooOoOO", "OooOoo0", "OooOoo", "OooOooO", "OooOooo",
                    "Oooo000", "Oooo00O", "Oooo00o"}, // 11.8.605
    };
    private static final MiHomeLottieFields UNSUPPORTED = new MiHomeLottieFields(null);
    private final Field[] fields;

    private MiHomeLottieFields(Field[] fields) {
        this.fields = fields;
    }

    static MiHomeLottieFields resolve(Class<?> type) {
        for (String[] layout : LAYOUTS) {
            Field[] fields = new Field[layout.length];
            boolean complete = true;
            for (int i = 0; i < layout.length; i++) {
                fields[i] = find(type, layout[i], i == 7 ? boolean.class : int.class);
                if (fields[i] == null) {
                    complete = false;
                    break;
                }
            }
            if (complete) return new MiHomeLottieFields(fields);
        }
        return UNSUPPORTED;
    }

    int selected(Object tab, boolean home) {
        boolean pinned = false;
        if (fields != null) {
            try {
                pinned = fields[7].getBoolean(tab);
            } catch (IllegalAccessException | IllegalArgumentException ignored) { }
        }
        return read(tab, (home ? 3 : 0) + (pinned ? 0 : 1));
    }

    int unselected(Object tab, boolean home) {
        return read(tab, home ? 5 : 2);
    }

    int loaded(Object tab) {
        return read(tab, 6);
    }

    private int read(Object tab, int slot) {
        if (fields == null) return -1;
        try {
            return fields[slot].getInt(tab);
        } catch (IllegalAccessException | IllegalArgumentException ignored) {
            return -1;
        }
    }

    private static Field find(Class<?> type, String name, Class<?> valueType) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                if (field.getType() != valueType) return null;
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Native tab subclasses may inherit their versioned layout.
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
