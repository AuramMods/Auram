package dev.auram.bliss.qa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.opengl.GL43C.*;

public final class RfPreparedReadback {
    private static final Path REQUEST = Path.of("bliss-read-rf-prepared");
    private static final Set<Integer> consumerPrograms = new HashSet<>();
    private static boolean requested;
    private static int pulses;

    public static void begin() throws Exception {
        requested = Files.deleteIfExists(REQUEST);
        if (!requested) return;
        consumerPrograms.clear();
        pulses = 0;
    }

    public static void prepared() {
        if (!requested) return;
        int cache = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, 15);
        if (cache == 0) return;
        int previous = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
        try {
            int[] count = new int[1];
            glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, cache);
            glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, count);
            pulses = count[0];
        } finally {
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, previous);
        }
    }

    public static void usedProgram(int program) {
        if (!requested || pulses == 0) return;
        int location = glGetUniformLocation(program, "auramRfPrepared");
        if (location >= 0 && glGetUniformi(program, location) == GL_TRUE) consumerPrograms.add(program);
    }

    public static void finish() {
        if (!requested) return;
        requested = false;
        System.out.printf("QA_EVT {\"event\":\"rf_prepared_readback\",\"status\":\"info\",\"context\":{\"pulses\":%d,\"consumers\":%d,\"gl\":%d}}%n",
                pulses, consumerPrograms.size(), glGetError());
    }
}
