package me.onils.fixtargetcalculate;

import me.onils.agent.commons.Utils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public final class WorldTransformer implements ClassFileTransformer {

    private static final String TARGET = "net/minecraft/world/World";
    private static final String HOOKS = "me/onils/fixtargetcalculate/Hooks";

    private static volatile boolean loggedPatched;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                             ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!Utils.isMinecraft(className)) return classfileBuffer;
        if (!TARGET.equals(className)) return classfileBuffer;

        ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        PatchWorld cv = new PatchWorld(Opcodes.ASM9, cw);
        cr.accept(cv, 0);

        if (cv.modified) {
            if (!loggedPatched) {
                loggedPatched = true;
                System.out.println("[FixTargetCalculate] Patched World: fields=" + cv.fieldsAdded +
                        " ctors=" + cv.ctorsPatched +
                        " getLoaded=" + cv.getLoadedPatched +
                        " getAABB=" + cv.getAabbPatched +
                        " update=" + cv.updatePatched);
            }
            return cw.toByteArray();
        }
        return classfileBuffer;
    }

    private static final class PatchWorld extends ClassVisitor {
        boolean modified;
        boolean fieldsAdded;
        boolean ctorsPatched;
        boolean getLoadedPatched;
        boolean getAabbPatched;
        boolean updatePatched;

        String owner;
        boolean hasGetLoadedMethod;

        PatchWorld(int api, ClassVisitor cv) { super(api, cv); }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.owner = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if ("<init>".equals(name)) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodVisitor(api, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                            super.visitInsn(Opcodes.DUP);
                            super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
                            super.visitFieldInsn(Opcodes.PUTFIELD, owner, "agent_cachedEntityList", "Ljava/util/List;");
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitInsn(Opcodes.LCONST_0);
                            super.visitFieldInsn(Opcodes.PUTFIELD, owner, "agent_lastEntityListUpdate", "J");
                            ctorsPatched = true; modified = true;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }

            if ("(Ljava/lang/Class;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;".equals(desc)
                    && ("getEntitiesWithinAABB".equals(name) || "func_175674_a".equals(name))) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodVisitor(api, mv) {
                    private boolean emitted;
                    @Override public void visitCode() {
                        if (emitted) return; emitted = true; super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getEntitiesAABBHook",
                                "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;)Ljava/util/List;", false);
                        super.visitInsn(Opcodes.ARETURN);
                        getAabbPatched = true; modified = true;
                    }
                    @Override public void visitMaxs(int maxStack, int maxLocals) { super.visitMaxs(0, 0); }
                };
            }

            if ("()V".equals(desc) && ("updateEntities".equals(name) || "func_72939_s".equals(name))) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodVisitor(api, mv) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onWorldUpdate", "(Ljava/lang/Object;)V", false);
                            updatePatched = true; modified = true;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }

            if ("()Ljava/util/List;".equals(desc) && "getLoadedEntityList".equals(name)) {
                hasGetLoadedMethod = true;
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                return new MethodVisitor(api, mv) {
                    private boolean emitted;
                    @Override public void visitCode() {
                        if (emitted) return; emitted = true; super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getLoadedEntityListHook",
                                "(Ljava/lang/Object;)Ljava/util/List;", false);
                        super.visitInsn(Opcodes.ARETURN);
                        getLoadedPatched = true; modified = true;
                    }
                    @Override public void visitMaxs(int maxStack, int maxLocals) { super.visitMaxs(0, 0); }
                };
            }

            return super.visitMethod(access, name, desc, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            FieldVisitor fv1 = super.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                    "agent_cachedEntityList", "Ljava/util/List;", null, null);
            if (fv1 != null) fv1.visitEnd();
            FieldVisitor fv2 = super.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                    "agent_lastEntityListUpdate", "J", null, null);
            if (fv2 != null) fv2.visitEnd();
            fieldsAdded = true; modified = true;

            if (!hasGetLoadedMethod) {
                MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "getLoadedEntityList",
                        "()Ljava/util/List;", null, null);
                if (mv != null) {
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getLoadedEntityListHook",
                            "(Ljava/lang/Object;)Ljava/util/List;", false);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    getLoadedPatched = true; modified = true;
                }
            }
            super.visitEnd();
        }
    }
}
