package simplerpc.benchmark;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

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

    private static CommandLine commandLine;
    private static boolean sync;

    private final int clientCount = 1;
    private NettyTcpClient[] client;
    private static byte[] DATA = "hello".getBytes(StandardCharsets.UTF_8);

    public ClientStarter(int threadCount, long time) {
        super(threadCount, time);
    }

    @Override
    public void init() throws Exception {
        client = new NettyTcpClient[clientCount];
        String host = commandLine.getOptionValue('h', "127.0.0.1");
        int port = Integer.parseInt(commandLine.getOptionValue('p', "12345"));
        for (int i = 0; i < clientCount; i++) {
            NettyTcpClientConfig c = new NettyTcpClientConfig();
            if (commandLine.hasOption("autoBatchFactor")) {
                c.setAutoBatchFactor(Float.parseFloat(commandLine.getOptionValue("autoBatchFactor")));
            }
            if (commandLine.hasOption("maxBatchSize")) {
                c.setMaxBatchSize(Integer.parseInt(commandLine.getOptionValue("maxBatchSize")));
            }
            client[i] = new NettyTcpClient(() -> Collections.singletonList(host + ":" + port), c);
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
            }, 10 * 1000);

            if (sync) {
                //同步调用
                try {
                    fu.get();
                    successCount.add(1);
                } catch (Exception e) {
                    failCount.add(1);
                }
            } else {
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
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("h", "host", true, "server ip");
        options.addOption("p", "port", true, "port");
        options.addOption("d", "duration", true, "test time in millis");
        options.addOption("t", "thread", true, "thread count");
        options.addOption("s", "sync", false, "sync mode");
        options.addOption(null, "autoBatchFactor", true, "autoBatchFactor");
        options.addOption(null, "maxBatchSize", true, "maxBatchSize");
        options.addOption("l", "length", true, "message size");

        DefaultParser parser = new DefaultParser();
        commandLine = parser.parse(options, args, true);

        sync = commandLine.hasOption('s');
        if (commandLine.hasOption("s")) {
            byte[] b = new byte[Integer.parseInt(commandLine.getOptionValue("s"))];
            new Random().nextBytes(b);
            DATA = b;
        }

        int thread = Integer.parseInt(commandLine.getOptionValue('t', "1"));
        long duration = Long.parseLong(commandLine.getOptionValue('d', "10000"));
        new ClientStarter(thread, duration).start();
    }
}
