package simplerpc;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;

/**
 * @author huangli
 */
@ChannelHandler.Sharable
@SuppressWarnings("checkstyle:MagicNumber")
public class AutoBatchWriteHandler extends ChannelDuplexHandler {
    private static final Logger logger = LoggerFactory.getLogger(AutoBatchWriteHandler.class);

    private static final AttributeKey<AutoBatchStatus> STATUS = AttributeKey.valueOf("AutoBatchStatus");

    private final int autoBatchMode;
    private final int maxBufferSize;
    private final int maxBatchCount;
    private final long batchTimeWindowNanos;

    @SuppressWarnings("checkstyle:VisibilityModifier")
    private static class AutoBatchStatus {
        LinkedList<ByteBuf> msgs = new LinkedList<>();
        final AutoBatchMode mode;

        private long totalBytes;
        private long totalRequestFlushCount;
        private long totalActualFlushCount;

        public AutoBatchStatus(AutoBatchMode mode) {
            this.mode = mode;
        }
    }

    public AutoBatchWriteHandler(int autoBatchMode, int maxBatchCount, int maxBufferSize, long batchTimeWindowNanos) {
        this.autoBatchMode = autoBatchMode;
        this.maxBatchCount = maxBatchCount;
        this.maxBufferSize = maxBufferSize;
        this.batchTimeWindowNanos = batchTimeWindowNanos;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        AutoBatchMode mode = new AutoBatchMode(autoBatchMode, maxBatchCount, maxBufferSize, batchTimeWindowNanos);
        ctx.channel().attr(STATUS).set(new AutoBatchStatus(mode));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        AutoBatchStatus status = ctx.channel().attr(STATUS).get();
        super.channelInactive(ctx);
        logger.debug("[server] channelInactive avgBatchCount={}, avgBatchSize={}",
                (float) status.totalRequestFlushCount / status.totalActualFlushCount,
                (float) status.totalBytes / status.totalActualFlushCount);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            AutoBatchStatus status = ctx.channel().attr(STATUS).get();
            ByteBuf buffer = (ByteBuf) msg;
            status.totalBytes += buffer.readableBytes();
            if (status.mode.isBatchStarted()) {
                status.msgs.add(buffer);
                status.mode.addSize(buffer.readableBytes());
                //                if (status.mode.shouldFlush()) {
                //                    doFlush(ctx, status);
                //                }
            } else {
                super.write(ctx, msg, promise);
            }
        } else {
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        AutoBatchStatus status = ctx.channel().attr(STATUS).get();
        status.totalRequestFlushCount++;
        if (status.mode.isBatchStarted()) {
            status.mode.addCount();
            if (status.mode.shouldFlush()) {
                doFlush(ctx, status, false);
            }
        } else if (status.mode.startBatchIfNecessary()) {
            status.mode.addCount();
            ctx.executor().schedule(() -> {
                doFlush(ctx, status, true);
            }, batchTimeWindowNanos, TimeUnit.NANOSECONDS);
        } else {
            status.totalActualFlushCount++;
            super.flush(ctx);
            status.mode.finish(false);
        }
    }

    private void doFlush(ChannelHandlerContext ctx, AutoBatchStatus status, boolean finishWindow) {
        LinkedList<ByteBuf> queue = status.msgs;
        status.totalActualFlushCount++;
        if (queue.size() > 0) {
            int bytes = status.mode.getPendingSize();
            ByteBuf buffer = ctx.alloc().directBuffer(bytes);
            for (ByteBuf buf : queue) {
                buffer.writeBytes(buf);
                buf.release();
            }
            queue.clear();
            ctx.writeAndFlush(buffer);
        } else {
            ctx.flush();
        }
        status.mode.finish(finishWindow);
    }
}
