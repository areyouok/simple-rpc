package simplerpc;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author huangli
 */
public class HandShakeHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(HandShakeHandler.class);
    private final byte[] handShakeBytes;
    private final boolean writeBack;

    private byte[] readBuffer;
    private int writeIndex;

    public HandShakeHandler(byte[] handShakeBytes, boolean writeBack) {
        this.handShakeBytes = handShakeBytes;
        this.readBuffer = new byte[handShakeBytes.length];
        this.writeBack = writeBack;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) {
        ByteBuf msg = (ByteBuf) obj;
        boolean needRelease = true;
        try {
            int readLen = readBuffer.length - writeIndex;
            readLen = Math.min(readLen, msg.readableBytes());
            msg.readBytes(readBuffer, writeIndex, readLen);
            writeIndex += readLen;
            if (writeIndex < readBuffer.length) {
                // 没读满，需要继续读
                return;
            }
            if (!Arrays.equals(readBuffer, handShakeBytes)) {
                logger.error("handshake mismatch: {}", ctx.channel().remoteAddress());
                ctx.close();
            } else {
                ctx.pipeline().remove(this);
                if (writeBack) {
                    ByteBuf buf = ctx.alloc().buffer();
                    buf.writeBytes(handShakeBytes);
                    ctx.writeAndFlush(buf);
                }
                readBuffer = null;
                logger.info("[{}] handshake success: {}", writeBack ? "server" : "client", ctx.channel());
                if (msg.readableBytes() > 0) {
                    needRelease = false;
                    // 还需要继续用，所以不能释放，交给后面的链路释放
                    ctx.fireChannelRead(msg);
                }
            }
        } finally {
            if (needRelease) {
                msg.release();
            }
        }
    }
}
