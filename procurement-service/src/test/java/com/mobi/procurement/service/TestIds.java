package com.mobi.procurement.service;

import java.lang.reflect.Field;

/** Sets fields JPA would normally fill (ids, status loaded from the database). */
final class TestIds {

    private TestIds() {
    }

    static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
