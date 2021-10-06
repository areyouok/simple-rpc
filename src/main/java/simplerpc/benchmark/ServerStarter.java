package simplerpc.benchmark;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import simplerpc.AutoBatchMode;
import simplerpc.NettyServer;
import simplerpc.NettyServerConfig;

/**
 * @author huangli
 * Created on 2021-10-06
 */
public class ServerStarter {
    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("e", "epoll", false, "use epoll");
        options.addOption("p", "port", true, "port");
        options.addOption("m", "mode", true, "auto batch mode");
        options.addOption("t", "bizThreads", true, "biz thread count");
        options.addOption(null, "maxBufferSize", true, "maxBufferSize");
        options.addOption(null, "maxBatchCount", true, "maxBatchCount");


        DefaultParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options, args, true);

        NettyServerConfig config = new NettyServerConfig();
        config.setPort(Integer.parseInt(commandLine.getOptionValue("p", "12345")));
        if (commandLine.hasOption("m")) {
            if ("enable".equalsIgnoreCase(commandLine.getOptionValue("m"))) {
                config.setAutoBatchMode(AutoBatchMode.MODE_ENABLE);
            } else if ("disable".equalsIgnoreCase(commandLine.getOptionValue("m"))) {
                config.setAutoBatchMode(AutoBatchMode.MODE_DISABLE);
            } else {
                config.setAutoBatchMode(AutoBatchMode.MODE_AUTO);
            }
        } else {
            config.setAutoBatchMode(AutoBatchMode.MODE_AUTO);
        }
        if (commandLine.hasOption('e')) {
            config.setEpoll(true);
        }
        if (commandLine.hasOption("t")) {
            config.setBizThreads(Integer.parseInt(commandLine.getOptionValue("t")));
        }
        if (commandLine.hasOption("maxBufferSize")) {
            config.setMaxBufferSize(Integer.parseInt(commandLine.getOptionValue("maxBufferSize")));
        }
        if (commandLine.hasOption("maxBatchCount")) {
            config.setMaxBatchCount(Integer.parseInt(commandLine.getOptionValue("maxBatchCount")));
        }
        NettyServer server = new NettyServer(config);

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
        }));
    }
}
