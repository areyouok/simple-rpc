package simplerpc.benchmark;

import java.util.concurrent.Executors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.rocketmq.remoting.CommandCustomHeader;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.NettyRemotingServer;
import org.apache.rocketmq.remoting.netty.NettyRequestProcessor;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

import io.netty.channel.ChannelHandlerContext;
import simplerpc.Commands;

/**
 * @author huangli
 * Created on 2021-10-06
 */
public class RmqServer {

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

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("p", "port", true, "port");


        DefaultParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options, args, true);

        NettyServerConfig config = new NettyServerConfig();
        config.setListenPort(Integer.parseInt(commandLine.getOptionValue("p", "12345")));
        NettyRemotingServer server = new NettyRemotingServer(config);
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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
        }));
    }
}
