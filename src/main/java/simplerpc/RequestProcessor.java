package simplerpc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author huangli
 */
public interface RequestProcessor {

    short getCommand();

    ByteBuf process(ChannelHandlerContext ctx, ByteBuf msg) throws Exception;

    default void writeString(ByteBuf msg, String str) {
        if (str == null) {
            msg.writeInt(-1);
        } else {
            msg.markWriterIndex();
            msg.writeInt(0);
            int len = msg.writeCharSequence(str, StandardCharsets.UTF_8);
            int newIndex = msg.writerIndex();
            msg.resetWriterIndex();
            msg.writeInt(len);
            msg.writerIndex(newIndex);
        }
    }

    default String readString(ByteBuf msg) {
        int len = msg.readInt();
        if (len == -1) {
            return null;
        }
        byte[] bs = new byte[len];
        msg.readBytes(bs);
        return new String(bs, StandardCharsets.UTF_8);
    }

    default void writeMap(ByteBuf buffer, Map<String, String> map) {
        if (map == null) {
            buffer.writeInt(-1);
            return;
        }
        buffer.writeInt(map.size());
        for (Entry<String, String> en : map.entrySet()) {
            writeString(buffer, en.getKey());
            writeString(buffer, en.getValue());
        }
    }
}
