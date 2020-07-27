package com.routp.k8s.zdt;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import org.eclipse.microprofile.health.HealthCheckResponse;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.media.jsonp.server.JsonSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

/**
 * @author prarout
 */
public final class Main {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static volatile AtomicInteger atomicInt = new AtomicInteger(0);
    private static final String ENV_APP_VERSION = System.getenv("APP_VERSION");
    private static final String APP_VERSION = ENV_APP_VERSION != null ? ENV_APP_VERSION : "v1.0.0";
    private static final String HOST_NAME = getLocalHostName();
    private static WebServer webServer;

    private Main() {
    }

    private static Routing createRouting() {
        return Routing.builder()
                .register(JsonSupport.create())
                .register(HealthSupport.builder()
                        .addLiveness(HealthChecks.healthChecks())
                        .addReadiness(() -> HealthCheckResponse
                                .named(HOST_NAME)
                                .up()
                                .withData("time", new Date().toString())
                                .withData("timeInMs", System.currentTimeMillis())
                                .build())
                        .build())
                .register("/api", rules -> rules.get("/hello", (req, res) -> {
                    System.out.println("Request Headers: " + req.headers().toMap());
                    final int reqCount = atomicInt.incrementAndGet();
                    JsonObject returnObject = JSON.createObjectBuilder()
                            .add("App Version", APP_VERSION)
                            .add("Request Count", reqCount)
                            .add("Container Name", HOST_NAME)
                            .build();
                    System.out.println("Response: " + returnObject.toString());
                    res.status(Http.Status.OK_200).headers().add(Http.Header.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON.toString());
                    res.send(returnObject);
                }))
                .build();
    }

    public static void main(final String[] args) {
        // Start the server
        startServer();
        // register shutdown hook to do a graceful shutdown. This hook would react to SIGTERM
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stopServer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }

    /**
     * Starts the server.
     *
     * @return {@link WebServer} object
     */
    private static WebServer startServer() {
        System.out.println("App Version: " + APP_VERSION);
        Config config = Config.create();
        ServerConfiguration serverConfig = ServerConfiguration.create(config.get("server"));
        webServer = WebServer.create(serverConfig, createRouting());
        webServer.start()
                .thenAccept(ws -> {
                    System.out.println("WEB server is up! http://localhost:" + ws.port());
                    ws.whenShutdown().thenRun(() -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionally(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                    return null;
                });
        return webServer;

    }

    private static void stopServer() throws Exception {
        if (webServer != null) {
            System.out.println("Stopping the web server...");
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    private static String getLocalHostName() {
        try {
            InetAddress inetAddr = InetAddress.getLocalHost();
            return inetAddr.getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }
}

