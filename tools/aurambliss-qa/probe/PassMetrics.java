package dev.auram.bliss.qa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL41C.*;

public final class PassMetrics {
    private static final int MAX_PENDING_FRAMES = 8;
    private static final Map<Integer, String> programNames = new HashMap<>();
    private static final ArrayDeque<Frame> pending = new ArrayDeque<>();
    private static final ArrayDeque<Integer> idleQueries = new ArrayDeque<>();
    private static final Map<String, List<Long>> timings = new TreeMap<>();
    private static boolean active;
    private static int issuedFrames;
    private static int skippedFrames;
    private static int currentProgram;
    private static int vertexBufferDraws;
    private static int shadowVertexBufferDraws;
    private static int cachedItemCalls;
    private static long totalVertexBufferDraws;
    private static long totalShadowVertexBufferDraws;
    private static long totalCachedItemCalls;
    private static boolean renderingShadows;
    private static Frame current;
    private static Span currentSpan;
    private static int currentQuery;

    private PassMetrics() {}

    public record Span(String name, Span parent) {}

    private record Sample(String name, int query) {}

    private record Frame(List<Sample> samples) {}

    public static void rememberProgram(int program, String name) {
        programNames.put(program, name.replaceAll("[^A-Za-z0-9_./:-]", "_"));
    }

    public static void useProgram(int program) {
        if (current != null) currentProgram = program;
    }

    public static void start() {
        if (active) return;
        issuedFrames = 0;
        skippedFrames = 0;
        totalVertexBufferDraws = 0;
        totalShadowVertexBufferDraws = 0;
        totalCachedItemCalls = 0;
        active = true;
    }

    public static boolean isActive() {
        return active;
    }

    public static void beginFrame() {
        if (!active) return;
        collectReadyFrames();
        if (issuedFrames == PerfMetrics.FRAME_COUNT) {
            if (pending.isEmpty()) report();
            return;
        }
        if (pending.size() == MAX_PENDING_FRAMES) {
            skippedFrames++;
            return;
        }
        currentProgram = 0;
        vertexBufferDraws = 0;
        shadowVertexBufferDraws = 0;
        cachedItemCalls = 0;
        current = new Frame(new ArrayList<>());
        currentSpan = new Span("world/other", null);
        startQuery();
    }

    public static void endFrame() {
        if (current == null) return;
        finishQuery();
        pending.addLast(current);
        current = null;
        currentSpan = null;
        totalVertexBufferDraws += vertexBufferDraws;
        totalShadowVertexBufferDraws += shadowVertexBufferDraws;
        totalCachedItemCalls += cachedItemCalls;
        issuedFrames++;
    }

    public static void renderingShadows(boolean rendering) {
        renderingShadows = rendering;
    }

    public static void vertexBufferDraw() {
        if (current == null) return;
        vertexBufferDraws++;
        if (renderingShadows) shadowVertexBufferDraws++;
    }

    public static void cachedItem() {
        if (current != null) cachedItemCalls++;
    }

    public static Span beginProgram(String kind) {
        if (current == null) return null;
        String name = programNames.getOrDefault(currentProgram, "program-" + currentProgram);
        return beginSpan(kind + "/" + name);
    }

    public static Span begin(String name) {
        return current == null ? null : beginSpan(name);
    }

    private static Span beginSpan(String name) {
        // Only one elapsed timer can be active. Attribute nested work to the innermost pass.
        finishQuery();
        currentSpan = new Span(name, currentSpan);
        startQuery();
        return currentSpan;
    }

    public static void end(Span span) {
        if (span == null) return;
        finishQuery();
        currentSpan = span.parent;
        startQuery();
    }

    private static void startQuery() {
        currentQuery = idleQueries.isEmpty() ? glGenQueries() : idleQueries.removeFirst();
        glBeginQuery(GL_TIME_ELAPSED, currentQuery);
    }

    private static void finishQuery() {
        glEndQuery(GL_TIME_ELAPSED);
        current.samples.add(new Sample(currentSpan.name, currentQuery));
    }

    private static void collectReadyFrames() {
        while (!pending.isEmpty()) {
            List<Sample> samples = pending.peek().samples;
            if (glGetQueryObjecti(samples.get(samples.size() - 1).query, GL_QUERY_RESULT_AVAILABLE) == 0) return;
            Frame frame = pending.removeFirst();
            Map<String, Long> frameTotals = new HashMap<>();
            long total = 0;
            for (Sample sample : frame.samples) {
                long elapsed = glGetQueryObjectui64(sample.query, GL_QUERY_RESULT);
                frameTotals.merge(sample.name, elapsed, Long::sum);
                total += elapsed;
                idleQueries.addLast(sample.query);
            }
            frameTotals.put("world", total);
            frameTotals.forEach((name, nanos) -> timings.computeIfAbsent(name, key -> new ArrayList<>()).add(nanos));
        }
    }

    private static void report() {
        String passes = timings.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + PerfMetrics.quantiles(entry.getValue()))
                .collect(Collectors.joining(","));
        System.out.printf(java.util.Locale.ROOT, "QA_EVT {\"event\":\"render_pass_timing\",\"status\":\"info\","
                        + "\"context\":{\"frames\":%d,\"skippedFrames\":%d,\"gpu\":{%s},"
                        + "\"vertexBufferDrawsPerFrame\":%.3f,\"shadowVertexBufferDrawsPerFrame\":%.3f,"
                        + "\"cachedItemCallsPerFrame\":%.3f,\"gl\":%d}}%n",
                issuedFrames, skippedFrames, passes, (double) totalVertexBufferDraws / issuedFrames,
                (double) totalShadowVertexBufferDraws / issuedFrames, (double) totalCachedItemCalls / issuedFrames, glGetError());
        while (!idleQueries.isEmpty()) glDeleteQueries(idleQueries.removeFirst());
        timings.clear();
        active = false;
    }
}
