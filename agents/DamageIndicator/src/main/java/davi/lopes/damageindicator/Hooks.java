package davi.lopes.damageindicator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public final class Hooks {

    private Hooks() {}

    private static boolean cfgLoaded = false;
    private static String positionMode = "centralizado";
    private static int fixedX = 0;
    private static int fixedY = 0;
    private static int offsetX = 0;
    private static int offsetY = 0;

    private static boolean editMode = false;
    private static boolean prevF7 = false, prevEnter = false, prevEsc = false, prevM = false;
    private static String bakPositionMode = null;
    private static int bakFixedX = 0, bakFixedY = 0, bakOffsetX = 0, bakOffsetY = 0;

    private static void loadCfgIfNeeded() {
        if (cfgLoaded) return;
        cfgLoaded = true;
        positionMode = "centralizado";
        fixedX = 0; fixedY = 0; offsetX = 0; offsetY = 0;
        try { loadFromFile(); } catch (Throwable ignored) {}
        try {
            String v;
            v = getenv("DAMAGEINDICATOR_POSITION"); if (notEmpty(v)) positionMode = v.trim().toLowerCase();
            v = getenv("DAMAGEINDICATOR_X"); if (notEmpty(v)) fixedX = parseIntSafe(v, fixedX);
            v = getenv("DAMAGEINDICATOR_Y"); if (notEmpty(v)) fixedY = parseIntSafe(v, fixedY);
            v = getenv("DAMAGEINDICATOR_OFFSET_X"); if (notEmpty(v)) offsetX = parseIntSafe(v, offsetX);
            v = getenv("DAMAGEINDICATOR_OFFSET_Y"); if (notEmpty(v)) offsetY = parseIntSafe(v, offsetY);
        } catch (Throwable ignored) {}
        try {
            String v;
            v = getprop("damageindicator.position"); if (notEmpty(v)) positionMode = v.trim().toLowerCase();
            v = getprop("damageindicator.x"); if (notEmpty(v)) fixedX = parseIntSafe(v, fixedX);
            v = getprop("damageindicator.y"); if (notEmpty(v)) fixedY = parseIntSafe(v, fixedY);
            v = getprop("damageindicator.offsetX"); if (notEmpty(v)) offsetX = parseIntSafe(v, offsetX);
            v = getprop("damageindicator.offsetY"); if (notEmpty(v)) offsetY = parseIntSafe(v, offsetY);
        } catch (Throwable ignored) {}
    }

    private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }
    private static String getprop(String k) { try { return System.getProperty(k); } catch (Throwable t) { return null; } }
    private static String getenv(String k) { try { return System.getenv(k); } catch (Throwable t) { return null; } }
    private static int parseIntSafe(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; } }

    private static File getConfigFile() {
        String home = null;
        try { home = System.getProperty("user.home"); } catch (Throwable ignored) {}
        if (home == null || home.isEmpty()) return null;
        File dir = new File(home + File.separator + ".lunar-agents");
        return new File(dir, "damageindicator.properties");
    }

    private static void loadFromFile() {
        File f = getConfigFile();
        if (f == null || !f.exists() || !f.isFile()) return;
        FileInputStream in = null;
        try {
            Properties p = new Properties();
            in = new FileInputStream(f);
            p.load(in);
            String v;
            v = p.getProperty("position"); if (notEmpty(v)) positionMode = v.trim().toLowerCase();
            v = p.getProperty("x"); if (notEmpty(v)) fixedX = parseIntSafe(v, fixedX);
            v = p.getProperty("y"); if (notEmpty(v)) fixedY = parseIntSafe(v, fixedY);
            v = p.getProperty("offsetX"); if (notEmpty(v)) offsetX = parseIntSafe(v, offsetX);
            v = p.getProperty("offsetY"); if (notEmpty(v)) offsetY = parseIntSafe(v, offsetY);
        } catch (Throwable ignored) {
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
        }
    }

    private static void saveToFile() {
        File f = getConfigFile();
        if (f == null) return;
        File dir = f.getParentFile();
        try { if (dir != null && !dir.exists()) dir.mkdirs(); } catch (Throwable ignored) {}
        Properties p = new Properties();
        p.setProperty("position", positionMode == null ? "centralizado" : positionMode);
        p.setProperty("x", String.valueOf(fixedX));
        p.setProperty("y", String.valueOf(fixedY));
        p.setProperty("offsetX", String.valueOf(offsetX));
        p.setProperty("offsetY", String.valueOf(offsetY));
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            p.store(out, "DamageIndicator config");
        } catch (Throwable ignored) {
        } finally {
            try { if (out != null) out.close(); } catch (Throwable ignored) {}
        }
    }

    public static void onRenderGameOverlay(Object guiIngame, float partialTicks) {
        try {
            loadCfgIfNeeded();

            Class<?> mcClass = findClass(
                    "net.minecraft.client.Minecraft"
            );
            if (mcClass == null) return;

            Method getMc = findMethod(mcClass, new String[]{"getMinecraft", "func_71410_x"});
            if (getMc == null) return;
            Object mc = getMc.invoke(null);
            if (mc == null) return;

            Object entity = null;
            try {
                Object looked = findLookedEntity(mc, partialTicks, 20.0d);
                if (looked != null) entity = looked;
            } catch (Throwable ignored) {}
            if (entity == null) {
                try {
                    Field fObjectMouseOver = findField(mcClass, new String[]{"objectMouseOver", "field_71476_x"});
                    Object movingObj = (fObjectMouseOver != null ? fObjectMouseOver.get(mc) : null);
                    if (movingObj != null) {
                        Class<?> mopClass = movingObj.getClass();
                        Field fEntityHit = findField(mopClass, new String[]{"entityHit", "field_72308_g"});
                        if (fEntityHit != null) entity = fEntityHit.get(movingObj);
                    }
                } catch (Throwable ignored) {}
            }
            if (entity == null && !editMode) return;

            Class<?> entityLivingBaseClass = findClass("net.minecraft.entity.EntityLivingBase");
            if (!editMode) {
                if (entityLivingBaseClass == null || !entityLivingBaseClass.isInstance(entity)) return;
            }

            boolean hasTarget = entity != null && entityLivingBaseClass != null && entityLivingBaseClass.isInstance(entity);

            String name = null;
            int realHealth = -1;
            int maxHpInt = -1;
            if (hasTarget) {
                try {
                    Method getName = findMethod(entity.getClass(), new String[]{"getName", "func_70005_c_"});
                    if (getName != null) name = String.valueOf(getName.invoke(entity));
                } catch (Throwable ignored) {}
                if (name == null) name = "Player";

                realHealth = getRealHealth(entity);
                try {
                    Method getMaxHealth = findMethod(entity.getClass(), new String[]{"getMaxHealth", "func_110138_aP"});
                    if (getMaxHealth == null) {
                        Class<?> sup = entity.getClass().getSuperclass();
                        if (sup != null) getMaxHealth = findMethod(sup, new String[]{"getMaxHealth", "func_110138_aP"});
                    }
                    if (getMaxHealth != null) {
                        Object mh = getMaxHealth.invoke(entity);
                        if (mh instanceof Number) maxHpInt = ((Number) mh).intValue();
                    }
                } catch (Throwable ignored) {}
            } else {
                name = "DamageIndicator (Edit Mode)";
                realHealth = 20;
                maxHpInt = 20;
            }

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

            try {
                boolean f7Now = isKeyDown(65);
                if (f7Now && !prevF7) {
                    if (!editMode) {
                        editMode = true;
                        bakPositionMode = positionMode;
                        bakFixedX = fixedX; bakFixedY = fixedY; bakOffsetX = offsetX; bakOffsetY = offsetY;
                    } else {
                        editMode = false;
                    }
                }
                prevF7 = f7Now;

                if (editMode) {
                    boolean mNow = isKeyDown(50);
                    if (mNow && !prevM) {
                        if ("fixo".equals(positionMode)) {
                            int centerX = width / 2;
                            int centerY = height / 2 - 25;
                            offsetX = fixedX - centerX;
                            offsetY = fixedY - centerY;
                            positionMode = "centralizado";
                        } else {
                            int centerX = width / 2;
                            int centerY = height / 2 - 25;
                            fixedX = centerX + offsetX;
                            fixedY = centerY + offsetY;
                            positionMode = "fixo";
                        }
                    }
                    prevM = mNow;

                    int speed = 1;
                    if (isKeyDown(42) || isKeyDown(54)) speed = 5;
                    if (isKeyDown(29) || isKeyDown(157)) speed = 20;
                    int dx = 0, dy = 0;
                    if (isKeyDown(203)) dx -= speed;
                    if (isKeyDown(205)) dx += speed;
                    if (isKeyDown(200)) dy -= speed;
                    if (isKeyDown(208)) dy += speed;
                    if (dx != 0 || dy != 0) {
                        if ("fixo".equals(positionMode)) { fixedX += dx; fixedY += dy; }
                        else { offsetX += dx; offsetY += dy; }
                    }

                    boolean enterNow = isKeyDown(28) || isKeyDown(156);
                    if (enterNow && !prevEnter) {
                        saveToFile();
                        editMode = false;
                    }
                    prevEnter = enterNow;

                    boolean escNow = isKeyDown(1);
                    if (escNow && !prevEsc) {
                        if (bakPositionMode != null) positionMode = bakPositionMode;
                        fixedX = bakFixedX; fixedY = bakFixedY; offsetX = bakOffsetX; offsetY = bakOffsetY;
                        editMode = false;
                    }
                    prevEsc = escNow;
                }
            } catch (Throwable ignored) {}

            String line1 = '[' + name + ']';
            String line2;
            if (realHealth >= 0 && maxHpInt >= 0) {
                line2 = realHealth + " §4\u2665§r/" + maxHpInt + " §4\u2665";
            } else if (realHealth >= 0) {
                line2 = realHealth + " §4\u2665";
            } else {
                line2 = "? §4\u2665";
            }

            int baseX;
            int baseY;
            if ("fixo".equals(positionMode)) {
                baseX = fixedX;
                baseY = fixedY;
            } else {
                baseX = width / 2 + offsetX;
                baseY = height / 2 - 25 + offsetY;
            }

            int textX = baseX;
            int textY = baseY;
            try {
                if (!"fixo".equals(positionMode)) {
                    float half1 = getStringHalfWidth(fontRenderer, line1);
                    float half2 = getStringHalfWidth(fontRenderer, line2);
                    int left = (int) (baseX - Math.max(half1, half2));
                    textX = left;
                }
            } catch (Throwable ignored) {}

            int fontH = getFontHeight(fontRenderer);

            int boxLeft = textX - 4;
            int boxTop = textY - 4;
            int contentWidth = Math.max(getStringWidth(fontRenderer, line1), getStringWidth(fontRenderer, line2));
            int boxHeightBase = fontH * 2 + 8;
            int boxRight = boxLeft + contentWidth + 8;
            int boxBottom = boxTop + boxHeightBase;

            if (editMode) {
                fillRect(boxLeft, boxTop, boxRight, boxBottom, 0x66000000);
                drawHollowRect(boxLeft, boxTop, boxRight, boxBottom, 0xCC00A2FF);
            }

            try {
                Method drawShadow = findMethod(fontRenderer.getClass(), new String[]{"drawStringWithShadow", "func_175063_a"}, String.class, float.class, float.class, int.class);
                if (drawShadow != null) {
                    drawShadow.invoke(fontRenderer, line1, (float) textX, (float) textY, 0xFFFFFF);
                    drawShadow.invoke(fontRenderer, line2, (float) (textX), (float) (textY + fontH), 0xFFFFFF);
                }
            } catch (Throwable ignored) {}

            if (editMode) {
                String tip = "F7 sair | M modo | Setas mover | Shift/Ctrl velocidade | Enter salvar | Esc cancelar";
                int tipW = getStringWidth(fontRenderer, tip);
                int tipX = Math.max(2, (boxLeft + boxRight) / 2 - tipW / 2);
                int tipY = boxBottom + 2;
                drawString(fontRenderer, tip, tipX, tipY, 0xFFFFFF);

                String credits = "github.com/Lopesnextgen | github.com/Adoecido";
                int credY = Math.max(2, height - fontH - 2);
                drawString(fontRenderer, credits, 2, credY, 0xFFFFFF);
            }

            if (hasTarget) {
                try {
                    Object[] byType = new Object[4];
                    for (int i = 0; i < 4; i++) {
                        Object st = getCurrentArmor(entity, i);
                        if (st != null) {
                            Object item = invoke(st.getClass(), st, new String[]{"getItem", "func_77973_b"});
                            Class<?> itemArmorClass = findClass("net.minecraft.item.ItemArmor");
                            if (item != null && itemArmorClass != null && itemArmorClass.isInstance(item)) {
                                int armorType = getIntField(item.getClass(), item, new String[]{"armorType"});
                                if (armorType >= 0 && armorType < 4) byType[armorType] = st;
                            }
                        }
                    }

                    int yOffset = textY + fontH * 2;
                    int iconX = textX;
                    int textValX = textX + 20;

                    Class<?> renderHelperClass = findClass("net.minecraft.client.renderer.RenderHelper");
                    Method enableGuiLight = findMethod(renderHelperClass, new String[]{"enableGUIStandardItemLighting", "func_74520_c"});
                    Method disableStdLight = findMethod(renderHelperClass, new String[]{"disableStandardItemLighting", "func_74519_b"});
                    if (enableGuiLight != null) enableGuiLight.invoke(null);

                    Object renderItem = getRenderItem(mc);
                    Method renderItemIntoGui = (renderItem == null ? null : findMethod(renderItem.getClass(), new String[]{"renderItemIntoGUI", "func_180450_b"}, findClass("net.minecraft.item.ItemStack"), int.class, int.class));

                    for (int t = 0; t < 4; t++) {
                        Object stack = byType[t];
                        if (stack == null) continue;

                        int max = getInt(invoke(stack.getClass(), stack, new String[]{"getMaxDamage", "func_77958_k"}), -1);
                        int dmg = getInt(invoke(stack.getClass(), stack, new String[]{"getItemDamage", "func_77952_i"}), 0);
                        int dur = max >= 0 ? (max - dmg) : 0;
                        float pct = (max > 0) ? ((float) dur / (float) max) : 1.0f;
                        int color = (pct > 0.7f) ? 5635925 : ((pct > 0.3f) ? 16777045 : 16733525);

                        int count = countArmorInInventory(mc, entity, t);
                        if (count >= 0) {
                            String cntStr = String.valueOf(count);
                            int numX = iconX - 2 - getStringWidth(fontRenderer, cntStr);
                            drawString(fontRenderer, cntStr, numX, yOffset + 4, 0xFFFFFF);
                        }

                        if (renderItem != null && renderItemIntoGui != null) {
                            renderItemIntoGui.invoke(renderItem, stack, iconX, yOffset);
                        }

                        drawString(fontRenderer, "(" + dur + ")", textValX, yOffset + 4, color);
                        yOffset += 16;
                    }

                    if (disableStdLight != null) disableStdLight.invoke(null);
                } catch (Throwable ignored) {}
            }

        } catch (Throwable ignored) {
        }
    }

    public static int getRealHealth(Object entity) {
        if (entity == null) return -1;
        try {
            Field fWorld = findField(entity.getClass(), new String[]{"worldObj", "field_70170_p"});
            Object world = (fWorld != null ? fWorld.get(entity) : null);
            if (world != null) {
                Method getScoreboard = findMethod(world.getClass(), new String[]{"getScoreboard", "func_96441_U"});
                Object scoreboard = (getScoreboard != null ? getScoreboard.invoke(world) : null);
                if (scoreboard != null) {
                    Method getObjectiveInDisplaySlot = findMethod(scoreboard.getClass(), new String[]{"getObjectiveInDisplaySlot", "func_96539_a"}, int.class);
                    Object objective = (getObjectiveInDisplaySlot != null ? getObjectiveInDisplaySlot.invoke(scoreboard, 2) : null);
                    if (objective != null) {
                        Method getName = findMethod(entity.getClass(), new String[]{"getName", "func_70005_c_"});
                        String name = (getName != null ? String.valueOf(getName.invoke(entity)) : null);
                        Method getValueFromObjective = findMethod(scoreboard.getClass(), new String[]{"getValueFromObjective", "func_96529_a"}, String.class, findClass("net.minecraft.scoreboard.ScoreObjective"));
                        Object score = (name != null && getValueFromObjective != null ? getValueFromObjective.invoke(scoreboard, name, objective) : null);
                        if (score != null) {
                            Method getScorePoints = findMethod(score.getClass(), new String[]{"getScorePoints", "func_96652_c"});
                            int healthFromScoreboard = (getScorePoints != null ? getInt(getScorePoints.invoke(score), -1) : -1);
                            if (healthFromScoreboard > 1) return healthFromScoreboard;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            Method getHealth = findMethod(entity.getClass(), new String[]{"getHealth", "func_110143_aJ"});
            Object h = (getHealth != null ? getHealth.invoke(entity) : null);
            if (h instanceof Number) return ((Number) h).intValue();
        } catch (Throwable ignored) {}
        return -1;
    }

    private static int countArmorInInventory(Object mc, Object targetEntity, int armorType) {
        try {
            Field fThePlayer = findField(mc.getClass(), new String[]{"thePlayer", "field_71439_g"});
            Object thePlayer = (fThePlayer != null ? fThePlayer.get(mc) : null);
            if (thePlayer == null || !thePlayer.getClass().isInstance(targetEntity)) return -1;

            Field fInventory = findField(thePlayer.getClass(), new String[]{"inventory", "field_71071_by"});
            Object inventory = (fInventory != null ? fInventory.get(thePlayer) : null);
            if (inventory == null) return -1;
            Field fMain = findField(inventory.getClass(), new String[]{"mainInventory", "field_70462_a"});
            Object main = (fMain != null ? fMain.get(inventory) : null);
            if (!(main instanceof Object[])) return -1;
            Object[] stacks = (Object[]) main;

            int count = 0;
            Class<?> itemArmorClass = findClass("net.minecraft.item.ItemArmor");
            for (Object stack : stacks) {
                if (stack == null) continue;
                Object item = invoke(stack.getClass(), stack, new String[]{"getItem", "func_77973_b"});
                if (item == null || itemArmorClass == null || !itemArmorClass.isInstance(item)) continue;
                int at = getIntField(item.getClass(), item, new String[]{"armorType"});
                if (at == armorType) {
                    int size = getIntField(stack.getClass(), stack, new String[]{"stackSize", "field_77994_a"});
                    if (size > 0) count += size;
                }
            }
            return count;
        } catch (Throwable ignored) {}
        return -1;
    }

    private static Object getCurrentArmor(Object entity, int slot) {
        try {
            Method m = findMethod(entity.getClass(), new String[]{"getCurrentArmor", "func_82169_q"}, int.class);
            if (m != null) return m.invoke(entity, slot);
        } catch (Throwable ignored) {}
        return null;
    }

    private static int getFontHeight(Object fontRenderer) {
        try {
            Field f = findField(fontRenderer.getClass(), new String[]{"FONT_HEIGHT", "field_78286_d"});
            if (f != null) return ((Number) f.get(fontRenderer)).intValue();
        } catch (Throwable ignored) {}
        return 10;
    }

    private static int getStringWidth(Object fontRenderer, String s) {
        try {
            Method getStringWidth = findMethod(fontRenderer.getClass(), new String[]{"getStringWidth", "func_78256_a"}, String.class);
            if (getStringWidth != null) return ((Number) getStringWidth.invoke(fontRenderer, s)).intValue();
        } catch (Throwable ignored) {}
        return s != null ? s.length() * 6 : 0;
    }

    private static void drawString(Object fontRenderer, String s, int x, int y, int color) {
        try {
            Method drawShadow = findMethod(fontRenderer.getClass(), new String[]{"drawStringWithShadow", "func_175063_a"}, String.class, float.class, float.class, int.class);
            if (drawShadow != null) drawShadow.invoke(fontRenderer, s, (float) x, (float) y, color);
        } catch (Throwable ignored) {}
    }

    private static Object getRenderItem(Object mc) {
        try {
            Method m = findMethod(mc.getClass(), new String[]{"getRenderItem", "func_175599_af"});
            if (m != null) return m.invoke(mc);
        } catch (Throwable ignored) {}
        try {
            Field f = findField(mc.getClass(), new String[]{"renderItem", "field_175621_X"});
            if (f != null) return f.get(mc);
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object invoke(Class<?> owner, Object instance, String[] names, Object... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = (args[i] == null) ? Object.class : args[i].getClass();
            for (String n : names) {
                try {
                    for (Method m : owner.getMethods()) {
                        if (!m.getName().equals(n)) continue;
                        if (m.getParameterTypes().length != args.length) continue;
                        m.setAccessible(true);
                        return m.invoke(instance, args);
                    }
                } catch (Throwable ignored) {}
                try {
                    for (Method m : owner.getDeclaredMethods()) {
                        if (!m.getName().equals(n)) continue;
                        if (m.getParameterTypes().length != args.length) continue;
                        m.setAccessible(true);
                        return m.invoke(instance, args);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int getInt(Object numberObj, int def) {
        if (numberObj instanceof Number) return ((Number) numberObj).intValue();
        return def;
    }

    private static int getIntField(Class<?> owner, Object instance, String[] names) {
        for (String n : names) {
            try {
                Field f = owner.getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(instance);
                if (v instanceof Number) return ((Number) v).intValue();
            } catch (Throwable ignored) {}
            try {
                Field f = owner.getField(n);
                f.setAccessible(true);
                Object v = f.get(instance);
                if (v instanceof Number) return ((Number) v).intValue();
            } catch (Throwable ignored) {}
        }
        return -1;
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

    private static boolean isKeyDown(int keyCode) {
        try {
            Class<?> keyboard = findClass("org.lwjgl.input.Keyboard");
            if (keyboard == null) return false;
            Method isKeyDown = findMethod(keyboard, new String[]{"isKeyDown"}, int.class);
            if (isKeyDown == null) return false;
            Object v = isKeyDown.invoke(null, keyCode);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {}
        return false;
    }

    private static void fillRect(int left, int top, int right, int bottom, int color) {
        try {
            int l = Math.min(left, right);
            int r = Math.max(left, right);
            int t = Math.min(top, bottom);
            int b = Math.max(top, bottom);
            Class<?> gui = findClass("net.minecraft.client.gui.Gui");
            Method drawRect = findMethod(gui, new String[]{"drawRect", "func_73734_a"}, int.class, int.class, int.class, int.class, int.class);
            if (drawRect != null) drawRect.invoke(null, l, t, r, b, color);
        } catch (Throwable ignored) {}
    }

    private static void drawHollowRect(int left, int top, int right, int bottom, int color) {
        fillRect(left, top, right, top + 1, color);
        fillRect(left, bottom - 1, right, bottom, color);
        fillRect(left, top, left + 1, bottom, color);
        fillRect(right - 1, top, right, bottom, color);
    }

    private static Object findLookedEntity(Object mc, float partialTicks, double maxDist) {
        try {
            if (mc == null) return null;
            Field fPlayer = findField(mc.getClass(), new String[]{"thePlayer", "field_71439_g"});
            Object player = (fPlayer != null ? fPlayer.get(mc) : null);
            if (player == null) return null;

            Field fWorld = findField(player.getClass(), new String[]{"worldObj", "field_70170_p"});
            Object world = (fWorld != null ? fWorld.get(player) : null);
            if (world == null) return null;

            Class<?> entityClass = findClass("net.minecraft.entity.Entity");
            Class<?> livingClass = findClass("net.minecraft.entity.EntityLivingBase");
            Class<?> vec3Class = findClass("net.minecraft.util.Vec3");
            Class<?> aabbClass = findClass("net.minecraft.util.AxisAlignedBB");
            if (entityClass == null || vec3Class == null || aabbClass == null) return null;

            Method mGetEyes = findMethod(player.getClass(), new String[]{"getPositionEyes", "func_174824_e"}, float.class);
            Method mGetLook = findMethod(player.getClass(), new String[]{"getLook", "func_70676_i"}, float.class);
            if (mGetEyes == null || mGetLook == null) return null;
            Object eyePos = mGetEyes.invoke(player, partialTicks);
            Object look = mGetLook.invoke(player, partialTicks);
            if (eyePos == null || look == null) return null;

            Method mAddVector = findMethod(vec3Class, new String[]{"addVector", "func_72441_c"}, double.class, double.class, double.class);
            Method mDistanceTo = findMethod(vec3Class, new String[]{"distanceTo", "func_72438_d"}, vec3Class);
            if (mAddVector == null) return null;

            double lx = 0.0, ly = 0.0, lz = 0.0;
            try {
                Field fx = findField(vec3Class, new String[]{"xCoord", "field_72450_a"});
                Field fy = findField(vec3Class, new String[]{"yCoord", "field_72448_b"});
                Field fz = findField(vec3Class, new String[]{"zCoord", "field_72449_c"});
                if (fx != null) lx = ((Number) fx.get(look)).doubleValue();
                if (fy != null) ly = ((Number) fy.get(look)).doubleValue();
                if (fz != null) lz = ((Number) fz.get(look)).doubleValue();
            } catch (Throwable ignored) {}

            Object end = mAddVector.invoke(eyePos, lx * maxDist, ly * maxDist, lz * maxDist);

            Field fLoaded = findField(world.getClass(), new String[]{"loadedEntityList", "field_72996_f"});
            Object list = (fLoaded != null ? fLoaded.get(world) : null);
            if (!(list instanceof java.util.List)) return null;
            java.util.List<?> entities = (java.util.List<?>) list;

            Method mGetBox = null;
            Method mExpand = null;
            Method mCalcIntercept = findMethod(aabbClass, new String[]{"calculateIntercept", "func_72327_a"}, vec3Class, vec3Class);
            Method mIsVecInside = findMethod(aabbClass, new String[]{"isVecInside", "func_72318_a"}, vec3Class);
            Method mGetCollisionBorder = findMethod(entityClass, new String[]{"getCollisionBorderSize", "func_70111_Y"});

            Object bestEntity = null;
            double bestDist = maxDist + 1.0;

            for (Object e : entities) {
                if (e == null) continue;
                if (e == player) continue;
                if (livingClass != null && !livingClass.isInstance(e)) continue;

                if (mGetBox == null || mGetBox.getDeclaringClass() != e.getClass()) {
                    mGetBox = findMethod(e.getClass(), new String[]{"getEntityBoundingBox", "func_174813_aQ"});
                }
                if (mGetBox == null) continue;
                Object box = mGetBox.invoke(e);
                if (box == null) continue;

                double border = 0.3D;
                try {
                    Object b = (mGetCollisionBorder != null ? mGetCollisionBorder.invoke(e) : null);
                    if (b instanceof Number) border = ((Number) b).doubleValue();
                } catch (Throwable ignored) {}

                if (mExpand == null) mExpand = findMethod(aabbClass, new String[]{"expand", "func_72314_b"}, double.class, double.class, double.class);
                Object expanded = (mExpand != null ? mExpand.invoke(box, border, border, border) : box);

                boolean inside = false;
                try {
                    if (mIsVecInside != null) {
                        Object r = mIsVecInside.invoke(expanded, eyePos);
                        inside = (r instanceof Boolean) ? (Boolean) r : false;
                    }
                } catch (Throwable ignored) {}
                if (inside) {
                    bestEntity = e;
                    bestDist = 0.0;
                    continue;
                }

                if (mCalcIntercept == null) continue;
                Object mop = mCalcIntercept.invoke(expanded, eyePos, end);
                if (mop == null) continue;
                Field fHitVec = findField(mop.getClass(), new String[]{"hitVec", "field_72307_f"});
                Object hitVec = (fHitVec != null ? fHitVec.get(mop) : null);
                if (hitVec == null) continue;

                double d = maxDist + 1.0;
                try {
                    if (mDistanceTo != null) {
                        Object dObj = mDistanceTo.invoke(eyePos, hitVec);
                        if (dObj instanceof Number) d = ((Number) dObj).doubleValue();
                    }
                } catch (Throwable ignored) {}

                if (d < bestDist && d <= maxDist) {
                    bestDist = d;
                    bestEntity = e;
                }
            }
            return bestEntity;
        } catch (Throwable ignored) {}
        return null;
    }

}