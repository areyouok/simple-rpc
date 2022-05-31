package simplerpc;

/**
 * @author huangli
 */
public class NettyTcpClientConfig {
    private boolean epoll;
    private int maxPending = 1000;
    private int autoBatchConcurrencyThreshold = 200;
    private long closeSilenceMillis = 1000;
    private long closeTimeoutMillis = 5000;
    private byte[] handShakeBytes = Commands.HAND_SHAKE_BYTES;

    private int ioLoopThread = 1;
    private int workLoopThread = 1;

    private int maxBatchSize = 100;
    private long maxAutoBatchPendingNanos = 1_000_000; // 1毫秒

    private int maxFrameSize = 2 * 1024 * 1024;

    private int maxIdleSeconds = 120;

    public boolean isEpoll() {
        return epoll;
    }

    public void setEpoll(boolean epoll) {
        this.epoll = epoll;
    }

    public int getMaxPending() {
        return maxPending;
    }

    public void setMaxPending(int maxPending) {
        this.maxPending = maxPending;
    }

    public int getAutoBatchConcurrencyThreshold() {
        return autoBatchConcurrencyThreshold;
    }

    public void setAutoBatchConcurrencyThreshold(int autoBatchConcurrencyThreshold) {
        this.autoBatchConcurrencyThreshold = autoBatchConcurrencyThreshold;
    }

    public long getCloseTimeoutMillis() {
        return closeTimeoutMillis;
    }

    public void setCloseTimeoutMillis(long closeTimeoutMillis) {
        this.closeTimeoutMillis = closeTimeoutMillis;
    }

    public long getCloseSilenceMillis() {
        return closeSilenceMillis;
    }

    public void setCloseSilenceMillis(long closeSilenceMillis) {
        this.closeSilenceMillis = closeSilenceMillis;
    }

    public byte[] getHandShakeBytes() {
        return handShakeBytes;
    }

    public void setHandShakeBytes(byte[] handShakeBytes) {
        this.handShakeBytes = handShakeBytes;
    }

    public int getIoLoopThread() {
        return ioLoopThread;
    }

    public void setIoLoopThread(int ioLoopThread) {
        this.ioLoopThread = ioLoopThread;
    }

    public int getWorkLoopThread() {
        return workLoopThread;
    }

    public void setWorkLoopThread(int workLoopThread) {
        this.workLoopThread = workLoopThread;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public long getMaxAutoBatchPendingNanos() {
        return maxAutoBatchPendingNanos;
    }

    public void setMaxAutoBatchPendingNanos(long maxAutoBatchPendingNanos) {
        this.maxAutoBatchPendingNanos = maxAutoBatchPendingNanos;
    }

    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    public void setMaxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    public int getMaxIdleSeconds() {
        return maxIdleSeconds;
    }

    public void setMaxIdleSeconds(int maxIdleSeconds) {
        this.maxIdleSeconds = maxIdleSeconds;
    }

}
