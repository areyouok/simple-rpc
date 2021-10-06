package simplerpc;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import org.apache.rocketmq.remoting.CommandCustomHeader;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyRemotingClient;
import org.apache.rocketmq.remoting.netty.NettyRemotingServer;
import org.apache.rocketmq.remoting.netty.NettyRequestProcessor;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

import io.netty.channel.ChannelHandlerContext;
import simplerpc.benchmark.BenchBase;

/**
 * @author huangli
 * Created on 2021-09-14
 */
public class RmqBenchmark extends BenchBase {

    private NettyRemotingServer server;
    private NettyRemotingClient client;
    private static byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

    public RmqBenchmark(int threadCount, int time) {
        super(threadCount, time);
    }

    public static class ReqHeader implements CommandCustomHeader {
        @Override
        public void checkFields() throws RemotingCommandException {
        }
    }

    public static class RespHeader implements CommandCustomHeader {
        @Override
        public void checkFields() throws RemotingCommandException {
        }
    }


    @Override
    public void init() throws Exception {
        server = new NettyRemotingServer(new NettyServerConfig());
        server.registerProcessor(Commands.COMMAND_PING, new NettyRequestProcessor() {
            @Override
            public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) throws Exception {
                try {
                    request.decodeCommandCustomHeader(ReqHeader.class);
                    RemotingCommand resp =
                            RemotingCommand.createResponseCommand(Commands.COMMAND_PING, null, RespHeader.class);
                    resp.setBody(request.getBody());

                    // 如果在这里睡1ms，rocketmq remoting异步调用将会失败，因为没有背压能力
                    // Thread.sleep(1);

                    return resp;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            public boolean rejectRequest() {
                return false;
            }
        }, Executors.newFixedThreadPool(4));
        server.start();

        client = new NettyRemotingClient(new NettyClientConfig());
        client.start();
    }

    @Override
    public void shutdown() throws Exception {
        Thread.sleep(1000);
        client.shutdown();
        server.shutdown();
    }

    @Override
    public void test(int threadIndex) {
        RemotingCommand req = RemotingCommand.createRequestCommand(Commands.COMMAND_PING, new ReqHeader());
        req.setBody(data);
        try {
            // 同步调用
//            client.invokeSync("127.0.0.1:8888", req, 3000);
//            successCount.add(1);

            // 异步调用
            client.invokeAsync("127.0.0.1:8888", req, 3000, responseFuture -> {
                if (responseFuture.isSendRequestOK()) {
                    successCount.add(1);
                } else {
                    failCount.add(1);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            failCount.add(1);
        }
    }

    public static void main(String[] args) throws Exception {
        new RmqBenchmark(128, 10000).start();
    }
}
