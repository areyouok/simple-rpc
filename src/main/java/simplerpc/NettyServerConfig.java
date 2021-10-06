package simplerpc;

/**
 * @author huangli
 */
public class NettyServerConfig {
    private int port = 12345;

    private byte[] handShakeBytes = Commands.HAND_SHAKE_BYTES;

    private boolean epoll;

    private int autoBatchMode;
    private int maxBufferSize = 32 * 1024;
    private int maxBatchCount = 100;
    private long batchTimeWindowsNanos = 1000 * 1000;

    private int maxIdleSeconds = 120;

    private int ioThreads = 4;
    private int bizThreads = 100;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getAutoBatchMode() {
        return autoBatchMode;
    }

    public void setAutoBatchMode(int autoBatchMode) {
        this.autoBatchMode = autoBatchMode;
    }

    public int getMaxBufferSize() {
        return maxBufferSize;
    }

    public void setMaxBufferSize(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    public int getMaxBatchCount() {
        return maxBatchCount;
    }

    public void setMaxBatchCount(int maxBatchCount) {
        this.maxBatchCount = maxBatchCount;
    }

    public long getBatchTimeWindowsNanos() {
        return batchTimeWindowsNanos;
    }

    public void setBatchTimeWindowsNanos(long batchTimeWindowsNanos) {
        this.batchTimeWindowsNanos = batchTimeWindowsNanos;
    }

    public int getMaxIdleSeconds() {
        return maxIdleSeconds;
    }

    public void setMaxIdleSeconds(int maxIdleSeconds) {
        this.maxIdleSeconds = maxIdleSeconds;
    }

    public int getIoThreads() {
        return ioThreads;
    }

    public void setIoThreads(int ioThreads) {
        this.ioThreads = ioThreads;
    }

    public int getBizThreads() {
        return bizThreads;
    }

    public void setBizThreads(int bizThreads) {
        this.bizThreads = bizThreads;
    }

    public byte[] getHandShakeBytes() {
        return handShakeBytes;
    }

    public void setHandShakeBytes(byte[] handShakeBytes) {
        this.handShakeBytes = handShakeBytes;
    }

    public boolean isEpoll() {
        return epoll;
    }

    public void setEpoll(boolean epoll) {
        this.epoll = epoll;
    }
}
