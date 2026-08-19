package com.chua.starter.ngrok.support;

import com.chua.ngrok.support.NgrokClient;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import com.ngrok.Http;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Ngrok 自动配置：Spring Boot 启动后自动建立 ngrok 隧道，将本机服务暴露到公网。
 *
 * <p>触发时机：{@link ApplicationReadyEvent}（此时 {@code server.port} 已确定）
 * → 建立 Session → 启动 HTTP 转发隧道。</p>
 *
 * <p>关闭时机：{@link ContextClosedEvent} → 关闭 Session。</p>
 *
 * <p>必填项：{@code plugin.ngrok.authtoken} 或环境变量 {@code NGROK_AUTHTOKEN}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(NgrokClient.class)
@ConditionalOnProperty(prefix = "plugin.ngrok", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(NgrokProperties.class)
public class NgrokAutoConfiguration {

    /**
     * ngrok authtoken 环境变量名
     */
    private static final String ENV_AUTHTOKEN = "NGROK_AUTHTOKEN";

    /**
     * 解析后的 authtoken（带环境变量回退）
     */
    private final NgrokProperties properties;

    /**
     * 资源加载器，用于读取 CA 证书
     */
    private final ResourceLoader resourceLoader;

    /**
     * Ngrok Client 实例
     */
    private NgrokClient client;

    public NgrokAutoConfiguration(NgrokProperties properties, ObjectProvider<ResourceLoader> resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader.getIfAvailable();
    }

    /**
     * 在应用就绪后建立 ngrok 隧道。
     *
     * <p>使用 {@link ApplicationReadyEvent} 而非 WebServerInitializedEvent
     * （后者在 Spring Boot 4.x 已移除），应用就绪时 server.port 已确定。</p>
     *
     * @param event 应用就绪事件
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (client != null) {
            return;
        }
        int localPort = resolveLocalPort(event);
        if (localPort <= 0) {
            log.warn("[Ngrok] 无法解析本地 server.port，跳过隧道建立");
            return;
        }
        try {
            startTunnels(localPort);
        } catch (Exception e) {
            handleStartupFailure(e);
        }
    }

    /**
     * Spring 上下文关闭时关闭 ngrok。
     *
     * @param event 上下文关闭事件
     */
    @EventListener
    public void onContextClosed(ContextClosedEvent event) {
        closeClient();
    }

    /**
     * bean 销毁兜底关闭（保证 try-with-resources 风格的容器关闭）。
     */
    @PreDestroy
    public void destroy() {
        closeClient();
    }

    /**
     * 解析本地端口（处理 {@code server.port=0} 随机端口场景）。
     *
     * @param event 应用就绪事件
     */
    private int resolveLocalPort(ApplicationReadyEvent event) {
        // 优先从 server.port 属性解析
        String portStr = event.getApplicationContext().getEnvironment().getProperty("server.port");
        if (portStr != null && !portStr.isBlank() && !"0".equals(portStr)) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    /**
     * 启动隧道。
     */
    private void startTunnels(int localPort) throws IOException {
        String authtoken = resolveAuthtoken();
        if (authtoken == null) {
            throw new IllegalStateException(
                    "[Ngrok] authtoken 未配置：请设置 plugin.ngrok.authtoken 或环境变量 NGROK_AUTHTOKEN");
        }

        client = buildClient(authtoken);
        client.connect();

        String type = normalize(properties.getType(), "http");
        String mode = normalize(properties.getMode(), "forward");

        switch (type) {
            case "tcp" -> startTcpTunnels();
            case "tls" -> startTlsTunnels(localPort, mode);
            case "http" -> startHttpTunnels(localPort, mode);
            default -> throw new IllegalStateException("[Ngrok] 不支持的 type: " + type);
        }

        logPublicUrls();
    }

    /**
     * 启动 HTTP 转发 / 监听。
     */
    private void startHttpTunnels(int localPort, String mode) throws IOException {
        boolean forward = "forward".equalsIgnoreCase(mode);

        // 主端口：server.port
        startHttpTunnel(localPort, "main", forward);

        // 额外端口
        String extra = properties.getExtraLocalPorts();
        if (StringUtils.hasText(extra)) {
            String[] ports = extra.split(",");
            for (String portStr : ports) {
                String trimmed = portStr.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    int port = Integer.parseInt(trimmed);
                    startHttpTunnel(port, "extra-" + port, forward);
                } catch (NumberFormatException e) {
                    log.warn("[Ngrok] 忽略非法端口: {}", trimmed);
                }
            }
        }
    }

    private void startHttpTunnel(int localPort, String tag, boolean forward) throws IOException {
        NgrokClient.HttpBuilderStage stage = client.http()
                .metadata(buildMetadata("http-" + tag));

        if (StringUtils.hasText(properties.getDomain())) {
            stage.domain(properties.getDomain());
        }
        if (StringUtils.hasText(properties.getSubdomain())) {
            log.warn("[Ngrok] subdomain 字段已在新版 ngrok 套餐下线，请改用 domain");
        }
        if ("HTTPS".equalsIgnoreCase(properties.getScheme())) {
            stage.scheme(Http.Scheme.HTTPS);
        } else if ("HTTP".equalsIgnoreCase(properties.getScheme())) {
            stage.scheme(Http.Scheme.HTTP);
        }
        if (properties.isCompression()) {
            stage.compression();
        }
        applyBasicAuth(stage);

        if (forward) {
            URL target = new URL("http://127.0.0.1:" + localPort);
            client.forwardHttp(stage.builder(), target);
        } else {
            ListenerHandle holder = new ListenerHandle();
            holder.listen = true;
            client.listenHttp(stage.builder());
        }
    }

    /**
     * 启动 TLS 转发 / 监听。
     */
    private void startTlsTunnels(int localPort, String mode) throws IOException {
        boolean forward = "forward".equalsIgnoreCase(mode);
        NgrokClient.TlsBuilderStage stage = client.tls()
                .metadata(buildMetadata("tls"));
        if (StringUtils.hasText(properties.getDomain())) {
            stage.domain(properties.getDomain());
        }
        if (forward) {
            client.forwardTls(stage.builder(), new URL("http://127.0.0.1:" + localPort));
        } else {
            client.listenTls(stage.builder());
        }
    }

    /**
     * 启动 TCP 监听。
     */
    private void startTcpTunnels() throws IOException {
        if (!StringUtils.hasText(properties.getRemoteAddress())) {
            throw new IllegalStateException("[Ngrok] tcp 模式必须配置 plugin.ngrok.remote-address");
        }
        client.listenTcp(client.tcp()
                .metadata(buildMetadata("tcp"))
                .remoteAddress(properties.getRemoteAddress())
                .builder());
    }

    /**
     * 应用 BasicAuth 配置。
     */
    private void applyBasicAuth(NgrokClient.HttpBuilderStage stage) {
        String user = null;
        String pass = null;
        if (StringUtils.hasText(properties.getBasicAuth())) {
            String[] parts = properties.getBasicAuth().split(":", 2);
            user = parts[0];
            pass = parts.length > 1 ? parts[1] : "";
        } else if (StringUtils.hasText(properties.getBasicAuthUsername())) {
            user = properties.getBasicAuthUsername();
            pass = properties.getBasicAuthPassword();
        }
        if (StringUtils.hasText(user)) {
            stage.basicAuthOptions(new Http.BasicAuth(user, pass == null ? "" : pass));
        }
    }

    /**
     * 构建 NgrokClient，注入所有可选项。
     */
    private NgrokClient buildClient(String authtoken) throws IOException {
        NgrokClient c = NgrokClient.create(authtoken);
        if (StringUtils.hasText(properties.getMetadata())) {
            c.metadata(properties.getMetadata());
        }
        if (StringUtils.hasText(properties.getServerAddr())) {
            c.serverAddr(properties.getServerAddr());
        }
        if (StringUtils.hasText(properties.getCaCert())) {
            c.caCert(loadCaCert(properties.getCaCert()));
        }
        if (properties.getHeartbeatInterval() != null) {
            c.heartbeatInterval(properties.getHeartbeatInterval());
        }
        if (properties.getHeartbeatTolerance() != null) {
            c.heartbeatTolerance(properties.getHeartbeatTolerance());
        }
        return c;
    }

    /**
     * 解析 authtoken（优先配置，其次环境变量）。
     */
    private String resolveAuthtoken() {
        if (StringUtils.hasText(properties.getAuthtoken())) {
            return properties.getAuthtoken().trim();
        }
        String env = System.getenv(ENV_AUTHTOKEN);
        if (StringUtils.hasText(env)) {
            return env.trim();
        }
        String sys = System.getProperty(ENV_AUTHTOKEN);
        if (StringUtils.hasText(sys)) {
            return sys.trim();
        }
        return null;
    }

    /**
     * 加载 classpath 资源形式的 CA 证书。
     */
    private byte[] loadCaCert(String location) throws IOException {
        if (resourceLoader == null) {
            throw new IllegalStateException("[Ngrok] ca-cert 配置需要 ResourceLoader，但当前环境未提供");
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("[Ngrok] CA 证书资源不存在: " + location);
        }
        try {
            return resource.getInputStream().readAllBytes();
        } catch (IOException e) {
            Path p = Path.of(location.replaceFirst("^classpath:", ""));
            if (Files.exists(p)) {
                return Files.readAllBytes(p);
            }
            throw e;
        }
    }

    /**
     * 构造 metadata。
     */
    private String buildMetadata(String suffix) {
        String base = StringUtils.hasText(properties.getMetadata())
                ? properties.getMetadata()
                : "spring-support-ngrok";
        return base + "/" + suffix;
    }

    /**
     * 输出公网 URL banner。
     */
    private void logPublicUrls() {
        if (client == null) {
            return;
        }
        List<String> urls = client.getUrls();
        if (urls.isEmpty()) {
            return;
        }
        for (String url : urls) {
            com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry.getInstance()
                    .registerEndpoint("公网隧道", "ngrok", url, "Ngrok 公网隧道");
        }
        log.info("");
        log.info("=========================================================");
        log.info("  Ngrok 隧道已建立，本机服务已暴露到公网");
        log.info("=========================================================");
        for (String url : urls) {
            log.info("  🌐 {}", url);
        }
        log.info("=========================================================");
        log.info("");
    }

    /**
     * 启动失败处理（根据 failFast 决定行为）。
     */
    private void handleStartupFailure(Exception e) {
        log.error("[Ngrok] 隧道启动失败: {}", e.getMessage(), e);
        closeClient();
        if (properties.isFailFast()) {
            throw new IllegalStateException("[Ngrok] failFast=true，终止 Spring 启动", e);
        }
    }

    /**
     * 关闭 Ngrok 客户端。
     */
    private void closeClient() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("[Ngrok] 关闭客户端异常: {}", e.getMessage());
            }
            client = null;
        }
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * Listener 句柄占位（保留扩展点；当前由 NgrokClient 内部持有所有 listener）。
     */
    private static final class ListenerHandle {
        @SuppressWarnings("unused")
        boolean listen;
    }

    @Bean
    public ServiceInfoContributor ngrokServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("ngrok", "内网穿透", true, "ngrok隧道模块");
        };
    }
}
