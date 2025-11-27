package me.onils.fixtargetcalculate;

import me.onils.agent.commons.Utils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class Transformer implements ClassFileTransformer {

    private static volatile boolean loggedPatched = false;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!Utils.isMinecraft(className)) {
            return classfileBuffer;
        }

        if (!"net/minecraft/client/renderer/EntityRenderer".equals(className)) {
            return classfileBuffer;
        }

        ClassReader cr = new ClassReader(classfileBuffer);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean modified = false;
        String patchedMethodName = null;

        for (MethodNode mn : cn.methods) {
            if (!"(F)V".equals(mn.desc)) continue;
            if (!"getMouseOver".equals(mn.name) && !"func_78473_a".equals(mn.name)) continue;

            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.RETURN) {
                    InsnList call = new InsnList();
                    call.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    call.add(new VarInsnNode(Opcodes.FLOAD, 1));
                    call.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "me/onils/fixtargetcalculate/Hooks",
                            "afterGetMouseOver",
                            "(Ljava/lang/Object;F)V",
                            false
                    ));
                    mn.instructions.insertBefore(insn, call);
                }
            }

            modified = true;
            patchedMethodName = mn.name;
            break;
        }

        if (modified) {
            ClassWriter cw = new ClassWriter(cr, 0);
            cn.accept(cw);
            if (!loggedPatched) {
                loggedPatched = true;
                System.out.println("[FixTargetCalculate] Patched EntityRenderer#getMouseOver (name: " + patchedMethodName + ")");
            }
            return cw.toByteArray();
        }

        return classfileBuffer;
    }
}
