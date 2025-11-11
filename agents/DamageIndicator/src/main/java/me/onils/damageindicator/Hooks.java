package me.onils.damageindicator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Hooks {

    private Hooks() {}

    public static void onRenderGameOverlay(Object guiIngame, float partialTicks) {
        try {
            // Minecraft instance
            Class<?> mcClass = findClass(
                    "net.minecraft.client.Minecraft"
            );
            if (mcClass == null) return;

            Method getMc = findMethod(mcClass, new String[]{"getMinecraft", "func_71410_x"});
            if (getMc == null) return;
            Object mc = getMc.invoke(null);
            if (mc == null) return;

            // objectMouseOver
            Field fObjectMouseOver = findField(mcClass, new String[]{"objectMouseOver", "field_71476_x"});
            if (fObjectMouseOver == null) return;
            Object movingObj = fObjectMouseOver.get(mc);
            if (movingObj == null) return;

            // entityHit
            Class<?> mopClass = findClass(
                    "net.minecraft.util.MovingObjectPosition",
                    "net.minecraft.util.MovingObjectPosition$MovingObjectType" // helps class loader in some versions
            );
            if (mopClass == null) return;

            Field fEntityHit = findField(mopClass, new String[]{"entityHit", "field_72308_g"});
            if (fEntityHit == null) return;
            Object entity = fEntityHit.get(movingObj);
            if (entity == null) return;

            // Ensure it's a player
            Class<?> entityPlayerClass = findClass("net.minecraft.entity.player.EntityPlayer");
            if (entityPlayerClass == null || !entityPlayerClass.isInstance(entity)) return;

            // Extract name and health
            String name = null;
            try {
                Method getName = findMethod(entity.getClass(), new String[]{"getName", "func_70005_c_"});
                if (getName != null) name = String.valueOf(getName.invoke(entity));
            } catch (Throwable ignored) {}
            if (name == null) name = "Player";

            float health = -1f;
            try {
                // EntityPlayer extends EntityLivingBase
                Method getHealth = findMethod(entity.getClass(), new String[]{"getHealth", "func_110143_aJ"});
                if (getHealth == null) {
                    // search in superclass if needed
                    Class<?> sup = entity.getClass().getSuperclass();
                    if (sup != null) getHealth = findMethod(sup, new String[]{"getHealth", "func_110143_aJ"});
                }
                if (getHealth != null) {
                    Object h = getHealth.invoke(entity);
                    if (h instanceof Float) health = (Float) h;
                    else if (h instanceof Number) health = ((Number) h).floatValue();
                }
            } catch (Throwable ignored) {}

            // Prepare renderer and scaled resolution
            Object fontRenderer = null;
            try {
                Field frField = findField(mcClass, new String[]{"fontRendererObj", "field_71466_p"});
                if (frField != null) fontRenderer = frField.get(mc);
            } catch (Throwable ignored) {}
            if (fontRenderer == null) return;

            int width = 0, height = 0;
            try {
                Class<?> scaledResClass = findClass("net.minecraft.client.gui.ScaledResolution");
                if (scaledResClass != null) {
                    Constructor<?> cons = scaledResClass.getDeclaredConstructor(mcClass);
                    cons.setAccessible(true);
                    Object sr = cons.newInstance(mc);
                    Method getW = findMethod(scaledResClass, new String[]{"getScaledWidth", "func_78326_a"});
                    Method getH = findMethod(scaledResClass, new String[]{"getScaledHeight", "func_78328_b"});
                    if (getW != null) width = ((Number) getW.invoke(sr)).intValue();
                    if (getH != null) height = ((Number) getH.invoke(sr)).intValue();
                }
            } catch (Throwable ignored) {}
            if (width <= 0 || height <= 0) return;

            String line1 = name;
            String line2 = (health >= 0 ? String.format("HP: %.1f", health) : "HP: ?");

            int x = width / 2;
            int y = height / 2 - 25; // above crosshair

            // Try GuiIngame.drawCenteredString first
            boolean drawn = false;
            try {
                Method drawCentered = findMethod(guiIngame.getClass(), new String[]{"drawCenteredString", "func_73732_a"},
                        findClass("net.minecraft.client.gui.FontRenderer"), int.class, int.class, int.class);
                if (drawCentered != null) {
                    drawCentered.invoke(guiIngame, fontRenderer, line1, x, y, 0xFFFFFF);
                    drawCentered.invoke(guiIngame, fontRenderer, line2, x, y + 10, 0xFFFFFF);
                    drawn = true;
                }
            } catch (Throwable ignored) {}

            // Fallback to FontRenderer.drawStringWithShadow
            if (!drawn) {
                try {
                    Method drawShadow = findMethod(fontRenderer.getClass(), new String[]{"drawStringWithShadow", "func_175063_a"}, String.class, float.class, float.class, int.class);
                    if (drawShadow != null) {
                        drawShadow.invoke(fontRenderer, line1, (float) x - getStringHalfWidth(fontRenderer, line1), (float) y, 0xFFFFFF);
                        drawShadow.invoke(fontRenderer, line2, (float) x - getStringHalfWidth(fontRenderer, line2), (float) y + 10, 0xFFFFFF);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            // Swallow any exception to avoid crashing the client HUD
        }
    }

    private static float getStringHalfWidth(Object fontRenderer, String s) {
        try {
            Method getStringWidth = findMethod(fontRenderer.getClass(), new String[]{"getStringWidth", "func_78256_a"}, String.class);
            if (getStringWidth != null) return ((Number) getStringWidth.invoke(fontRenderer, s)).floatValue() / 2f;
        } catch (Throwable ignored) {}
        return 0f;
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
        // try public fields too
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
        for (String name : names) {
            try {
                Method m = owner.getMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
