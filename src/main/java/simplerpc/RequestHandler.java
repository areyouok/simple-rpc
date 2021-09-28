package simplerpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author huangli
 */
@ChannelHandler.Sharable
public class RequestHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    static class PingProcessor implements RequestProcessor {
        @Override
        public short getCommand() {
            return Commands.COMMAND_PING;
        }

        @Override
        public ByteBuf process(ChannelHandlerContext ctx, ByteBuf msg) {
            int len = msg.readableBytes();
            ByteBuf buffer = ctx.alloc().buffer(2 + len);
            buffer.writeShort(Commands.CODE_SUCCESS);
            buffer.writeBytes(msg);
            return buffer;
        }
    }

    private static final RequestProcessor PING_PROCESSOR = new PingProcessor();

    private static final RequestProcessor CLOSE_PROCESSOR = new RequestProcessor() {
        @Override
        public short getCommand() {
            return Commands.COMMAND_CLOSE;
        }

        @Override
        public ByteBuf process(ChannelHandlerContext ctx, ByteBuf msg) {
            ByteBuf buffer = ctx.alloc().buffer(2);
            buffer.writeShort(Commands.CODE_SUCCESS);
            return buffer;
        }
    };

    private final RequestProcessor[] requestProcessors = new RequestProcessor[Short.MAX_VALUE];

    public RequestHandler() {
        registerProcessor(PING_PROCESSOR);
        registerProcessor(CLOSE_PROCESSOR);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) {
        ByteBuf msg = (ByteBuf) obj;
        int type = msg.readByte() & Commands.TYPE_MASK;
        short command = msg.readShort();
        int seqId = msg.readInt();
        ByteBuf resp;
        try {
            if (type == Commands.TYPE_REQ) {
                RequestProcessor c = null;
                try {
                    c = processReq(command);
                    resp = c.process(ctx, msg);
                } catch (Exception e) {
                    // 统一的指令错误处理，这种情况下不关闭连接
                    resp = ctx.alloc().buffer();
                    resp.writeShort(Commands.CODE_FAIL);
                    // 用一下pingProcessor
                    PING_PROCESSOR.writeString(resp, e.toString());
                }

                // 其它的地方出错由ExHandler处理，关闭连接
                ByteBuf header = ctx.alloc().buffer(4 + Commands.HEAD_LENGTH);
                header.writeInt(resp.readableBytes() + Commands.HEAD_LENGTH);
                header.writeByte(Commands.TYPE_RESP);
                header.writeShort(command);
                header.writeInt(seqId);

                ctx.write(header);
                ctx.writeAndFlush(resp);

                if (c == CLOSE_PROCESSOR) {
                    logger.info("connection close. {}", ctx.channel());
                    ctx.close();
                }
            } else {
                // TODO 暂不支持server push
            }
        } finally {
            msg.release();
        }
    }

    private RequestProcessor processReq(short command) throws Exception {
        try {
            RequestProcessor c = requestProcessors[command];
            if (c == null) {
                throw new Exception("unknown command: " + command);
            }
            return c;
        } catch (IndexOutOfBoundsException e) {
            throw new Exception("unknown command: " + command);
        }
    }

    public void registerProcessor(RequestProcessor processor) {
        requestProcessors[processor.getCommand()] = processor;
    }
}
