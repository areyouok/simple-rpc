package simplerpc;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author huangli
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class AutoBatchMode {

    public static final int MODE_DISABLE = 0;
    public static final int MODE_ENABLE = 1;
    public static final int MODE_AUTO = 2;

    private static final float MAX_FACTOR = 1.5f;
    private static final float UPDATE_MIN = -0.5f;
    private static final float UPDATE_MAX = 0.2f;

    // 1秒
    private static final long RECOVER_INTERVAL = 1000 * 1000 * 1000;
    private static final float RECOVER_FACTOR = 0.1f;

    private final int mode;
    private final int maxBatchCount;
    private final int maxBufferSize;
    private final long timeWindowsNanos;

    private float factor = 1.0f;

    private boolean batchStarted;
    private boolean fullBatch;
    private int pendingCount;
    private int pendingSize;

    private long lastFlushTime = 0;
    private long lastDownTime = 0;

    public AutoBatchMode(int mode, int maxBatchCount, int maxBufferSize, long timeWindowsNanos) {
        this.mode = mode;
        this.maxBatchCount = maxBatchCount;
        this.maxBufferSize = maxBufferSize;
        this.timeWindowsNanos = timeWindowsNanos;
    }

    public boolean isBatchStarted() {
        return batchStarted;
    }

    public int getPendingSize() {
        return pendingSize;
    }

    public boolean startBatchIfNecessary() {
        if (batchStarted) {
            return false;
        }
        batchStarted = startBatchIfNecessary0();
        return batchStarted;
    }

    private boolean startBatchIfNecessary0() {
        if (mode == MODE_DISABLE) {
            return false;
        }
        if (mode == MODE_ENABLE) {
            return true;
        }

        long time = System.nanoTime();
        if (time - lastFlushTime > timeWindowsNanos) {
            // 第一次总是直接flush，后续是否flush，先从当前时间往前看一个时间窗口，看这段时候是否flush过。
            // 如果过去一段时间没有flush过，说明tps不大，batch很可能不会命中，命中了收益也很低，反而会拉长RT
            return false;
        }
        if (factor >= 1.0f) {
            return true;
        }
        if (factor == 0.0f && (time - lastDownTime) > RECOVER_INTERVAL) {
            // 每隔一段时间给一次复活的机会
            factor = RECOVER_FACTOR;
        }
        return ThreadLocalRandom.current().nextFloat() < factor;
    }

    public void addCount() {
        pendingCount++;
    }

    public void addSize(int size) {
        pendingSize += size;
    }

    public boolean shouldFlush() {
        boolean b = pendingCount >= maxBatchCount || pendingSize >= maxBufferSize;
        if (b) {
            fullBatch = true;
        }
        return b;
    }

    public void finish(boolean finishWindow) {
        long time = System.nanoTime();
        if (finishWindow && mode == MODE_AUTO) {
            if (fullBatch) {
                factor += UPDATE_MAX;
            } else {
                float batchRate = Math.max((float) pendingCount / maxBatchCount, (float) pendingSize / maxBufferSize);
                float delta = (UPDATE_MAX - UPDATE_MIN) * batchRate + UPDATE_MIN;
                factor = factor + delta;
                factor = Math.min(MAX_FACTOR, factor);
                if (factor <= 0) {
                    factor = 0.0f;
                    lastDownTime = time;
                }
            }
        }

        lastFlushTime = time;
        pendingCount = 0;
        pendingSize = 0;

        if (finishWindow) {
            fullBatch = false;
            batchStarted = false;
        }
    }
}
