package me.onils.fixtargetcalculate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Hooks {

    private Hooks() {}

    private static Object lockedEntity; // net.minecraft.entity.Entity

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

            // If no entity is currently targeted, release the lock
            if (currentEntity == null) {
                lockedEntity = null;
                debugLog("No target -> release lock");
                return;
            }

            // Only handle players; otherwise clear lock and return
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

            // If it's the same entity, refresh the lock
            if (isSameEntity(lockedEntity, currentEntity)) {
                lockedEntity = currentEntity;
                debugLog("Keep lock -> " + entityLabel(currentEntity));
                return;
            }

            // If previous locked is still alive and in the same block as the currently traced player,
            // keep using the previously locked one to emulate 1.8.8 behavior (fixed target within block).
            if (isAlive(lockedEntity) && sameBlock(lockedEntity, currentEntity)) {
                Object mopFromLocked = newMopFromEntity(lockedEntity);
                if (mopFromLocked != null) {
                    fObjectMouseOver.set(mc, mopFromLocked);
                    debugLog("Override objectMouseOver -> " + entityLabel(lockedEntity));
                    // keep the older lock (do not switch)
                    return;
                }
            }

            // Otherwise, switch lock to the new entity
            lockedEntity = currentEntity;
        } catch (Throwable ignored) {
            // Never let HUD crash
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
        return true; // assume true to avoid over-releasing
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

            // Try constructor MovingObjectPosition(Entity, Vec3)
            try {
                Class<?> vec3Class = findClass("net.minecraft.util.Vec3");
                if (vec3Class != null) {
                    Constructor<?> c2 = mopClass.getDeclaredConstructor(findClass("net.minecraft.entity.Entity"), vec3Class);
                    c2.setAccessible(true);
                    // We don't know the exact hitVec; pass null – vanilla tolerates this and only checks entityHit.
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
        // also try in superclasses
        Class<?> sup = owner.getSuperclass();
        if (sup != null) return findField(sup, names);
        // try public
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
        // search in superclass hierarchy
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
        if (now - lastLogMs < 1000L) return; // throttle to 1 msg/sec
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
}
