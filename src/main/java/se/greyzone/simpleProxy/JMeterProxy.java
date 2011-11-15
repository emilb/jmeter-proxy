package se.greyzone.simpleProxy;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.littleshoot.proxy.DefaultHttpProxyServer;
import org.littleshoot.proxy.HttpFilter;
import org.littleshoot.proxy.HttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMeterProxy {

    private static Logger log = LoggerFactory
            .getLogger(JMeterProxy.class);

    final HttpProxyServer server;
    final int port;
    final RequestLogger requestLogger;

    public JMeterProxy(final int proxyPort, final String testName,
            final List<String> acceptedHosts, final List<String> uriFilter) {

        port = proxyPort;
        requestLogger = new RequestLogger(testName, acceptedHosts, uriFilter);

        server = new DefaultHttpProxyServer(port,
                requestLogger, new HashMap<String, HttpFilter>());

        addShutDownHook();
        startProxy();
    }

    private void addShutDownHook() {

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {

                JMeterProxy.this.stopProxy();
            }
        });
    }

    public void startProxy() {

        log.info("Starting proxy:");
        log.info("Port: {}", port);
        log.info("Name of testcase: {}", requestLogger.getTestName());
        log.info("Included hosts: {}", requestLogger.getLogForHostNames());
        log.info("Included URIs: {}", requestLogger.getRequestURINameFilter());

        server.start();
    }

    public void stopProxy() {

        log.info("Stopping proxy...");
        server.stop();
    }

    public static void main(final String... args) throws IOException {

        final OptionParser parser = new OptionParser();

        final OptionSpec<Integer> port = parser.accepts("p", "proxy port")
                .withRequiredArg()
                .ofType(Integer.class)
                .describedAs("port").defaultsTo(8080);

        final OptionSpec<String> testName = parser
                .accepts("n", "name of testcase").withRequiredArg()
                .ofType(String.class)
                .describedAs("name").defaultsTo("testcase");

        final OptionSpec<String> uris = parser
                .accepts("u",
                        "Only log requests with URIs that contain any of the specified values")
                .withRequiredArg().ofType(String.class)
                .describedAs("uris").withValuesSeparatedBy(',');

        final OptionSpec<String> hosts = parser.accepts("h",
                "Only log requests designated to any of the specified hosts")
                .withRequiredArg().ofType(String.class)
                .describedAs("hosts").withValuesSeparatedBy(',');

        final OptionSpec<Void> help = parser.accepts("?", "show help");

        final OptionSet options = parser.parse(args);

        if (options.has(help)) {
            parser.printHelpOn(System.out);
            System.exit(0);
        }

        new JMeterProxy(options.valueOf(port),
                options.valueOf(testName), options.valuesOf(hosts),
                options.valuesOf(uris));
    }

    @SuppressWarnings("unused")
    private static OptionParser createOptionParser() {

        return new OptionParser() {

            {
                accepts("p", "proxy port").withRequiredArg()
                        .ofType(Integer.class)
                        .describedAs("port").defaultsTo(8080);

                accepts("n", "name of testcase").withRequiredArg()
                        .ofType(String.class)
                        .describedAs("name").defaultsTo("testcase");

                accepts("u",
                        "Only log requests with URIs that contain any of the specified values")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("uris").withValuesSeparatedBy(',');

                accepts("h",
                        "Only log requests designated to any of the specified hosts")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("hosts").withValuesSeparatedBy(',');

                accepts("?", "show help");
            }
        };
    }
}
