package me.onils.damageindicator;

import me.onils.agent.commons.Utils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class Transformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!Utils.isMinecraft(className)) {
            return classfileBuffer;
        }

        // Target the in-game HUD class
        if (!"net/minecraft/client/gui/GuiIngame".equals(className)) {
            return classfileBuffer;
        }

        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean modified = false;

        for (MethodNode mn : cn.methods) {
            // renderGameOverlay(float) or func_175180_a(float)
            if (!"(F)V".equals(mn.desc)) continue;
            if (!"renderGameOverlay".equals(mn.name) && !"func_175180_a".equals(mn.name)) continue;

            // Insert before each return to ensure execution regardless of control flow
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.RETURN) {
                    InsnList call = new InsnList();
                    call.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this (GuiIngame)
                    call.add(new VarInsnNode(Opcodes.FLOAD, 1)); // partialTicks
                    call.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "me/onils/damageindicator/Hooks",
                            "onRenderGameOverlay",
                            "(Ljava/lang/Object;F)V",
                            false
                    ));
                    mn.instructions.insertBefore(insn, call);
                }
            }

            modified = true;
            break; // only patch once
        }

        if (modified) {
            ClassWriter cw = new ClassWriter(cr, 0);
            cn.accept(cw);
            return cw.toByteArray();
        }

        return classfileBuffer;
    }
}
