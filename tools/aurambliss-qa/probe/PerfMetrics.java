package dev.auram.bliss.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL41C.*;

public final class PerfMetrics {
    private static final int FRAME_COUNT = 90;
    private static final ArrayDeque<Integer> pending = new ArrayDeque<>();
    private static final ArrayDeque<Integer> idle = new ArrayDeque<>();
    private static final List<Long> frameTimes = new ArrayList<>();
    private static final List<Long> volumeTimes = new ArrayList<>();
    private static boolean measuring;
    private static long previousFrame;
    private static long heapHighWater;
    private static int volumeQuery;

    private PerfMetrics() {}

    public static void onFrame() throws Exception {
        long now = System.nanoTime();
        if (!measuring) {
            if (!Files.deleteIfExists(Path.of("bliss-read-perf"))) return;
            measuring = true;
            previousFrame = now;
            heapHighWater = 0;
            for (int i = 0; i < 8; i++) idle.add(glGenQueries());
            return;
        }
        while (!pending.isEmpty() && glGetQueryObjecti(pending.peek(), GL_QUERY_RESULT_AVAILABLE) != 0) {
            int query = pending.remove();
            volumeTimes.add(glGetQueryObjectui64(query, GL_QUERY_RESULT));
            idle.add(query);
        }
        frameTimes.add(now - previousFrame);
        previousFrame = now;
        Runtime heap = Runtime.getRuntime();
        heapHighWater = Math.max(heapHighWater, heap.totalMemory() - heap.freeMemory());
        if (frameTimes.size() >= FRAME_COUNT) {
            System.out.printf(java.util.Locale.ROOT,
                    "QA_EVT {\"event\":\"frame_timing\",\"status\":\"info\",\"context\":{\"frame\":%s,\"lpvGpu\":%s,\"heapHighWaterBytes\":%d,\"gl\":%d}}%n",
                    quantiles(frameTimes), quantiles(volumeTimes), heapHighWater, glGetError());
            measuring = false;
            while (!idle.isEmpty()) glDeleteQueries(idle.remove());
            while (!pending.isEmpty()) glDeleteQueries(pending.remove());
            frameTimes.clear();
            volumeTimes.clear();
        }
    }

    public static void beginVolume() {
        if (!measuring || idle.isEmpty()) return;
        volumeQuery = idle.remove();
        glBeginQuery(GL_TIME_ELAPSED, volumeQuery);
    }

    public static void endVolume() {
        if (volumeQuery == 0) return;
        glEndQuery(GL_TIME_ELAPSED);
        pending.add(volumeQuery);
        volumeQuery = 0;
    }

    private static String quantiles(List<Long> times) {
        if (times.isEmpty()) return "null";
        List<Long> sorted = times.stream().sorted().toList();
        return String.format(java.util.Locale.ROOT, "{\"samples\":%d,\"medianMs\":%.3f,\"p95Ms\":%.3f,\"p99Ms\":%.3f}",
                sorted.size(), percentile(sorted, 0.5), percentile(sorted, 0.95), percentile(sorted, 0.99));
    }

    private static double percentile(List<Long> sorted, double fraction) {
        return sorted.get((int) Math.ceil(sorted.size() * fraction) - 1) / 1_000_000.0;
    }
}
