package com.chua.starter.ngrok.support;

import com.chua.ngrok.support.NgrokClient;
import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.tunnel.spi.TunnelProvider;
import com.chua.starter.tunnel.spi.TunnelSession;
import com.chua.starter.tunnel.support.TunnelInputs;
import com.ngrok.Http;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ngrok 提供方实现：把「本机端口 → ngrok 公网地址」接入统一入口。
 *
 * <p>改造前的 {@code NgrokTunnelService} 同时承担了编排（增删改查、持久化、
 * 启动恢复）与厂商细节（建客户端、开隧道），与 cloudflared 侧重复了约九成代码。
 * 现在编排逻辑上移到 {@code spring-support-tunnel-starter} 的 {@code TunnelService}，
 * 本类只保留「ngrok 特有」的部分：</p>
 * <ul>
 *   <li>凭据解析优先级：映射记录 → {@code plugin.ngrok.authtoken} → 环境变量；</li>
 *   <li>模式与协议映射：{@code http} / {@code tcp} / {@code tls}；</li>
 *   <li>私有化部署选项：{@code server-addr}、CA 证书、心跳参数。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
public class NgrokTunnelProvider implements TunnelProvider {

    /**
     * 提供方标识。
     */
    public static final String NAME = "ngrok";

    /**
     * authtoken 环境变量名。
     */
    private static final String ENV_AUTHTOKEN = "NGROK_AUTHTOKEN";

    /**
     * 支持的模式。
     */
    private static final List<String> MODES = List.of("http", "tcp", "tls");

    /**
     * 全局配置。
     */
    private final NgrokProperties properties;

    /**
     * 资源加载器（读取私有部署 CA 证书）。
     */
    private final ResourceLoader resourceLoader;

    /**
     * 创建提供方。
     *
     * @param properties     全局配置
     * @param resourceLoader 资源加载器，可为空
     */
    public NgrokTunnelProvider(NgrokProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String displayName() {
        return "ngrok";
    }

    /**
     * 客户端是编译期依赖，能加载到 {@link NgrokClient} 即可用。
     *
     * @return 恒为 true
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<String> supportedModes() {
        return MODES;
    }

    @Override
    public String defaultMode() {
        return "http";
    }

    /**
     * 沿用既有配置 {@code plugin.ngrok.store-dir}，保证升级后旧映射仍被识别。
     *
     * @return 目录或 null
     */
    @Override
    public String storeDir() {
        return properties.getStoreDir();
    }

    @Override
    public boolean autoStartStored() {
        return properties.isAutoStartStored();
    }

    @Override
    public Map<String, Object> capabilities() {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("provider", NAME);
        caps.put("authtokenConfigured", resolveAuthtoken(null) != null);
        caps.put("autoStart", properties.isAutoStart());
        caps.put("autoStartStored", properties.isAutoStartStored());
        caps.put("serverAddr", StringUtils.hasText(properties.getServerAddr())
                ? properties.getServerAddr().trim() : "ngrok 官方服务");
        return caps;
    }

    @Override
    public void applyInput(TunnelRecord target, TunnelRecord input) {
        if (input == null) {
            throw new IllegalArgumentException("映射配置不能为空");
        }
        String mode = TunnelInputs.normalize(input.getMode(), defaultMode()).toLowerCase();
        TunnelInputs.requireMode(mode, MODES, "ngrok");
        if ("tcp".equals(mode) && !StringUtils.hasText(input.getRemoteAddress())) {
            throw new IllegalArgumentException("tcp 模式必须填写远程地址");
        }
        target.setMode(mode);
        target.setDomain(TunnelInputs.trimToNull(input.getDomain()));
        target.setRemoteAddress(TunnelInputs.trimToNull(input.getRemoteAddress()));
        target.setBasicAuth(TunnelInputs.trimToNull(input.getBasicAuth()));
        target.setAuthtoken(TunnelInputs.trimToNull(input.getAuthtoken()));
        target.setMetadata(TunnelInputs.trimToNull(input.getMetadata()));
        // cloudflared 专属字段在 ngrok 记录上必须清空，避免两套配置互相污染。
        target.setToken(null);
        TunnelInputs.applyCommon(target, input, NAME);
    }

    @Override
    public TunnelSession open(TunnelRecord record) {
        NgrokClient client = null;
        try {
            client = buildClient(record);
            client.connect();
            openTunnel(client, record);
            return new NgrokSession(client);
        } catch (RuntimeException e) {
            closeQuietly(client);
            throw e;
        } catch (IOException | UncheckedIOException e) {
            closeQuietly(client);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * ngrok 会话：持有一个已连接的 {@link NgrokClient}。
     */
    private static final class NgrokSession implements TunnelSession {

        /**
         * 客户端。
         */
        private final NgrokClient client;

        /**
         * 关闭标记（保证 close 幂等）。
         */
        private boolean closed;

        /**
         * 创建会话。
         *
         * @param client 客户端
         */
        private NgrokSession(NgrokClient client) {
            this.client = client;
        }

        @Override
        public List<String> publicUrls() {
            List<String> urls = client.getUrls();
            return urls == null ? List.of() : urls;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                client.close();
            } catch (Exception e) {
                log.warn("[Ngrok] 关闭客户端异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 按记录构建客户端（凭据优先级：记录 → 全局配置 → 环境变量）。
     *
     * @param record 映射记录
     * @return 客户端
     */
    private NgrokClient buildClient(TunnelRecord record) {
        String authtoken = resolveAuthtoken(record);
        if (authtoken == null) {
            throw new IllegalStateException("authtoken 未配置：请在映射中填写，或设置 plugin.ngrok.authtoken / 环境变量 "
                    + ENV_AUTHTOKEN);
        }
        NgrokClient client = NgrokClient.create(authtoken);
        String metadata = StringUtils.hasText(record.getMetadata())
                ? record.getMetadata().trim()
                : properties.getMetadata();
        if (StringUtils.hasText(metadata)) {
            client.metadata(metadata);
        }
        if (StringUtils.hasText(properties.getServerAddr())) {
            client.serverAddr(properties.getServerAddr().trim());
        }
        if (StringUtils.hasText(properties.getCaCert())) {
            client.caCert(loadCaCert(properties.getCaCert()));
        }
        if (properties.getHeartbeatInterval() != null) {
            client.heartbeatInterval(properties.getHeartbeatInterval());
        }
        if (properties.getHeartbeatTolerance() != null) {
            client.heartbeatTolerance(properties.getHeartbeatTolerance());
        }
        return client;
    }

    /**
     * 按模式打开隧道：http / tcp / tls。
     *
     * @param client 已连接的客户端
     * @param record 映射记录
     */
    private void openTunnel(NgrokClient client, TunnelRecord record) {
        String mode = TunnelInputs.normalize(record.getMode(), defaultMode());
        String metadata = buildTunnelMetadata(record, mode);
        switch (mode) {
            case "tcp" -> {
                if (!StringUtils.hasText(record.getRemoteAddress())) {
                    throw new IllegalStateException("tcp 模式必须填写远程地址（形如 1.tcp.ngrok.io:20000）");
                }
                client.listenTcp(client.tcp()
                        .metadata(metadata)
                        .remoteAddress(record.getRemoteAddress().trim())
                        .builder());
            }
            case "tls" -> {
                NgrokClient.TlsBuilderStage stage = client.tls().metadata(metadata);
                if (StringUtils.hasText(record.getDomain())) {
                    stage.domain(record.getDomain().trim());
                }
                client.forwardTls(stage.builder(), localUrl(record.getLocalPort()));
            }
            default -> {
                NgrokClient.HttpBuilderStage stage = client.http().metadata(metadata);
                if (StringUtils.hasText(record.getDomain())) {
                    stage.domain(record.getDomain().trim());
                }
                stage.scheme(Http.Scheme.HTTPS);
                if (StringUtils.hasText(record.getBasicAuth())) {
                    String[] parts = record.getBasicAuth().split(":", 2);
                    stage.basicAuthOptions(new Http.BasicAuth(parts[0], parts.length > 1 ? parts[1] : ""));
                }
                client.forwardHttp(stage.builder(), localUrl(record.getLocalPort()));
            }
        }
    }

    /**
     * 本地转发目标。
     *
     * @param port 本地端口
     * @return URL
     */
    private static URL localUrl(int port) {
        try {
            return new URL("http://127.0.0.1:" + port);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("本地端口非法: " + port, e);
        }
    }

    /**
     * 构造隧道元数据（ngrok 控制台可见）。
     *
     * @param record 映射
     * @param mode   模式
     * @return 元数据
     */
    private static String buildTunnelMetadata(TunnelRecord record, String mode) {
        String base = StringUtils.hasText(record.getMetadata())
                ? record.getMetadata().trim()
                : "spring-support-ngrok";
        return base + "/" + mode + ":" + record.getLocalPort();
    }

    /**
     * 解析 authtoken。
     *
     * @param record 映射（可为空）
     * @return token，未配置时返回 null
     */
    private String resolveAuthtoken(TunnelRecord record) {
        if (record != null && StringUtils.hasText(record.getAuthtoken())) {
            return record.getAuthtoken().trim();
        }
        if (StringUtils.hasText(properties.getAuthtoken())) {
            return properties.getAuthtoken().trim();
        }
        String env = System.getenv(ENV_AUTHTOKEN);
        if (StringUtils.hasText(env)) {
            return env.trim();
        }
        String sys = System.getProperty(ENV_AUTHTOKEN);
        return StringUtils.hasText(sys) ? sys.trim() : null;
    }

    /**
     * 加载私有部署 CA 证书。
     *
     * @param location 资源位置
     * @return 证书字节
     */
    private byte[] loadCaCert(String location) {
        if (resourceLoader == null) {
            throw new IllegalStateException("ca-cert 配置需要 ResourceLoader，但当前环境未提供");
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("CA 证书资源不存在: " + location);
        }
        try {
            return resource.getInputStream().readAllBytes();
        } catch (IOException e) {
            Path path = Path.of(location.replaceFirst("^classpath:", ""));
            if (Files.exists(path)) {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("读取 CA 证书失败: " + location, ex);
                }
            }
            throw new IllegalStateException("读取 CA 证书失败: " + location, e);
        }
    }

    /**
     * 关闭客户端并吞掉异常。
     *
     * @param client 客户端
     */
    private static void closeQuietly(NgrokClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.warn("[Ngrok] 关闭客户端异常: {}", e.getMessage());
        }
    }
}
