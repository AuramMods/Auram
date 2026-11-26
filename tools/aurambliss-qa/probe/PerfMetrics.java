package dev.auram.bliss.qa;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

import static org.lwjgl.opengl.GL41C.*;

public final class PerfMetrics {
    static final int FRAME_COUNT = Integer.getInteger("auram.bliss.qa.frames", 90);
    private static final java.lang.management.ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final ArrayDeque<Integer> pending = new ArrayDeque<>();
    private static final ArrayDeque<Integer> idle = new ArrayDeque<>();
    private static final List<Long> frameTimes = new ArrayList<>();
    private static final List<Long> cpuTimes = new ArrayList<>();
    private static final List<Long> volumeTimes = new ArrayList<>();
    private static boolean measuring;
    private static long previousFrame;
    private static long previousCpu;
    private static long heapHighWater;
    private static int volumeQuery;
    private static PassMetrics.Span profileVolume;
    private static CameraMotion motion;

    private PerfMetrics() {}

    public static void onFrame() throws Exception {
        long now = System.nanoTime();
        if (!measuring) {
            if (!Files.deleteIfExists(Path.of("bliss-read-perf"))) return;
            if (Files.deleteIfExists(Path.of("bliss-profile-passes"))) PassMetrics.start();
            measuring = true;
            previousFrame = now;
            previousCpu = THREADS.getCurrentThreadCpuTime();
            heapHighWater = 0;
            for (int i = 0; i < 8; i++) idle.add(glGenQueries());
            Path requested = Path.of("bliss-perf-motion");
            if (Files.isRegularFile(requested)) {
                motion = CameraMotion.start(Files.readString(requested));
                Files.delete(requested);
            }
            return;
        }
        while (!pending.isEmpty() && glGetQueryObjecti(pending.peek(), GL_QUERY_RESULT_AVAILABLE) != 0) {
            int query = pending.remove();
            volumeTimes.add(glGetQueryObjectui64(query, GL_QUERY_RESULT));
            idle.add(query);
        }
        frameTimes.add(now - previousFrame);
        previousFrame = now;
        long cpu = THREADS.getCurrentThreadCpuTime();
        if (cpu >= 0 && previousCpu >= 0) cpuTimes.add(cpu - previousCpu);
        previousCpu = cpu;
        Runtime heap = Runtime.getRuntime();
        heapHighWater = Math.max(heapHighWater, heap.totalMemory() - heap.freeMemory());
        if (frameTimes.size() >= FRAME_COUNT) {
            System.out.printf(java.util.Locale.ROOT,
                    "QA_EVT {\"event\":\"frame_timing\",\"status\":\"info\",\"context\":{\"frame\":%s,\"renderThreadCpu\":%s,\"lpvGpu\":%s,\"heapHighWaterBytes\":%d,\"movingCamera\":%b,\"gl\":%d}}%n",
                    quantiles(frameTimes), quantiles(cpuTimes), quantiles(volumeTimes), heapHighWater, motion != null, glGetError());
            measuring = false;
            motion = null;
            while (!idle.isEmpty()) glDeleteQueries(idle.remove());
            while (!pending.isEmpty()) glDeleteQueries(pending.remove());
            frameTimes.clear();
            cpuTimes.clear();
            volumeTimes.clear();
        } else if (motion != null) {
            motion.advance((float) frameTimes.size() / (FRAME_COUNT - 1));
        }
    }

    private record CameraMotion(double x, double y, double z, float yaw, float pitch, double dx, double dz, float turn) {
        private static CameraMotion start(String setting) {
            var player = Minecraft.m_91087_().f_91074_;
            String[] motion = setting.trim().split(",");
            return new CameraMotion(player.m_20185_(), player.m_20186_(), player.m_20189_(),
                    player.m_146908_(), player.m_146909_(), Double.parseDouble(motion[0]), Double.parseDouble(motion[1]),
                    Float.parseFloat(motion[2]));
        }

        private void advance(float progress) {
            Minecraft.m_91087_().f_91074_.m_7678_(x + dx * progress, y, z + dz * progress, yaw + turn * progress, pitch);
        }
    }

    public static void beginVolume() {
        if (PassMetrics.isActive()) {
            profileVolume = PassMetrics.begin("stage/lpv-propagation");
            return;
        }
        if (!measuring || idle.isEmpty()) return;
        volumeQuery = idle.remove();
        glBeginQuery(GL_TIME_ELAPSED, volumeQuery);
    }

    public static void endVolume() {
        if (profileVolume != null) {
            PassMetrics.end(profileVolume);
            profileVolume = null;
        }
        if (volumeQuery == 0) return;
        glEndQuery(GL_TIME_ELAPSED);
        pending.add(volumeQuery);
        volumeQuery = 0;
    }

    static String quantiles(List<Long> times) {
        if (times.isEmpty()) return "null";
        List<Long> sorted = times.stream().sorted().toList();
        return String.format(java.util.Locale.ROOT, "{\"samples\":%d,\"medianMs\":%.3f,\"p95Ms\":%.3f,\"p99Ms\":%.3f}",
                sorted.size(), percentile(sorted, 0.5), percentile(sorted, 0.95), percentile(sorted, 0.99));
    }

    private static double percentile(List<Long> sorted, double fraction) {
        return sorted.get((int) Math.ceil(sorted.size() * fraction) - 1) / 1_000_000.0;
    }
}
