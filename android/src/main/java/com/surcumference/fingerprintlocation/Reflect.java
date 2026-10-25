package com.surcumference.fingerprintlocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class Reflect {
    private Reflect() {}
    static Object field(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> cls = object.getClass(); cls != null; cls = cls.getSuperclass()) {
            try { Field f = cls.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
            catch (NoSuchFieldException absent) { }
        }
        throw new NoSuchFieldException(name);
    }
    static Object call(Object object, String name) throws ReflectiveOperationException {
        return call(object, name, new Class<?>[0]);
    }
    static Object call(Object object, String name, Class<?>[] types, Object... arguments) throws ReflectiveOperationException {
        return method(object.getClass(), name, types).invoke(object, arguments);
    }
    static Object callStatic(String cls, String name, Class<?>[] types, Object... arguments) throws ReflectiveOperationException {
        return method(Class.forName(cls), name, types).invoke(null, arguments);
    }
    private static Method method(Class<?> cls, String name, Class<?>[] types) throws NoSuchMethodException {
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            try { Method m = current.getDeclaredMethod(name, types); m.setAccessible(true); return m; }
            catch (NoSuchMethodException absent) { }
        }
        throw new NoSuchMethodException(name);
    }
}
