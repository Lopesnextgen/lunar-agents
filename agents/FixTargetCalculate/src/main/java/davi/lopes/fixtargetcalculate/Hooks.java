package davi.lopes.fixtargetcalculate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

public final class Hooks {

    private Hooks() {}


    public static volatile boolean DEBUG = false;
    private static boolean printedHookActive = false;
    private static long lastLogMs = 0L;









    private static Class<?> findClass(String... names) {
        for (String n : names) {
            try {
                return Class.forName(n);
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private static Field findField(Class<?> owner, String[] names) {
        for (String name : names) {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {}
        }
        Class<?> sup = owner.getSuperclass();
        if (sup != null) return findField(sup, names);
        for (String name : names) {
            try {
                Field f = owner.getField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, String[] names, Class<?>... params) {
        for (String name : names) {
            try {
                Method m = owner.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {}
        }
        Class<?> sup = owner.getSuperclass();
        if (sup != null) {
            Method m = findMethod(sup, names, params);
            if (m != null) return m;
        }
        for (String name : names) {
            try {
                Method m = owner.getMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void debugLog(String msg) {
        if (!DEBUG) return;
        long now = System.currentTimeMillis();
        if (now - lastLogMs < 1000L) return;
        lastLogMs = now;
        try {
            System.out.println("[FixTargetCalculate] " + msg);
        } catch (Throwable ignored) {
        }
    }


    private static final String INJECTED_LIST = "agent_cachedEntityList";
    private static final String INJECTED_LAST = "agent_lastEntityListUpdate";

    private static final String[] WORLD_LOADED_LIST = {"loadedEntityList", "field_72996_f"};
    private static final String[] ENTITY_GET_BOX = {"getEntityBoundingBox", "func_174813_aQ"};
    private static final String[] AABB_INTERSECTS = {"intersectsWith", "func_72326_a"};

    private static final long CACHE_INTERVAL_MS = 50L;

    private static final WeakHashMap<Object, WorldCache> FALLBACK = new WeakHashMap<>();

    public static List<?> getLoadedEntityListHook(Object world) {
        if (world == null) return Collections.emptyList();
        try {
            Field listF = getDeclared(world.getClass(), INJECTED_LIST);
            Field lastF = getDeclared(world.getClass(), INJECTED_LAST);
            if (listF != null && lastF != null) {
                @SuppressWarnings("unchecked")
                List<?> cached = (List<?>) listF.get(world);
                long last = lastF.getLong(world);
                long now = System.currentTimeMillis();
                if (cached != null && (now - last) < CACHE_INTERVAL_MS) {
                    return cached;
                }
                List<?> src = readLoadedEntityList(world);
                if (src == null) return cached != null ? cached : Collections.emptyList();
                List<?> snapshot = new ArrayList<>(src);
                listF.set(world, snapshot);
                lastF.setLong(world, now);
                debugLog("[World] cache refresh size=" + snapshot.size());
                return snapshot;
            }
        } catch (Throwable ignored) {}

        synchronized (FALLBACK) {
            WorldCache wc = FALLBACK.computeIfAbsent(world, k -> new WorldCache());
            long now = System.currentTimeMillis();
            if (wc.cached != null && (now - wc.last) < CACHE_INTERVAL_MS) {
                return wc.cached;
            }
            List<?> src = readLoadedEntityList(world);
            if (src == null) return wc.cached != null ? wc.cached : Collections.emptyList();
            wc.cached = new ArrayList<>(src);
            wc.last = now;
            debugLog("[World:fallback] cache refresh size=" + wc.cached.size());
            return wc.cached;
        }
    }

    public static List<?> getEntitiesAABBHook(Object world, Class<?> entityClass, Object aabb) {
        try {
            List<?> list = getLoadedEntityListHook(world);
            if (list.isEmpty()) return Collections.emptyList();

            Method getBox = null;
            Method intersects = null;
            Class<?> aabbClass = aabb != null ? aabb.getClass() : findClass("net.minecraft.util.AxisAlignedBB");
            if (aabbClass != null) intersects = findMethod(aabbClass, AABB_INTERSECTS, aabbClass);

            List<Object> out = new ArrayList<>();
            for (Object e : list) {
                if (e == null) continue;
                if (entityClass != null && !entityClass.isInstance(e)) continue;
                if (getBox == null || getBox.getDeclaringClass() != e.getClass()) {
                    getBox = findMethod(e.getClass(), ENTITY_GET_BOX);
                }
                if (getBox == null) continue;
                Object eb = getBox.invoke(e);
                if (eb == null || aabb == null || intersects == null) continue;
                Boolean ok = (Boolean) intersects.invoke(aabb, eb);
                if (ok != null && ok) out.add(e);
            }
            debugLog("[World] AABB filtered size=" + out.size());
            return out;
        } catch (Throwable t) {
            List<?> vanilla = readLoadedEntityList(world);
            return vanilla != null ? new ArrayList<>(vanilla) : Collections.emptyList();
        }
    }

    public static List<?> getEntitiesInAABBExcludingHook(Object world, Object exclude, Object aabb, Object predicate) {
        try {
            List<?> list = getLoadedEntityListHook(world);
            if (list.isEmpty()) return Collections.emptyList();

            Method getBox = null;
            Method intersects = null;
            Class<?> aabbClass = aabb != null ? aabb.getClass() : findClass("net.minecraft.util.AxisAlignedBB");
            if (aabbClass != null) intersects = findMethod(aabbClass, AABB_INTERSECTS, aabbClass);

            List<Object> out = new ArrayList<>();
            for (Object e : list) {
                if (e == null) continue;
                if (e == exclude) continue;
                if (getBox == null || getBox.getDeclaringClass() != e.getClass()) {
                    getBox = findMethod(e.getClass(), ENTITY_GET_BOX);
                }
                if (getBox == null) continue;
                Object eb = getBox.invoke(e);
                if (eb == null || aabb == null || intersects == null) continue;
                Boolean ok = (Boolean) intersects.invoke(aabb, eb);
                if (ok == null || !ok) continue;
                if (predicate != null && !applyPredicate(predicate, e)) continue;
                out.add(e);
            }
            debugLog("[World] AABB excluding filtered size=" + out.size());
            return out;
        } catch (Throwable t) {
            List<?> vanilla = readLoadedEntityList(world);
            return vanilla != null ? new ArrayList<>(vanilla) : Collections.emptyList();
        }
    }

    private static boolean applyPredicate(Object predicate, Object e) {
        try {
            if (predicate == null) return true;
            for (Method m : predicate.getClass().getMethods()) {
                if (!"apply".equals(m.getName())) continue;
                if (m.getParameterTypes().length != 1) continue;
                m.setAccessible(true);
                Object r = m.invoke(predicate, e);
                if (r instanceof Boolean) return (Boolean) r;
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public static void onWorldUpdate(Object world) {
        if (world == null) return;
        try {
            Field lastF = getDeclared(world.getClass(), INJECTED_LAST);
            if (lastF != null) {
                lastF.setLong(world, 0L);
                debugLog("[World] cache invalidated after update");
                return;
            }
        } catch (Throwable ignored) {}
        synchronized (FALLBACK) {
            WorldCache wc = FALLBACK.get(world);
            if (wc != null) wc.last = 0L;
        }
    }

    private static List<?> readLoadedEntityList(Object world) {
        try {
            Field f = findField(world.getClass(), WORLD_LOADED_LIST);
            if (f == null) return null;
            Object val = f.get(world);
            if (val instanceof List) return (List<?>) val;
        } catch (Throwable ignored) {}
        return null;
    }

    private static Field getDeclared(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) { return null; }
    }

    private static final class WorldCache {
        List<?> cached;
        long last;
    }
}
