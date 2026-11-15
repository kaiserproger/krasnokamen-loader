package dev.krasnokamen.agent.javaagent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Hooks the plain Java client's extraction and rendering of the same frame. */
final class VanillaFrameTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(Module module, ClassLoader loader, String name, Class<?> redefined,
                            ProtectionDomain domain, byte[] bytes) {
        if (!"net/minecraft/client/Minecraft".equals(name)) {
            return null;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        boolean[] found = {false, false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String method, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                if (method.equals("renderFrame") && descriptor.equals("(Z)V")) {
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String called,
                                                    String desc, boolean isInterface) {
                            super.visitMethodInsn(opcode, owner, called, desc, isInterface);
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                    && owner.equals("net/minecraft/client/renderer/GameRenderer")) {
                                String hook = null;
                                if (called.equals("extract")
                                        && desc.equals("(Lnet/minecraft/client/DeltaTracker;Z)V")) {
                                    found[0] = true;
                                    hook = "afterExtract";
                                } else if (called.equals("render") && desc.equals("()V")) {
                                    found[1] = true;
                                    hook = "afterRender";
                                }
                                if (hook != null) {
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "dev/krasnokamen/agent/javaagent/VanillaFrameCapture", hook,
                                        "(Lnet/minecraft/client/Minecraft;)V", false);
                                }
                            }
                        }
                    };
                }
                return delegate;
            }
        }, 0);
        if (!found[0] || !found[1]) {
            throw new IllegalStateException("plain client is missing a frame capture hook");
        }
        return writer.toByteArray();
    }
}
