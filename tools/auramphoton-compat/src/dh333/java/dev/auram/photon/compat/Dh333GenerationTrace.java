package dev.auram.photon.compat;

import net.irisshaders.iris.Iris;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Mod.EventBusSubscriber(modid = "auramphoton_compat", value = Dist.CLIENT)
public final class Dh333GenerationTrace {
    public static final boolean ENABLED = Boolean.getBoolean("auram.dh.trace");
    private static final long REPORT_INTERVAL = TimeUnit.SECONDS.toNanos(10);
    private static final ConcurrentHashMap<ServerLevel, Dh333GenerationTrace> LEVELS = new ConcurrentHashMap<>();
    private static final LongAdder CAMERA_DEFERRALS = new LongAdder();
    private static final LongAdder LOD_DEFERRALS = new LongAdder();
    private static final LongAdder HEALTH_DEFERRALS = new LongAdder();

    private final ServerLevel level;
    private final ConcurrentHashMap<Long, Request> active = new ConcurrentHashMap<>();
    private final LongAdder queuedBatches = new LongAdder();
    private final LongAdder queuedNanos = new LongAdder();
    private final LongAdder fresh = new LongAdder();
    private final LongAdder existing = new LongAdder();
    private final LongAdder fullNanos = new LongAdder();
    private final LongAdder lodChunks = new LongAdder();
    private final LongAdder lodNanos = new LongAdder();
    private final LongAdder lodBatches = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder capacityDeferrals = new LongAdder();
    private final LongAdder playerDeferrals = new LongAdder();
    private volatile String queueGate = "unobserved";
    private long lastReport;

    private Dh333GenerationTrace(ServerLevel level) {
        this.level = level;
    }

    public static Dh333GenerationTrace open(ServerLevel level) {
        return ENABLED ? LEVELS.computeIfAbsent(level, Dh333GenerationTrace::new) : null;
    }

    public static Dh333GenerationTrace get(ServerLevel level) {
        return ENABLED ? LEVELS.get(level) : null;
    }

    public static void cameraDeferred() {
        if (ENABLED) CAMERA_DEFERRALS.increment();
    }

    public static void lodDeferred() {
        if (ENABLED) LOD_DEFERRALS.increment();
    }

    public static void healthDeferred() {
        if (ENABLED) HEALTH_DEFERRALS.increment();
    }

    public void queueGate(String reason) {
        queueGate = reason;
        if (reason.equals("overworld_capacity")) capacityDeferrals.increment();
        if (reason.equals("player_chunks")) playerDeferrals.increment();
    }

    public void close() {
        LEVELS.remove(level, this);
    }

    public Request begin(long pos) {
        Request request = new Request();
        return active.putIfAbsent(pos, request) == null ? request : null;
    }

    public void end(long pos, Request request, boolean success) {
        if (request == null || !active.remove(pos, request)) return;
        if (success) {
            (request.fresh ? fresh : existing).increment();
            fullNanos.add(System.nanoTime() - request.start);
        } else {
            failures.increment();
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!ENABLED || !event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) return;
        Dh333GenerationTrace trace = get(level);
        if (trace == null) return;
        Request request = trace.active.get(event.getChunk().m_7697_().m_45588_());
        if (request != null) request.fresh = true;
    }

    public void batchStart(long createdAt, long submittedAt) {
        queuedBatches.increment();
        queuedNanos.add(submittedAt - createdAt);
    }

    public void batchComplete(long submittedAt, int chunks) {
        lodBatches.increment();
        lodChunks.add(chunks);
        lodNanos.add(System.nanoTime() - submittedAt);
    }

    public void report(int waiting, int inFlight) {
        long now = System.nanoTime();
        if (waiting + inFlight == 0 || now - lastReport < REPORT_INTERVAL) return;
        lastReport = now;
        long freshCount = fresh.sum();
        long existingCount = existing.sum();
        Iris.logger.info("Auram DH {} waiting={} inFlight={} queue={} fullFresh={} fullExisting={} fullPending={} lodBatches={} lodChunks={} failedFull={} "
                        + "capDeferrals={} foregroundDeferrals={} poolCameraDeferrals={} poolLodDeferrals={} poolHealthDeferrals={} "
                        + "meanQueueMs={} meanFullMs={} meanSubmissionToLodMs={}",
                level.m_46472_().m_135782_(), waiting, inFlight, queueGate, freshCount, existingCount, active.size(),
                lodBatches.sum(), lodChunks.sum(), failures.sum(), capacityDeferrals.sum(), playerDeferrals.sum(), CAMERA_DEFERRALS.sum(),
                LOD_DEFERRALS.sum(), HEALTH_DEFERRALS.sum(), meanMillis(queuedNanos, queuedBatches.sum()),
                meanMillis(fullNanos, freshCount + existingCount), meanMillis(lodNanos, lodBatches.sum()));
    }

    private static double meanMillis(LongAdder nanos, long samples) {
        return samples == 0 ? 0 : nanos.sum() / (samples * 1_000_000.0);
    }

    public interface QueuedBatch {
        long auram$queuedAt();
    }

    public static final class Request {
        private final long start = System.nanoTime();
        private volatile boolean fresh;
    }
}
