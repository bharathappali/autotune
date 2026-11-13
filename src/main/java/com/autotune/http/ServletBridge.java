package com.autotune.http;

import com.autotune.analyzer.adapters.RecommendationItemAdapter;
import com.autotune.http.results.ExperimentCache;
import com.autotune.utils.ServerContext;
import com.autotune.analyzer.exceptions.KruizeResponse;
import com.autotune.analyzer.utils.AnalyzerConstants;
import com.autotune.analyzer.utils.GsonUTCDateAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.ee8.servlet.ServletMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ReadListener;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the existing servlets on a worker so cold routes keep their behavior.
 * The hot-path POSTs do not come through here.
 */
public final class ServletBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServletBridge.class);
    private static final Gson ERROR_JSON = new GsonBuilder()
            .disableHtmlEscaping()
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
            .registerTypeAdapter(AnalyzerConstants.RecommendationItem.class, new RecommendationItemAdapter())
            .create();

    private final Vertx vertx;
    private final WorkerExecutor workers;
    private final ServletContextHandler context;
    private final ServletContext servletContext;
    private final Map<String, Servlet> servlets = new ConcurrentHashMap<>();

    public ServletBridge(Vertx vertx, WorkerExecutor workers, ServletContextHandler context) {
        this.vertx = vertx;
        this.workers = workers;
        this.context = context;
        this.servletContext = context.getServletContext();
    }

    public void handle(RoutingContext ctx) {
        String path = ctx.normalizedPath();
        String method = ctx.request().method().name();
        String query = ctx.request().query();
        List<Map.Entry<String, String>> headers = new java.util.ArrayList<>(ctx.request().headers().entries());
        Buffer body = ctx.body() == null ? Buffer.buffer() : ctx.body().buffer();
        workers.executeBlocking(promise -> {
            try {
                promise.complete(invoke(path, method, query, headers, body));
            } catch (Exception e) {
                promise.fail(e);
            }
        }, false).onSuccess(result -> {
            if (dropsCache(path, method)) {
                ExperimentCache.invalidateAll();
            }
            ServletResult servletResult = (ServletResult) result;
            ctx.response().setStatusCode(servletResult.status);
            servletResult.headers.forEach((name, value) -> {
                if (value != null) {
                    ctx.response().putHeader(name, value);
                }
            });
            if (!ctx.response().headers().contains("Content-Type")) {
                ctx.response().putHeader("Content-Type", "application/json;charset=UTF-8");
            }
            ctx.response().end(Buffer.buffer(servletResult.body));
        }).onFailure(err -> {
            LOGGER.error("Cold route {} failed: {}", path, err.toString());
            ctx.response().setStatusCode(500).end("{\"message\":\"" + escape(err.getMessage()) + "\",\"httpcode\":500,\"documentationLink\":\"\",\"status\":\"ERROR\"}");
        });
    }

    private static boolean dropsCache(String path, String method) {
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return false;
        }
        return ServerContext.CREATE_EXPERIMENT.equals(path)
                || path.contains("PerformanceProfile")
                || path.contains("MetricProfile")
                || path.contains("MetadataProfile")
                || ServerContext.BULK_SERVICE.equals(path)
                || ServerContext.BULK_CONFIG_SERVICE.equals(path);
    }

    private ServletResult invoke(String path, String method, String query, List<Map.Entry<String, String>> headers, Buffer body) throws Exception {
        Servlet servlet = servletFor(path);
        if (servlet == null) {
            return new ServletResult(404, Map.of("Content-Type", "application/json"),
                    "{\"message\":\"Not Found\",\"httpcode\":404,\"documentationLink\":\"\",\"status\":\"ERROR\"}".getBytes(StandardCharsets.UTF_8));
        }
        BridgeState state = new BridgeState(servletContext, method, path, query, headers, body == null ? "" : body.toString(StandardCharsets.UTF_8));
        HttpServletRequest request = proxy(HttpServletRequest.class, state.requestHandler());
        HttpServletResponse response = proxy(HttpServletResponse.class, state.responseHandler());
        servlet.service(request, response);
        return state.toResult();
    }

    private Servlet servletFor(String path) throws Exception {
        Servlet cached = servlets.get(path);
        if (cached != null) {
            return cached;
        }
        ServletHolder holder = null;
        ServletMapping[] mappings = context.getServletHandler().getServletMappings();
        if (mappings != null) {
            for (ServletMapping mapping : mappings) {
                String[] specs = mapping.getPathSpecs();
                if (specs == null) {
                    continue;
                }
                for (String spec : specs) {
                    if (path.equals(spec)) {
                        holder = context.getServletHandler().getServlet(mapping.getServletName());
                        break;
                    }
                }
                if (holder != null) {
                    break;
                }
            }
        }
        if (holder == null) {
            return null;
        }
        Servlet servlet = holder.getServlet();
        servlets.put(path, servlet);
        return servlet;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ServletResult(int status, Map<String, String> headers, byte[] body) {
    }

    private static final class BridgeState {
        private final ServletContext servletContext;
        private final String method;
        private final String path;
        private final String query;
        private final Map<String, String> queryParams = new HashMap<>();
        private final Map<String, String> headers;
        private final String body;
        private final Map<String, Object> attributes = new HashMap<>();
        private final StringWriter writer = new StringWriter();
        private final PrintWriter printWriter = new PrintWriter(writer);
        private final Map<String, String> responseHeaders = new HashMap<>();
        private int status = 200;
        private boolean errorSent;

        private BridgeState(ServletContext servletContext, String method, String path, String query, List<Map.Entry<String, String>> headerEntries, String body) {
            this.servletContext = servletContext;
            this.method = method;
            this.path = path;
            this.query = query == null ? "" : query;
            this.body = body == null ? "" : body;
            this.headers = new HashMap<>();
            for (Map.Entry<String, String> entry : headerEntries) {
                this.headers.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            if (!this.query.isEmpty()) {
                for (String pair : this.query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq < 0) {
                        queryParams.put(decode(pair), "");
                    } else {
                        queryParams.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
                    }
                }
            }
        }

        private InvocationHandler requestHandler() {
            return (proxy, method, args) -> {
                String name = method.getName();
                return switch (name) {
                    case "getMethod" -> this.method;
                    case "getRequestURI", "getServletPath" -> this.path;
                    case "getQueryString" -> this.query.isEmpty() ? null : this.query;
                    case "getParameter" -> queryParams.get((String) args[0]);
                    case "getParameterMap" -> {
                        Map<String, String[]> map = new HashMap<>();
                        queryParams.forEach((key, value) -> map.put(key, new String[]{value}));
                        yield map;
                    }
                    case "getParameterNames" -> Collections.enumeration(queryParams.keySet());
                    case "getParameterValues" -> {
                        String value = queryParams.get((String) args[0]);
                        yield value == null ? null : new String[]{value};
                    }
                    case "getReader" -> new BufferedReader(new StringReader(this.body));
                    case "getInputStream" -> new BytesServletInputStream(this.body.getBytes(StandardCharsets.UTF_8));
                    case "getContentLength" -> this.body.getBytes(StandardCharsets.UTF_8).length;
                    case "getContentLengthLong" -> (long) this.body.getBytes(StandardCharsets.UTF_8).length;
                    case "getContentType" -> headers.get("content-type");
                    case "getCharacterEncoding" -> "UTF-8";
                    case "setCharacterEncoding" -> null;
                    case "getHeader" -> headers.get(String.valueOf(args[0]).toLowerCase());
                    case "getHeaders" -> Collections.enumeration(List.of(headers.getOrDefault(String.valueOf(args[0]).toLowerCase(), "")));
                    case "getHeaderNames" -> Collections.enumeration(headers.keySet());
                    case "getAttribute" -> attributes.get(args[0]);
                    case "setAttribute" -> {
                        attributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "removeAttribute" -> {
                        attributes.remove(args[0]);
                        yield null;
                    }
                    case "getAttributeNames" -> Collections.enumeration(attributes.keySet());
                    case "getProtocol" -> "HTTP/1.1";
                    case "getScheme" -> "http";
                    case "getServerName" -> "localhost";
                    case "getServerPort", "getLocalPort", "getRemotePort" -> 8080;
                    case "getRemoteAddr", "getLocalAddr" -> "127.0.0.1";
                    case "getRemoteHost", "getLocalName" -> "localhost";
                    case "getContextPath" -> "";
                    case "getPathInfo" -> null;
                    case "isSecure" -> false;
                    case "getLocale" -> java.util.Locale.ROOT;
                    case "getLocales" -> Collections.enumeration(List.of(java.util.Locale.ROOT));
                    case "getCookies" -> new javax.servlet.http.Cookie[0];
                    case "getIntHeader" -> -1;
                    case "getDateHeader" -> -1L;
                    case "getServletContext" -> servletContext;
                    case "startAsync", "isAsyncStarted", "isAsyncSupported" -> name.equals("isAsyncSupported");
                    case "getDispatcherType" -> javax.servlet.DispatcherType.REQUEST;
                    case "isRequestedSessionIdValid", "isRequestedSessionIdFromCookie", "isRequestedSessionIdFromURL", "isRequestedSessionIdFromUrl" -> false;
                    default -> defaultValue(method);
                };
            };
        }

        private InvocationHandler responseHandler() {
            return (proxy, method, args) -> {
                String name = method.getName();
                return switch (name) {
                    case "setStatus" -> {
                        if (args.length == 1) {
                            status = (Integer) args[0];
                        }
                        yield null;
                    }
                    case "getStatus" -> status;
                    case "sendError" -> {
                        status = (Integer) args[0];
                        String message = args.length > 1 && args[1] instanceof String text ? text : "";
                        attributes.put("javax.servlet.error.message", message);
                        writer.getBuffer().setLength(0);
                        printWriter.write(ERROR_JSON.toJson(new KruizeResponse(message, status, "", "ERROR",
                                (List) attributes.get("data"))));
                        errorSent = true;
                        yield null;
                    }
                    case "setContentType" -> {
                        responseHeaders.put("Content-Type", String.valueOf(args[0]));
                        yield null;
                    }
                    case "setCharacterEncoding" -> null;
                    case "setHeader", "addHeader" -> {
                        responseHeaders.put(String.valueOf(args[0]), String.valueOf(args[1]));
                        yield null;
                    }
                    case "setIntHeader", "addIntHeader" -> {
                        responseHeaders.put(String.valueOf(args[0]), String.valueOf(args[1]));
                        yield null;
                    }
                    case "getWriter" -> printWriter;
                    case "getOutputStream" -> new WriterServletOutputStream(writer);
                    case "flushBuffer" -> {
                        printWriter.flush();
                        yield null;
                    }
                    case "isCommitted" -> errorSent;
                    case "reset", "resetBuffer" -> {
                        writer.getBuffer().setLength(0);
                        yield null;
                    }
                    case "setContentLength", "setContentLengthLong" -> null;
                    case "encodeURL", "encodeRedirectURL", "encodeUrl", "encodeRedirectUrl" -> args[0];
                    case "containsHeader" -> responseHeaders.containsKey(args[0]);
                    case "getHeader" -> responseHeaders.get(args[0]);
                    case "getHeaderNames" -> Collections.enumeration(responseHeaders.keySet());
                    case "getHeaders" -> Collections.enumeration(List.of(responseHeaders.getOrDefault(args[0], "")));
                    case "getCharacterEncoding" -> "UTF-8";
                    case "getContentType" -> responseHeaders.get("Content-Type");
                    case "getLocale" -> java.util.Locale.ROOT;
                    case "setLocale" -> null;
                    default -> defaultValue(method);
                };
            };
        }

        private ServletResult toResult() {
            printWriter.flush();
            byte[] bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
            return new ServletResult(status, responseHeaders, bytes);
        }

        private static Object defaultValue(Method method) {
            Class<?> type = method.getReturnType();
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return -1;
            }
            if (type == long.class) {
                return -1L;
            }
            if (type == void.class) {
                return null;
            }
            if (Enumeration.class.isAssignableFrom(type)) {
                return Collections.emptyEnumeration();
            }
            return null;
        }

        private static String decode(String value) {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

    }
}

final class BytesServletInputStream extends ServletInputStream {
    private final ByteArrayInputStream input;

    BytesServletInputStream(byte[] bytes) {
        this.input = new ByteArrayInputStream(bytes);
    }

    @Override
    public boolean isFinished() {
        return input.available() == 0;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
    }

    @Override
    public int read() {
        return input.read();
    }
}

final class WriterServletOutputStream extends ServletOutputStream {
    private final StringWriter writer;

    WriterServletOutputStream(StringWriter writer) {
        this.writer = writer;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
    }

    @Override
    public void write(int b) throws IOException {
        writer.write(b);
    }
}
