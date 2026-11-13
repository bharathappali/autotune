package com.autotune.http;

import com.autotune.operator.KruizeDeploymentInfo;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.pgclient.SslMode;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non-blocking pool against the kruize Postgres that is already deployed.
 * No pooler, extension, or extra database is added.
 */
public final class KruizePg {
    private static final Logger LOGGER = LoggerFactory.getLogger(KruizePg.class);

    private KruizePg() {
    }

    public static PgPool create(Vertx vertx, int eventLoopSize) {
        String host = KruizeDeploymentInfo.database_hostname;
        String db = KruizeDeploymentInfo.database_dbname;
        String user = KruizeDeploymentInfo.database_username;
        String password = KruizeDeploymentInfo.database_password;
        int port = 5432;
        try {
            if (KruizeDeploymentInfo.database_port != null && !KruizeDeploymentInfo.database_port.isBlank()) {
                port = Integer.parseInt(KruizeDeploymentInfo.database_port.trim());
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid database port '{}', using 5432", KruizeDeploymentInfo.database_port);
        }
        int poolSize = positiveEnv("KRUIZE_PG_POOL_SIZE", Math.min(Math.max(eventLoopSize, 2), 8));
        PgConnectOptions connect = new PgConnectOptions()
                .setHost(host == null ? "localhost" : host)
                .setPort(port)
                .setDatabase(db == null ? "kruizeDB" : db)
                .setUser(user == null ? "admin" : user)
                .setPassword(password == null ? "" : password)
                .setCachePreparedStatements(true)
                .setPreparedStatementCacheMaxSize(256)
                .setPreparedStatementCacheSqlLimit(4096)
                .setPipeliningLimit(256)
                .setReconnectAttempts(5)
                .setReconnectInterval(1000)
                .addProperty("TimeZone", "UTC");
        String ssl = KruizeDeploymentInfo.database_ssl_mode;
        if (ssl != null && (ssl.equalsIgnoreCase("require") || ssl.equalsIgnoreCase("verify-ca") || ssl.equalsIgnoreCase("verify-full"))) {
            connect.setSslMode(SslMode.REQUIRE);
        }
        PoolOptions pool = new PoolOptions().setMaxSize(poolSize);
        LOGGER.info("Reactive Postgres pool size {} against {}:{}", poolSize, connect.getHost(), connect.getPort());
        return PgPool.pool(vertx, connect, pool);
    }

    public static int positiveEnv(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
