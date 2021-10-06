package simplerpc.benchmark;

import simplerpc.AutoBatchMode;
import simplerpc.NettyServer;
import simplerpc.NettyServerConfig;

/**
 * @author huangli
 * Created on 2021-10-06
 */
public class ServerStarter {
    public static void main(String[] args) throws Exception {
        NettyServerConfig config = new NettyServerConfig();
        config.setPort(12345);
        config.setAutoBatchMode(AutoBatchMode.MODE_AUTO);
        // config.setBizThreads(0);
        NettyServer server = new NettyServer(config);

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            server.shutdown();
        }));
    }
}
