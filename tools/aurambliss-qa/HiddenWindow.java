import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

public final class HiddenWindow {
    private static final String GLFW = "org/lwjgl/glfw/GLFW";

    public static void premain(String options, Instrumentation instrumentation) {
        boolean mac = System.getProperty("os.name").startsWith("Mac");
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                if (!GLFW.equals(name)) return null;
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
                    @Override
                    public MethodVisitor visitMethod(int access, String method, String descriptor,
                            String signature, String[] exceptions) {
                        MethodVisitor original = super.visitMethod(access, method, descriptor, signature, exceptions);
                        if ((method.equals("glfwShowWindow") || method.equals("glfwFocusWindow"))
                                && descriptor.equals("(J)V")) {
                            original.visitCode();
                            original.visitInsn(Opcodes.RETURN);
                            original.visitMaxs(0, 0);
                            original.visitEnd();
                            return null;
                        }
                        return new MethodVisitor(Opcodes.ASM8, original) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                if (method.equals("nglfwCreateWindow")) {
                                    hint("glfwWindowHint", 0x20004); // GLFW_VISIBLE
                                    hint("glfwWindowHint", 0x20001); // GLFW_FOCUSED
                                    hint("glfwWindowHint", 0x2000c); // GLFW_FOCUS_ON_SHOW
                                }
                                if (mac && method.equals("glfwInit") && descriptor.equals("()Z")) {
                                    hint("glfwInitHint", 0x51002); // GLFW_COCOA_MENUBAR
                                }
                            }

                            private void hint(String method, int property) {
                                super.visitLdcInsn(property);
                                super.visitInsn(Opcodes.ICONST_0);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, GLFW, method, "(II)V", false);
                            }

                        };
                    }
                }, 0);
                System.out.println("AuramBliss QA disabled GLFW window visibility and focus");
                return writer.toByteArray();
            }
        });
    }
}
