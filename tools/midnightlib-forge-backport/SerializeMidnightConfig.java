import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.ASM9;

public final class SerializeMidnightConfig {
    private static final String CONFIG = "eu/midnightdust/lib/config/MidnightConfig";
    private static final String CLASS_ENTRY = CONFIG + ".class";
    private static final String INIT_DESCRIPTOR = "(Ljava/lang/String;Ljava/lang/Class;)V";
    private static final LocalDateTime ZIP_EPOCH = LocalDateTime.of(1980, 1, 1, 0, 0);

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: SerializeMidnightConfig <original.jar> <backport.jar>");
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]).toAbsolutePath();
        Path staged = Files.createTempFile(output.getParent(), "midnightlib-", ".jar");
        try {
            writeBackport(input, staged);
            Files.move(staged, output);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static void writeBackport(Path input, Path output) throws IOException {
        try (ZipFile original = new ZipFile(input.toFile());
                ZipOutputStream result = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry config = original.getEntry(CLASS_ENTRY);
            if (config == null) {
                throw new IOException("MidnightConfig is absent from " + input);
            }
            byte[] patched = serializeInit(read(original, config));
            for (var entries = original.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTimeLocal(ZIP_EPOCH);
                result.putNextEntry(copy);
                result.write(entry.getName().equals(CLASS_ENTRY) ? patched : read(original, entry));
                result.closeEntry();
            }
        }
    }

    private static byte[] read(ZipFile archive, ZipEntry entry) throws IOException {
        try (var input = archive.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static byte[] serializeInit(byte[] original) {
        ClassReader input = new ClassReader(original);
        ClassWriter output = new ClassWriter(input, 0);
        InitPatch patch = new InitPatch(output);
        input.accept(patch, 0);
        if (!input.getClassName().equals(CONFIG) || !patch.legacyEntries || !patch.applied) {
            throw new IllegalArgumentException("MidnightConfig does not match the Forge 1.20.1 layout");
        }
        return output.toByteArray();
    }

    private static final class InitPatch extends ClassVisitor {
        private boolean legacyEntries;
        private boolean applied;

        private InitPatch(ClassVisitor output) {
            super(ASM9, output);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (name.equals("entries") && descriptor.equals("Ljava/util/LinkedHashMap;")) {
                legacyEntries = true;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.equals("init") && descriptor.equals(INIT_DESCRIPTOR)
                    && (access & (ACC_PUBLIC | ACC_STATIC)) == (ACC_PUBLIC | ACC_STATIC)) {
                // Forge mod constructors can call init concurrently. Hold the class
                // monitor across registration and the later config-entry traversal.
                access |= ACC_SYNCHRONIZED;
                applied = true;
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }
}
