package me.onils.nojumpdelay;

import me.onils.agent.commons.Utils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Transformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        // We only care about Minecraft classes
        if (!Utils.isMinecraft(className)) {
            return classfileBuffer;
        }

        // Only target EntityLivingBase
        if (!"net/minecraft/entity/EntityLivingBase".equals(className)) {
            return classfileBuffer;
        }

        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        // Find the correct field name for jumpTicks (deobf: jumpTicks, srg: field_70773_bE)
        String jumpField = null;
        for (FieldNode fn : cn.fields) {
            if ("I".equals(fn.desc) && ("jumpTicks".equals(fn.name) || "field_70773_bE".equals(fn.name))) {
                jumpField = fn.name;
                break;
            }
        }

        if (jumpField == null) {
            // Could not find known name; try a heuristic: look for an int field whose name contains "jump"
            for (FieldNode fn : cn.fields) {
                if ("I".equals(fn.desc) && fn.name.toLowerCase().contains("jump")) {
                    jumpField = fn.name;
                    break;
                }
            }
        }

        boolean modified = false;
        if (jumpField != null) {
            for (MethodNode mn : cn.methods) {
                // onLivingUpdate (deobf) or func_70636_d (srg), both with ()V
                if ("()V".equals(mn.desc) && ("onLivingUpdate".equals(mn.name) || "func_70636_d".equals(mn.name))) {
                    InsnList patch = new InsnList();
                    patch.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    patch.add(new InsnNode(Opcodes.ICONST_0));
                    patch.add(new FieldInsnNode(Opcodes.PUTFIELD, cn.name, jumpField, "I"));
                    mn.instructions.insert(patch);
                    modified = true;
                    break;
                }
            }
        }

        if (modified) {
            ClassWriter cw = new ClassWriter(cr, 0);
            cn.accept(cw);
            return cw.toByteArray();
        }

        return classfileBuffer;
    }
}
