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

    private static int clientCount = 1;
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
            if (commandLine.hasOption("autoBatchConcurrencyThreshold")) {
                c.setAutoBatchConcurrencyThreshold(Integer.parseInt(commandLine.getOptionValue("autoBatchConcurrencyThreshold")));
            }
            if (commandLine.hasOption("maxBatchSize")) {
                c.setMaxBatchSize(Integer.parseInt(commandLine.getOptionValue("maxBatchSize")));
            }
            if (commandLine.hasOption("maxPending")) {
                c.setMaxPending(Integer.parseInt(commandLine.getOptionValue("maxPending")));
            }
            if (commandLine.hasOption('e')) {
                c.setEpoll(true);
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
    public void test(int threadIndex) {
        NettyTcpClient c = client[threadIndex % clientCount];

        CompletableFuture<Void> fu = c.sendRequest(Commands.COMMAND_PING, new Callback<Void>() {
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

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("e", "epoll", false, "use epoll");
        options.addOption("h", "host", true, "server ip");
        options.addOption("p", "port", true, "port");
        options.addOption("d", "duration", true, "test time in millis");
        options.addOption("t", "thread", true, "thread count");
        options.addOption("s", "sync", false, "sync mode");
        options.addOption(null, "autoBatchConcurrencyThreshold", true, "autoBatchConcurrencyThreshold");
        options.addOption(null, "maxBatchSize", true, "maxBatchSize");
        options.addOption(null, "maxPending", true, "maxPending");
        options.addOption("l", "length", true, "message size");
        options.addOption("c", "client", true, "client count");


        DefaultParser parser = new DefaultParser();
        commandLine = parser.parse(options, args, true);

        sync = commandLine.hasOption('s');
        if (commandLine.hasOption("l")) {
            byte[] b = new byte[Integer.parseInt(commandLine.getOptionValue("l"))];
            new Random().nextBytes(b);
            DATA = b;
        }
        if (commandLine.hasOption('c')) {
            clientCount = Integer.parseInt(commandLine.getOptionValue('c'));
        }

        int thread = Integer.parseInt(commandLine.getOptionValue('t', "1"));
        long duration = Long.parseLong(commandLine.getOptionValue('d', "10000"));
        new ClientStarter(thread, duration).start();
    }
}
