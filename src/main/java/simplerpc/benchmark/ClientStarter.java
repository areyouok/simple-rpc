package simplerpc.benchmark;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import io.netty.buffer.ByteBuf;
import simplerpc.Commands;
import simplerpc.NettyTcpClient;
import simplerpc.NettyTcpClient.Callback;
import simplerpc.NettyTcpClientConfig;

/**
 * @author huangli
 * Created on 2021-10-06
 */
public class ClientStarter extends BenchBase {

    private final int clientCount = 1;
    private NettyTcpClient[] client;
    private final static byte[] DATA = "hello".getBytes(StandardCharsets.UTF_8);

    public ClientStarter(int threadCount, int time) {
        super(threadCount, time);
    }

    @Override
    public void init() throws Exception {
        client = new NettyTcpClient[clientCount];
        for (int i = 0; i < clientCount; i++) {
            NettyTcpClientConfig c = new NettyTcpClientConfig();
            client[i] = new NettyTcpClient(() -> Collections.singletonList("127.0.0.1:12345"), c);
            client[i].start();
        }
    }

    @Override
    public void shutdown() {
        for (int i = 0; i < clientCount; i++) {
            client[i].close();
        }
    }

    @Override
    public void test() {
        for (int i = 0; i < clientCount; i++) {
            CompletableFuture<Void> fu = client[i].sendRequest(Commands.COMMAND_PING, new Callback<Void>() {
                @Override
                public void encode(ByteBuf out) {
                    out.writeBytes(DATA);
                }

                @Override
                public Void decode(ByteBuf in) {
                    short code = in.readShort();
                    if (code != Commands.CODE_SUCCESS) {
                        throw new RuntimeException();
                    }
                    byte[] bs = new byte[DATA.length];
                    in.readBytes(bs);
                    return null;
                }
            }, 3500);

            // 同步调用
            //            try {
            //                fu.get();
            //                successCount.add(1);
            //            } catch (Exception e) {
            //                failCount.add(1);
            //            }

            // 异步调用
            fu.handle((unused, throwable) -> {
                if (throwable != null) {
                    failCount.add(1);
                } else {
                    successCount.add(1);
                }
                return null;
            });
        }
    }

    public static void main(String[] args) throws Exception {
        new ClientStarter(1, 10000).start();
    }
}
