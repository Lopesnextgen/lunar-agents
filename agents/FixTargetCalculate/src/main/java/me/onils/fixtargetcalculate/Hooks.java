package me.onils.fixtargetcalculate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

public final class Hooks {

    private Hooks() {}

    private static Object lockedEntity;

    public static volatile boolean DEBUG = false;
    private static boolean printedHookActive = false;
    private static long lastLogMs = 0L;

    public static void afterGetMouseOver(Object entityRenderer, float partialTicks) {
        if (entityRenderer == null) return;
        try {
            if (DEBUG && !printedHookActive) {
                printedHookActive = true;
                System.out.println("[FixTargetCalculate] Hook active");
            }
            Class<?> mcClass = findClass("net.minecraft.client.Minecraft");
            if (mcClass == null) return;

            Method getMc = findMethod(mcClass, new String[]{"getMinecraft", "func_71410_x"});
            if (getMc == null) return;
            Object mc = getMc.invoke(null);
            if (mc == null) return;

            Field fObjectMouseOver = findField(mcClass, new String[]{"objectMouseOver", "field_71476_x"});
            if (fObjectMouseOver == null) return;
            Object movingObj = fObjectMouseOver.get(mc);

            Class<?> mopClass = findClass("net.minecraft.util.MovingObjectPosition");
            if (mopClass == null) return;
            Field fEntityHit = findField(mopClass, new String[]{"entityHit", "field_72308_g"});
            if (fEntityHit == null) return;

            Object currentEntity = movingObj != null ? fEntityHit.get(movingObj) : null;

            if (currentEntity == null) {
                lockedEntity = null;
                debugLog("No target -> release lock");
                return;
            }

            if (!isPlayer(currentEntity)) {
                lockedEntity = null;
                debugLog("Non-player target -> ignored");
                return;
            }

            if (lockedEntity == null || !isPlayer(lockedEntity)) {
                lockedEntity = currentEntity;
                debugLog("Lock -> " + entityLabel(currentEntity));
                return;
            }

            if (isSameEntity(lockedEntity, currentEntity)) {
                lockedEntity = currentEntity;
                debugLog("Keep lock -> " + entityLabel(currentEntity));
                return;
            }

            if (isAlive(lockedEntity) && sameBlock(lockedEntity, currentEntity)) {
                Object mopFromLocked = newMopFromEntity(lockedEntity);
                if (mopFromLocked != null) {
                    fObjectMouseOver.set(mc, mopFromLocked);
                    debugLog("Override objectMouseOver -> " + entityLabel(lockedEntity));
                    return;
                }
            }

            lockedEntity = currentEntity;
        } catch (Throwable ignored) {
        }
    }

    private static boolean isSameEntity(Object a, Object b) {
        if (a == b) return true;
        try {
            Method getId = findMethod(a.getClass(), new String[]{"getEntityId", "func_145782_y"});
            if (getId != null) {
                int ia = ((Number) getId.invoke(a)).intValue();
                int ib = ((Number) getId.invoke(b)).intValue();
                return ia == ib;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isAlive(Object entity) {
        try {
            Method alive = findMethod(entity.getClass(), new String[]{"isEntityAlive", "func_70089_S"});
            if (alive != null) return (Boolean) alive.invoke(entity);
        } catch (Throwable ignored) {}
        return true;
    }

    private static boolean isPlayer(Object obj) {
        try {
            Class<?> ep = findClass("net.minecraft.entity.player.EntityPlayer");
            return ep != null && ep.isInstance(obj);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean sameBlock(Object a, Object b) {
        try {
            int ax = (int) Math.floor(getDoubleField(a, new String[]{"posX", "field_70165_t"}));
            int ay = (int) Math.floor(getDoubleField(a, new String[]{"posY", "field_70163_u"}));
            int az = (int) Math.floor(getDoubleField(a, new String[]{"posZ", "field_70161_v"}));
            int bx = (int) Math.floor(getDoubleField(b, new String[]{"posX", "field_70165_t"}));
            int by = (int) Math.floor(getDoubleField(b, new String[]{"posY", "field_70163_u"}));
            int bz = (int) Math.floor(getDoubleField(b, new String[]{"posZ", "field_70161_v"}));
            return ax == bx && ay == by && az == bz;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean areEntitiesClose(Object e1, Object e2, double maxDist) {
        try {
            double dx = getDoubleField(e1, new String[]{"posX", "field_70165_t"}) - getDoubleField(e2, new String[]{"posX", "field_70165_t"});
            double dy = getDoubleField(e1, new String[]{"posY", "field_70163_u"}) - getDoubleField(e2, new String[]{"posY", "field_70163_u"});
            double dz = getDoubleField(e1, new String[]{"posZ", "field_70161_v"}) - getDoubleField(e2, new String[]{"posZ", "field_70161_v"});
            return dx * dx + dy * dy + dz * dz <= maxDist * maxDist;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double getDoubleField(Object obj, String[] names) throws Exception {
        Field f = findField(obj.getClass(), names);
        if (f == null) throw new NoSuchFieldException(names[0]);
        Object val = f.get(obj);
        if (val instanceof Number) return ((Number) val).doubleValue();
        throw new IllegalStateException("Not a number: " + val);
    }

    private static Object newMopFromEntity(Object entity) {
        try {
            Class<?> mopClass = findClass("net.minecraft.util.MovingObjectPosition");
            if (mopClass == null) return null;

            try {
                Class<?> vec3Class = findClass("net.minecraft.util.Vec3");
                if (vec3Class != null) {
                    Constructor<?> c2 = mopClass.getDeclaredConstructor(findClass("net.minecraft.entity.Entity"), vec3Class);
                    c2.setAccessible(true);
                    return c2.newInstance(entity, null);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }

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

    private static String entityLabel(Object entity) {
        if (entity == null) return "null";
        String name = null;
        try {
            Method getName = findMethod(entity.getClass(), new String[]{"getName", "getCommandSenderName", "func_70005_c_"});
            if (getName != null) {
                Object n = getName.invoke(entity);
                if (n != null) name = String.valueOf(n);
            }
        } catch (Throwable ignored) {}
        int id = -1;
        try {
            Method getId = findMethod(entity.getClass(), new String[]{"getEntityId", "func_145782_y"});
            if (getId != null) id = ((Number) getId.invoke(entity)).intValue();
        } catch (Throwable ignored) {}
        if (name == null || name.isEmpty()) {
            name = entity.getClass().getSimpleName();
        }
        return name + "(id=" + id + ")";
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
