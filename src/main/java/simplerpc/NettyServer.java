package simplerpc;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;

/**
 * @author huangli
 */
public class NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    private final NettyServerConfig config;

    private NioEventLoopGroup eventLoopGroupBoss;
    private NioEventLoopGroup eventLoopGroupSelector;
    private DefaultEventExecutorGroup bizExecutorGroup;

    // 这个注入
    private final RequestHandler requestHandler = new RequestHandler();

    private final WriteExHandler writeExHandler = new WriteExHandler();
    private final AutoBatchWriteHandler autoBatchWriteHandler;

    public NettyServer(NettyServerConfig config) {
        this.config = config;
        if (config.getAutoBatchMode() != AutoBatchMode.MODE_DISABLE) {
            autoBatchWriteHandler = new AutoBatchWriteHandler(config.getAutoBatchMode(), config.getMaxBatchCount(),
                    config.getMaxBufferSize(), config.getBatchTimeWindowsNanos());
        } else {
            autoBatchWriteHandler = null;
        }
    }

    public void start() throws Exception {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        this.eventLoopGroupBoss = new NioEventLoopGroup(1, new ThreadFactory() {
            private final AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("ServerNIOBoss_%d", this.threadIndex.incrementAndGet()));
            }
        });

        this.eventLoopGroupSelector = new NioEventLoopGroup(config.getIoThreads(), new ThreadFactory() {
            private final AtomicInteger threadIndex = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("ServerNIOSelector_%d", this.threadIndex.incrementAndGet()));
            }
        });

        if (config.getBizThreads() > 0) {
            this.bizExecutorGroup = new DefaultEventExecutorGroup(config.getBizThreads(), new ThreadFactory() {
                private final AtomicInteger threadIndex = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "ServerBizThread_" + this.threadIndex.incrementAndGet());
                }
            });
        }

        serverBootstrap.group(this.eventLoopGroupBoss, this.eventLoopGroupSelector)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, false)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_SNDBUF, 65535)
                .childOption(ChannelOption.SO_RCVBUF, 65535)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .localAddress(config.getPort())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        if (config.getMaxIdleSeconds() > 0) {
                            ch.pipeline().addLast(new IdleStateHandler(config.getMaxIdleSeconds(), 0, 0));
                        }
                        ch.pipeline().addLast(new HandShakeHandler(config.getHandShakeBytes(), true));
                        ch.pipeline().addLast(writeExHandler);
                        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(2 * 1024 * 1024, 0, 4, 0, 4));

                        if (bizExecutorGroup != null) {
                            if (autoBatchWriteHandler != null) {
                                ch.pipeline().addLast(bizExecutorGroup, autoBatchWriteHandler);
                            }
                            ch.pipeline().addLast(bizExecutorGroup, requestHandler);
                        } else {
                            if (autoBatchWriteHandler != null) {
                                ch.pipeline().addLast(autoBatchWriteHandler);
                            }
                            ch.pipeline().addLast(requestHandler);
                        }
                        ch.pipeline().addLast(new NettyConnectManageHandler(true, channel -> closeChannel(channel)));
                    }
                });

        serverBootstrap.bind().sync();
    }

    private void closeChannel(Channel channel) {
        if (channel != null && channel.isActive()) {
            logger.info("closing channel {}", channel);
            channel.close();
        }
    }

    public void shutdown() {
        try {
            this.eventLoopGroupBoss.shutdownGracefully();
            this.eventLoopGroupSelector.shutdownGracefully();
            if (bizExecutorGroup != null) {
                this.bizExecutorGroup.shutdownGracefully();
            }
        } catch (Exception e) {
            logger.error("NettyRemotingServer shutdown exception, ", e);
        }
    }

    @ChannelHandler.Sharable
    private static class WriteExHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            ctx.write(msg, promise.addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    Throwable failureCause = future.cause();
                    logger.warn("write fail. {}, msg: {}", ctx.channel().remoteAddress(), failureCause.toString());
                    if (ctx.channel().isActive()) {
                        logger.warn("close channel:" + ctx.channel());
                        ctx.close();
                    }
                }
            }));
        }
    }

    public RequestHandler getRequestHandler() {
        return requestHandler;
    }

    public NettyServerConfig getConfig() {
        return config;
    }
}
