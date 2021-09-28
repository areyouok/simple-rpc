package simplerpc;

import java.net.SocketAddress;
import java.util.function.Consumer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * @author huangli
 */
public class NettyConnectManageHandler extends ChannelDuplexHandler {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(NettyConnectManageHandler.class.getName());

    private final String prefix;
    private final Consumer<Channel> closeAction;

    public NettyConnectManageHandler(boolean server, Consumer<Channel> closeAction) {
        this.prefix = server ? "[server]" : "[client]";
        this.closeAction = closeAction;
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {
        super.connect(ctx, remoteAddress, localAddress, promise);
        logger.info("{} connected. remote={}", prefix, remoteAddress);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        super.disconnect(ctx, promise);
        if (logger.isInfoEnabled()) {
            SocketAddress s = ctx.channel() == null ? null : ctx.channel().remoteAddress();
            logger.info("{} disconnected. remote={}", prefix, s);
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        super.close(ctx, promise);
        if (logger.isInfoEnabled()) {
            SocketAddress s = ctx.channel() == null ? null : ctx.channel().remoteAddress();
            logger.info("{} closed. remote={}", prefix, s);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        if (logger.isInfoEnabled()) {
            SocketAddress s = ctx.channel() == null ? null : ctx.channel().remoteAddress();
            logger.info("{} channelActive. remote={}", prefix, s);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (logger.isInfoEnabled()) {
            SocketAddress s = ctx.channel() == null ? null : ctx.channel().remoteAddress();
            logger.info("{} channelInactive. remote={}", prefix, s);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            SocketAddress s = ctx.channel() == null ? null : ctx.channel().remoteAddress();
            logger.warn("{} detect {}. remote={}", prefix, event.state(), s);
            closeAction.accept(ctx.channel());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        SocketAddress s = ctx.channel() == null ? null : ctx.channel().remoteAddress();
        logger.warn(prefix + " exceptionCaught. remote=" + s, cause);
        closeAction.accept(ctx.channel());
    }
}
