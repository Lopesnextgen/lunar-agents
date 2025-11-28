package davi.lopes.fixtargetcalculate;

import davi.lopes.agent.commons.Utils;
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
    private static final String HOOKS = "davi/lopes/fixtargetcalculate/Hooks";

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
                        " getAABBExcl=" + cv.getAabbExclPatched +
                        " fieldReadsRewritten=" + cv.loadedFieldRewrites +
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
        boolean getAabbExclPatched;
        boolean updatePatched;
        int loadedFieldRewrites;

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
            MethodVisitor base = super.visitMethod(access, name, desc, signature, exceptions);

            if ("(Ljava/lang/Class;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;".equals(desc)) {
                return new MethodVisitor(api, base) {
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
                    @Override public void visitFieldInsn(int opcode, String ownerF, String nameF, String descF) {
                        if (opcode == Opcodes.GETFIELD && owner.equals(ownerF) && "Ljava/util/List;".equals(descF)
                                && ("loadedEntityList".equals(nameF) || "field_72996_f".equals(nameF))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getLoadedEntityListHook",
                                    "(Ljava/lang/Object;)Ljava/util/List;", false);
                            loadedFieldRewrites++; modified = true;
                        } else {
                            super.visitFieldInsn(opcode, ownerF, nameF, descF);
                        }
                    }
                    @Override public void visitMaxs(int maxStack, int maxLocals) { super.visitMaxs(0, 0); }
                };
            }

            if ("(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;Lcom/google/common/base/Predicate;)Ljava/util/List;".equals(desc)) {
                return new MethodVisitor(api, base) {
                    private boolean emitted;
                    @Override public void visitCode() {
                        if (emitted) return; emitted = true; super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitVarInsn(Opcodes.ALOAD, 3);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getEntitiesInAABBExcludingHook",
                                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;", false);
                        super.visitInsn(Opcodes.ARETURN);
                        getAabbExclPatched = true; modified = true;
                    }
                    @Override public void visitFieldInsn(int opcode, String ownerF, String nameF, String descF) {
                        if (opcode == Opcodes.GETFIELD && owner.equals(ownerF) && "Ljava/util/List;".equals(descF)
                                && ("loadedEntityList".equals(nameF) || "field_72996_f".equals(nameF))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getLoadedEntityListHook",
                                    "(Ljava/lang/Object;)Ljava/util/List;", false);
                            loadedFieldRewrites++; modified = true;
                        } else {
                            super.visitFieldInsn(opcode, ownerF, nameF, descF);
                        }
                    }
                    @Override public void visitMaxs(int maxStack, int maxLocals) { super.visitMaxs(0, 0); }
                };
            }

            if ("()V".equals(desc) && ("updateEntities".equals(name) || "func_72939_s".equals(name))) {
                return new MethodVisitor(api, base) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "onWorldUpdate", "(Ljava/lang/Object;)V", false);
                            updatePatched = true; modified = true;
                        }
                        super.visitInsn(opcode);
                    }
                    @Override public void visitFieldInsn(int opcode, String ownerF, String nameF, String descF) {
                        if (opcode == Opcodes.GETFIELD && owner.equals(ownerF) && "Ljava/util/List;".equals(descF)
                                && ("loadedEntityList".equals(nameF) || "field_72996_f".equals(nameF))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getLoadedEntityListHook",
                                    "(Ljava/lang/Object;)Ljava/util/List;", false);
                            loadedFieldRewrites++; modified = true;
                        } else {
                            super.visitFieldInsn(opcode, ownerF, nameF, descF);
                        }
                    }
                };
            }

            if ("()Ljava/util/List;".equals(desc) && "getLoadedEntityList".equals(name)) {
                hasGetLoadedMethod = true;
                return new MethodVisitor(api, base) {
                    private boolean emitted;
                    @Override public void visitCode() {
                        if (emitted) return; emitted = true; super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getLoadedEntityListHook",
                                "(Ljava/lang/Object;)Ljava/util/List;", false);
                        super.visitInsn(Opcodes.ARETURN);
                        getLoadedPatched = true; modified = true;
                    }
                    @Override public void visitFieldInsn(int opcode, String ownerF, String nameF, String descF) {
                        if (opcode == Opcodes.GETFIELD && owner.equals(ownerF) && "Ljava/util/List;".equals(descF)
                                && ("loadedEntityList".equals(nameF) || "field_72996_f".equals(nameF))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getLoadedEntityListHook",
                                    "(Ljava/lang/Object;)Ljava/util/List;", false);
                            loadedFieldRewrites++; modified = true;
                        } else {
                            super.visitFieldInsn(opcode, ownerF, nameF, descF);
                        }
                    }
                    @Override public void visitMaxs(int maxStack, int maxLocals) { super.visitMaxs(0, 0); }
                };
            }

            return new MethodVisitor(api, base) {
                @Override public void visitFieldInsn(int opcode, String ownerF, String nameF, String descF) {
                    if (opcode == Opcodes.GETFIELD && owner.equals(ownerF) && "Ljava/util/List;".equals(descF)
                            && ("loadedEntityList".equals(nameF) || "field_72996_f".equals(nameF))) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getLoadedEntityListHook",
                                "(Ljava/lang/Object;)Ljava/util/List;", false);
                        loadedFieldRewrites++; modified = true;
                    } else {
                        super.visitFieldInsn(opcode, ownerF, nameF, descF);
                    }
                }
            };
        }

        private void rewriteLoadedListRead(MethodVisitor mv, int opcode, String ownerF, String nameF, String descF) {
            if (opcode == Opcodes.GETFIELD && owner.equals(ownerF) && "Ljava/util/List;".equals(descF)
                    && ("loadedEntityList".equals(nameF) || "field_72996_f".equals(nameF))) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "getLoadedEntityListHook",
                        "(Ljava/lang/Object;)Ljava/util/List;", false);
                loadedFieldRewrites++; modified = true;
                return;
            }
            mv.visitFieldInsn(opcode, ownerF, nameF, descF);
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
