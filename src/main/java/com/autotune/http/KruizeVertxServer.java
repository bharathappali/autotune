package com.autotune.http;

import com.autotune.http.reco.UpdateRecommendationsHandler;
import com.autotune.http.results.UpdateResultsHandler;
import com.autotune.utils.MetricsConfig;
import com.autotune.utils.ServerContext;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP entry. Hot POSTs stay on the event loop. Every other route runs the existing servlet on a worker.
 */
public final class KruizeVertxServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(KruizeVertxServer.class);

    private KruizeVertxServer() {
    }

    public static void start(ServletContextHandler context) {
        int eventLoops = KruizePg.positiveEnv("KRUIZE_EVENT_LOOP_SIZE", Math.max(2, Runtime.getRuntime().availableProcessors()));
        int workers = KruizePg.positiveEnv("KRUIZE_WORKER_POOL_COUNT", 4);
        VertxOptions options = new VertxOptions()
                .setEventLoopPoolSize(eventLoops)
                .setWorkerPoolSize(workers)
                .setPreferNativeTransport(true);
        Vertx vertx = Vertx.vertx(options);
        if (!vertx.isNativeTransportEnabled()) {
            LOGGER.info("Native transport unavailable, using NIO");
        }
        WorkerExecutor executor = vertx.createSharedWorkerExecutor("kruize-blocking", workers);
        var pool = KruizePg.create(vertx, eventLoops);
        ServletBridge cold = new ServletBridge(vertx, executor, context);
        UpdateResultsHandler results = new UpdateResultsHandler(pool);
        UpdateRecommendationsHandler recommendations = new UpdateRecommendationsHandler(pool, executor, cold, false);
        UpdateRecommendationsHandler recommendationsV1 = new UpdateRecommendationsHandler(pool, executor, cold, true);

        Router router = Router.router(vertx);
        router.route().handler(CorsHandler.create()
                .addRelativeOrigin(".*")
                .allowedMethods(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.OPTIONS))
                .allowedHeader("*"));
        router.route().handler(BodyHandler.create().setBodyLimit(64L * 1024 * 1024));
        router.get(ServerContext.HEALTH_SERVICE).handler(ctx ->
                ctx.response().setStatusCode(200).putHeader("Content-Type", "text/plain;charset=UTF-8").end("UP\n"));
        router.get(ServerContext.METRICS_SERVICE).handler(ctx -> {
            String scrape = MetricsConfig.meterRegistry() == null ? "" : MetricsConfig.meterRegistry().scrape();
            ctx.response().setStatusCode(200).putHeader("Content-Type", "text/plain; version=0.0.4; charset=utf-8").end(scrape);
        });
        router.post(ServerContext.UPDATE_RESULTS).handler(results::handle);
        router.post(ServerContext.UPDATE_RECOMMENDATIONS).handler(recommendations::handle);
        router.post(ServerContext.RECOMMENDATIONS).handler(recommendationsV1::handle);
        router.route().handler(cold::handle);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        HttpServerOptions http = new HttpServerOptions()
                .setCompressionSupported(false)
                .setMaxFormAttributeSize(32 * 1024 * 1024)
                .setMaxFormFields(1024);
        vertx.createHttpServer(http)
                .requestHandler(router)
                .listen(ServerContext.KRUIZE_SERVER_PORT)
                .onComplete(result -> {
                    if (result.failed()) {
                        failure.set(result.cause());
                    } else {
                        LOGGER.info("Kruize listening on {}", ServerContext.KRUIZE_SERVER_PORT);
                    }
                    latch.countDown();
                });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while binding the HTTP port", e);
        }
        if (failure.get() != null) {
            LOGGER.error("Could not start the server", failure.get());
            System.exit(1);
        }
    }
}
