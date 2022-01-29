package simplerpc.benchmark;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyRemotingClient;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

import simplerpc.Commands;
import simplerpc.benchmark.RmqServer.ReqHeader;

/**
 * @author huangli
 * Created on 2021-10-06
 */
public class RmqClient extends BenchBase {

    private static CommandLine commandLine;
    private static boolean sync;

    private static int clientCount = 1;
    private NettyRemotingClient[] client;
    private static byte[] DATA = "hello".getBytes(StandardCharsets.UTF_8);

    private final String remoteAddr;

    public RmqClient(int threadCount, long time) {
        super(threadCount, time);
        String host = commandLine.getOptionValue('h', "127.0.0.1");
        int port = Integer.parseInt(commandLine.getOptionValue('p', "12345"));
        remoteAddr = host + ":" + port;
    }

    @Override
    public void init() throws Exception {
        client = new NettyRemotingClient[clientCount];
        for (int i = 0; i < clientCount; i++) {
            NettyClientConfig c = new NettyClientConfig();
            client[i] = new NettyRemotingClient(c);
            client[i].start();
        }
    }

    @Override
    public void shutdown() {
        for (int i = 0; i < clientCount; i++) {
            client[i].shutdown();
        }
    }

    @Override
    public void test(int threadIndex) {
        NettyRemotingClient c = client[threadIndex % clientCount];

        RemotingCommand req = RemotingCommand.createRequestCommand(Commands.COMMAND_PING, new ReqHeader());
        req.setBody(DATA);
        try {
            if (sync) {
                // 同步调用
                c.invokeSync(remoteAddr, req, 3000);
                successCount.add(1);
            } else {
                // 异步调用
                c.invokeAsync(remoteAddr, req, 3000, responseFuture -> {
                    if (responseFuture.isSendRequestOK()) {
                        successCount.add(1);
                    } else {
                        failCount.add(1);
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            failCount.add(1);
        }
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("h", "host", true, "server ip");
        options.addOption("p", "port", true, "port");
        options.addOption("d", "duration", true, "test time in millis");
        options.addOption("t", "thread", true, "thread count");
        options.addOption("s", "sync", false, "sync mode");
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
        new RmqClient(thread, duration).start();
    }
}
