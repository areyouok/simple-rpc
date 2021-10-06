package simplerpc;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Assertions;

import io.netty.buffer.ByteBuf;
import simplerpc.NettyTcpClient.Callback;
import simplerpc.benchmark.BenchBase;

/**
 * @author huangli
 * Created on 2021-09-14
 */
public class NettyClientBenchmark extends BenchBase {

    private final int clientCount = 1;
    private NettyServer server;
    private NettyTcpClient[] client;
    private final static byte[] DATA = "hello".getBytes(StandardCharsets.UTF_8);

    public NettyClientBenchmark(int threadCount, int time) {
        super(threadCount, time);
    }

    @Override
    public void init() throws Exception {
        NettyServerConfig config = new NettyServerConfig();
        config.setPort(12345);
        config.setAutoBatchMode(AutoBatchMode.MODE_AUTO);
        // config.setBizThreads(0);
        server = new NettyServer(config);

        server.start();
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
        server.shutdown();
    }

    @Override
    public void test(int threadIndex) {
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
                    Assertions.assertArrayEquals(DATA, bs);
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
        new NettyClientBenchmark(128, 10000).start();
    }
}
