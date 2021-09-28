package simplerpc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import simplerpc.NettyTcpClient.Callback;

/**
 * @author huangli
 */
@SuppressWarnings("rawtypes")
class NettyTcpClientRequest {
    private final AtomicBoolean notified = new AtomicBoolean(false);
    private final short command;
    private final Callback callback;
    private final long timeout;
    private final long deadlineNano;
    private volatile int seqId;
    private final CompletableFuture future;
    private final NettyTcpClient client;
    private volatile NettyTcpClientRequest next;

    public NettyTcpClientRequest(short command, Callback callback, long timeout, long deadlineNano,
            CompletableFuture future, NettyTcpClient client) {
        this.command = command;
        this.callback = callback;
        this.timeout = timeout;
        this.deadlineNano = deadlineNano;
        this.future = future;
        this.client = client;
    }

    public AtomicBoolean getNotified() {
        return notified;
    }

    public Callback getCallback() {
        return callback;
    }

    public long getDeadlineNano() {
        return deadlineNano;
    }

    public long getTimeout() {
        return timeout;
    }

    public int getSeqId() {
        return seqId;
    }

    public void setSeqId(int seqId) {
        this.seqId = seqId;
    }

    public short getCommand() {
        return command;
    }

    public CompletableFuture getFuture() {
        return future;
    }

    public NettyTcpClient getClient() {
        return client;
    }

    public NettyTcpClientRequest getNext() {
        return next;
    }

    public void setNext(NettyTcpClientRequest next) {
        this.next = next;
    }
}
