package simplerpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author huangli
 */
public class NettyTcpClient implements AutoCloseable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NettyTcpClient.class.getName());

    private final Supplier<List<String>> servers;
    private final NettyTcpClientConfig config;
    private final Semaphore semaphore;
    private int requestId;

    private int serverIndex;

    private volatile DefaultEventExecutorGroup workerLoop;
    private volatile EventLoopGroup ioLoop;
    private volatile Bootstrap bootstrap;

    private volatile int status = STATUS_INIT;
    private static final int STATUS_INIT = 0;
    private static final int STATUS_STARTED = 1;
    private static final int STATUS_STOPPING = 2;
    private static final int STATUS_STOPPED = 4;


    private volatile ChannelFuture channelFuture;
    private final ConcurrentHashMap<Integer, NettyTcpClientRequest> waitForResponseMap = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<NettyTcpClientRequest> waitForWriteQueue = new LinkedBlockingQueue<>();

    private final Thread thread;
    private long lastCleanTimeNano = System.nanoTime();

    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

    public interface Callback<T> {

        void encode(ByteBuf out);

        T decode(ByteBuf in);

    }

    public NettyTcpClient(Supplier<List<String>> servers, NettyTcpClientConfig config) {
        this.servers = servers;
        this.config = config;
        this.semaphore = new Semaphore(config.getMaxPending());
        this.thread = new Thread(this::run);
    }

    private Channel connect() throws InterruptedException, IOException {
        List<String> serversCopy = new ArrayList<>(servers.get());
        String[] serverAndPort = serversCopy.get(serverIndex).split(":");
        String server = serverAndPort[0];
        int port = Integer.parseInt(serverAndPort[1]);
        serverIndex++;
        if (serverIndex > serversCopy.size()) {
            serverIndex = 0;
        }
        ChannelFuture f = this.bootstrap.connect(server, port);
        f.sync();
        if (!f.isSuccess()) {
            throw new IOException("[client] connect to " + server + ":" + port + " fail: " + f.cause());
        }
        ByteBuf buf = f.channel().alloc().buffer();
        buf.writeBytes(config.getHandShakeBytes());
        ChannelFuture handshakeFuture = f.channel()
                .writeAndFlush(buf)
                .sync();
        if (!handshakeFuture.isSuccess()) {
            throw new IOException("[client] handshake with " + server + ":" + port + " fail: " + f.cause());
        }
        Channel channel = f.channel();
        this.channelFuture = f;
        return channel;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public void start() throws Exception {
        this.thread.start();
        this.bootstrap = new Bootstrap();
        if (config.isEpoll()) {
            this.ioLoop = new EpollEventLoopGroup(config.getIoLoopThread(), new IndexThreadFactory("NettyClientIO"));
        } else {
            this.ioLoop = new NioEventLoopGroup(config.getIoLoopThread(), new IndexThreadFactory("NettyClientIO"));
        }
        if (config.getWorkLoopThread() > 0) {
            this.workerLoop = new DefaultEventExecutorGroup(config.getWorkLoopThread(), new IndexThreadFactory("NettyClientWorker"));
        }


        this.bootstrap.group(this.ioLoop).channel(config.isEpoll() ? EpollSocketChannel.class : NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .option(ChannelOption.SO_SNDBUF, 65535)
                .option(ChannelOption.SO_RCVBUF, 65535)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        EventExecutorGroup group = workerLoop == null ? ioLoop : workerLoop;
                        if (config.getMaxIdleSeconds() > 0) {
                            pipeline.addLast(group, new IdleStateHandler(config.getMaxIdleSeconds(), 0, 0));
                        }
                        pipeline.addLast(group, new HandShakeHandler(config.getHandShakeBytes(), false));
                        pipeline.addLast(group, new LengthFieldBasedFrameDecoder(config.getMaxFrameSize(), 0, 4, 0, 4));
                        pipeline.addLast(group, new NettyTcpClientDecoder());
                        pipeline.addLast(group, new NettyTcpClientEncoder());
                        pipeline.addLast(group, new NettyConnectManageHandler(false, channel -> closeChannel(channel)));
                    }
                });
        try {
            connect();
            status = STATUS_STARTED;
        } catch (Exception e) {
            close();
            throw e;
        }
    }

    private <T> CompletableFuture<T> errorFuture(Throwable e) {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(e);
        return f;
    }

    public <T> CompletableFuture<T> sendRequest(short command, Callback<T> callback, long timeoutMillis) {
        boolean needRelease = false;
        try {
            if (status >= STATUS_STOPPING) {
                return errorFuture(new IOException("closed"));
            }
            if (status == STATUS_INIT) {
                return errorFuture(new IOException("not start"));
            }
            long deadLine = System.nanoTime() + (timeoutMillis << 20);
            // 如果pending请求太多semaphore没有permit了，这个时候就会堵塞直到超时
            // 但由于发送方被堵塞，server处理很快的情况下permit会迅速释放，不会导致单次请求超时，这样实现了异步背压
            boolean acquire = this.semaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
            if (acquire && (deadLine - System.nanoTime() > 1_000_000)) {
                CompletableFuture<T> future = new CompletableFuture<>();
                NettyTcpClientRequest request = new NettyTcpClientRequest(command, callback,
                        timeoutMillis, deadLine, future, this);
                // 这个队列是无界的，肯定能直接放进去
                waitForWriteQueue.add(request);
                return future;
            } else {
                needRelease = acquire;
                return errorFuture(new IOException("too many pending requests"));
            }
        } catch (InterruptedException e) {
            return errorFuture(new IOException("InterruptedException"));
        } catch (Throwable e) {
            return errorFuture(new IOException("submit task error:" + e, e));
        } finally {
            if (needRelease) {
                this.semaphore.release();
            }
        }
    }

    private void setRequestId(NettyTcpClientRequest request) {
        while (request != null) {
            requestId++;
            if (requestId < 0) {
                requestId = 1;
            }
            request.setSeqId(requestId);
            request = request.getNext();
        }
    }

    private static void notifyError(NettyTcpClientRequest request, Throwable ex) {
        if (request.getNotified().compareAndSet(false, true)) {
            logger.info("error : {}", ex.toString());
            request.getClient().semaphore.release();
            request.getFuture().completeExceptionally(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void notifySuccess(NettyTcpClientRequest request, Object data) {
        if (request.getNotified().compareAndSet(false, true)) {
            request.getClient().semaphore.release();
            request.getFuture().complete(data);
        }
    }

    private void run() {
        final int maxBatchSize = config.getMaxBatchSize();
        final int enableBatchPermits = config.getMaxPending() - config.getAutoBatchConcurrencyThreshold();
        final long maxBatchPendingNanos = config.getMaxAutoBatchPendingNanos();
        long totalRequest = 0;
        long totalBatch = 0;
        while (status <= STATUS_STOPPING) {
            try {
                NettyTcpClientRequest request;
                if (status < STATUS_STOPPING) {
                    request = waitForWriteQueue.poll(1, TimeUnit.SECONDS);
                } else {
                    request = waitForWriteQueue.poll();
                }

                if (request != null) {
                    totalRequest++;
                    NettyTcpClientRequest current = request;
                    // 如果pending数超过阈值，就合并发送
                    int restPermits = semaphore.availablePermits();
                    if (restPermits < enableBatchPermits) {
                        long restTime = maxBatchPendingNanos;
                        for (int i = 0; i < maxBatchSize - 1; i++) {
                            if (restTime < 1) {
                                break;
                            }
                            long start = System.nanoTime();
                            NettyTcpClientRequest next;
                            if (status < STATUS_STOPPING) {
                                next = waitForWriteQueue.poll(restTime, TimeUnit.NANOSECONDS);
                            } else {
                                next = waitForWriteQueue.poll();
                            }
                            if (next != null) {
                                totalRequest++;
                                current.setNext(next);
                                current = next;
                            } else {
                                break;
                            }
                            restTime = restTime - (System.nanoTime() - start);
                        }
                    }
                    totalBatch++;
                    sendRequest0(channelFuture, request);
                } else if (status >= STATUS_STOPPING) {
                    break;
                }
                cleanExpireRequest();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        logger.debug("client socket write thread finished. avg batch size is " + 1.0 * totalRequest / totalBatch);
        doClose();
    }

    private void sendRequest0(ChannelFuture currentChannelFuture, NettyTcpClientRequest request) {
        try {
            Channel channel;
            if (currentChannelFuture == null) {
                channel = connect();
            } else {
                channel = currentChannelFuture.channel();
                if (channel == null || !channel.isActive()) {
                    channel = connect();
                }
            }
            setRequestId(request);

            if (true) {
                NettyTcpClientRequest tmp = request;
                while (tmp != null) {
                    waitForResponseMap.put(tmp.getSeqId(), tmp);
                    tmp = tmp.getNext();
                }
                ChannelFuture writeResult = channel.writeAndFlush(request);
                Channel selectChannel = channel;
                writeResult.addListener(future -> processWriteError(future, request, selectChannel));
            } else {
                // 这个分支测试用的
                NettyTcpClientRequest tmp = request;
                while (tmp != null) {
                    waitForResponseMap.put(tmp.getSeqId(), tmp);
                    if (tmp.getNotified().compareAndSet(false, true)) {
                        semaphore.release();
                        tmp.getFuture().complete(null);
                    }
                    waitForResponseMap.remove(tmp.getSeqId());
                    tmp = tmp.getNext();
                }
            }
        } catch (Throwable e) {
            NettyTcpClientRequest tmp = request;
            while (tmp != null) {
                if (tmp.getSeqId() > 0) {
                    waitForResponseMap.remove(tmp.getSeqId());
                }
                notifyError(tmp, new IOException(e.toString(), e));
                tmp = tmp.getNext();
            }
        }
    }

    private void processWriteError(Future<? super Void> future, NettyTcpClientRequest request, Channel selectChannel) {
        if (!future.isSuccess()) {
            while (request != null) {
                waitForResponseMap.remove(request.getSeqId());
                notifyError(request, new IOException("write fail: ex=" + future.cause()));
                request = request.getNext();
            }
            logger.error("[client] write error: " + future.cause());
            closeChannel(selectChannel);
        }
    }

    private void cleanExpireRequest() {
        long currentNano = System.nanoTime();
        if (currentNano - lastCleanTimeNano > 1000 * 1000 * 1000) {
            doCleanExpireData(currentNano);
            lastCleanTimeNano = currentNano;
        }
    }

    private void doCleanExpireData(long currentNano) {
        Iterator<Entry<Integer, NettyTcpClientRequest>> iterator = waitForResponseMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Integer, NettyTcpClientRequest> entry = iterator.next();
            NettyTcpClientRequest req = entry.getValue();
            if (currentNano > req.getDeadlineNano()) {
                notifyError(req, new IOException("timeout: " + req.getTimeout() + "ms"));
                iterator.remove();
            }
        }
    }

    private synchronized void closeChannel(Channel oldChannel) {
        ChannelFuture cf = this.channelFuture;
        if (oldChannel != null && cf != null && oldChannel == cf.channel()) {
            channelFuture = null;
            if (oldChannel.isActive()) {
                logger.info("closing channel {}", oldChannel);
                oldChannel.close();
            }
        }
    }

    @Override
    public void close() {
        if (this.status >= STATUS_STOPPING) {
            return;
        }
        this.status = STATUS_STOPPING;
        logger.info("netty tcp client closing: begin shutdown");
        this.thread.interrupt();
    }

    private void doClose() {
        long deadline = System.nanoTime() + config.getCloseTimeoutMillis() * 1000 * 1000;
        try {
            logger.debug("netty tcp client closing: clean waitForWriteQueue ...");
            waitForWriteQueue.forEach(c -> notifyError(c, new IOException("closed")));

            while (!waitForResponseMap.isEmpty()) {
                Thread.sleep(10);
                doCleanExpireData(System.nanoTime());
                if (System.nanoTime() > deadline) {
                    break;
                }
            }

            logger.debug("netty tcp client closing: clean waitForResponseMap ...");
            waitForResponseMap.values().forEach(c -> notifyError(c, new IOException("closed")));

            logger.debug("netty tcp client closing: shutdown event loop ...");
            Future<?> f1 = this.ioLoop.shutdownGracefully(config.getCloseSilenceMillis(),
                    config.getCloseTimeoutMillis(), TimeUnit.MILLISECONDS);
            Future<?> f2 = null;
            if (workerLoop != null) {
                f2 = this.workerLoop.shutdownGracefully(config.getCloseSilenceMillis(),
                        config.getCloseTimeoutMillis(), TimeUnit.MILLISECONDS);
            }

            f1.sync();
            if (f2 != null) {
                f2.sync();
            }

            logger.info("netty tcp client closing: finish shutdown");
            closeFuture.complete(null);
        } catch (Throwable e) {
            closeFuture.completeExceptionally(e);
        }
        this.status = STATUS_STOPPED;
    }

    private class NettyTcpClientDecoder extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf inBuffer = (ByteBuf) msg;
            try {
                int type = inBuffer.readByte() & Commands.TYPE_MASK;
                inBuffer.readShort(); // the command
                int seqId = inBuffer.readInt();
                if (type == Commands.TYPE_RESP) {
                    NettyTcpClientRequest request = waitForResponseMap.remove(seqId);
                    if (request != null) {
                        try {
                            Object result = request.getCallback().decode(inBuffer);
                            notifySuccess(request, result);
                        } catch (Throwable e) {
                            notifyError(request, e);
                        }
                    } else {
                        logger.debug("the request expired: {}", seqId);
                    }
                } else {
                    // TODO 暂时没有支持server push
                }
            } finally {
                inBuffer.release();
            }
        }
    }

    private static class NettyTcpClientEncoder extends MessageToByteEncoder<NettyTcpClientRequest> {

        @Override
        protected void encode(ChannelHandlerContext ctx, NettyTcpClientRequest msg, ByteBuf out) {
            while (msg != null) {
                int startIndex = out.writerIndex();
                out.writeInt(0);
                int startReadableBytes = out.readableBytes();

                out.writeByte(Commands.TYPE_REQ);
                out.writeShort(msg.getCommand());
                out.writeInt(msg.getSeqId());
                msg.getCallback().encode(out);

                int endIndex = out.writerIndex();
                int len = out.readableBytes() - startReadableBytes;
                out.writerIndex(startIndex);
                out.writeInt(len);
                out.writerIndex(endIndex);
                msg = msg.getNext();
            }
        }
    }

}
