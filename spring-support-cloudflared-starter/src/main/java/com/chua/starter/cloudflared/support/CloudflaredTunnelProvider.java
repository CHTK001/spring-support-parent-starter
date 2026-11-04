package com.chua.starter.cloudflared.support;

import com.chua.cloudflared.support.CloudflareTunnelClient;
import com.chua.cloudflared.support.CloudflaredTool;
import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.tunnel.spi.TunnelProvider;
import com.chua.starter.tunnel.spi.TunnelSession;
import com.chua.starter.tunnel.support.TunnelInputs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cloudflare Tunnel 提供方实现：把「本机端口 → Cloudflare 公网地址」接入统一入口。
 *
 * <p>与 ngrok 侧的差别只在厂商细节：每个映射独占一个 {@code cloudflared}
 * 子进程，模式是 {@code quick}（免账号随机域名）或 {@code named}（token 命名隧道）。
 * 增删改查、持久化、启动恢复等公共流程由
 * {@code spring-support-tunnel-starter} 统一提供。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
public class CloudflaredTunnelProvider implements TunnelProvider {

    /**
     * 提供方标识。
     */
    public static final String NAME = "cloudflared";

    /**
     * 支持的模式。
     */
    private static final List<String> MODES = List.of("quick", "named");

    /**
     * 全局配置。
     */
    private final CloudflaredProperties properties;

    /**
     * 创建提供方。
     *
     * @param properties 全局配置
     */
    public CloudflaredTunnelProvider(CloudflaredProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String displayName() {
        return "Cloudflare Tunnel";
    }

    /**
     * 是否能在本机找到 {@code cloudflared} 可执行文件。
     *
     * @return 找到返回 true
     */
    @Override
    public boolean isAvailable() {
        return new CloudflaredTool().isAvailable();
    }

    @Override
    public List<String> supportedModes() {
        return MODES;
    }

    @Override
    public String defaultMode() {
        return "quick";
    }

    /**
     * 沿用既有配置 {@code plugin.cloudflared.store-dir}。
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
        CloudflaredTool tool = new CloudflaredTool();
        boolean available = tool.isAvailable();
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("provider", NAME);
        caps.put("binaryAvailable", available);
        caps.put("binaryPath", available ? tool.locate().map(Path::toString).orElse(null) : null);
        caps.put("tokenConfigured", StringUtils.hasText(properties.getToken()));
        caps.put("autoStart", properties.isAutoStart());
        caps.put("autoStartStored", properties.isAutoStartStored());
        return caps;
    }

    @Override
    public void applyInput(TunnelRecord target, TunnelRecord input) {
        if (input == null) {
            throw new IllegalArgumentException("映射配置不能为空");
        }
        String mode = TunnelInputs.normalize(input.getMode(), defaultMode()).toLowerCase();
        TunnelInputs.requireMode(mode, MODES, "Cloudflare");
        if ("named".equals(mode)
                && !StringUtils.hasText(input.getToken())
                && !StringUtils.hasText(properties.getToken())) {
            throw new IllegalArgumentException("named 模式必须填写 Cloudflare 隧道 Token");
        }
        target.setMode(mode);
        target.setDomain(TunnelInputs.trimToNull(input.getDomain()));
        target.setToken(TunnelInputs.trimToNull(input.getToken()));
        // ngrok 专属字段在 cloudflared 记录上必须清空，避免两套配置互相污染。
        target.setAuthtoken(null);
        target.setRemoteAddress(null);
        target.setBasicAuth(null);
        target.setMetadata(null);
        TunnelInputs.applyCommon(target, input, NAME);
    }

    @Override
    public TunnelSession open(TunnelRecord record) {
        CloudflareTunnelClient client = null;
        try {
            client = buildClient(record);
            client.start();
            return new CloudflaredSession(client);
        } catch (RuntimeException e) {
            closeQuietly(client);
            throw e;
        }
    }

    /**
     * cloudflared 会话：持有一个已启动的子进程客户端。
     */
    private static final class CloudflaredSession implements TunnelSession {

        /**
         * 客户端。
         */
        private final CloudflareTunnelClient client;

        /**
         * 关闭标记（保证 close 幂等）。
         */
        private boolean closed;

        /**
         * 创建会话。
         *
         * @param client 客户端
         */
        private CloudflaredSession(CloudflareTunnelClient client) {
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
                log.warn("[Cloudflared] 关闭客户端异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 按记录构建子进程客户端。
     *
     * @param record 映射记录
     * @return 客户端
     */
    private CloudflareTunnelClient buildClient(TunnelRecord record) {
        CloudflareTunnelClient client;
        if (StringUtils.hasText(properties.getBinary())) {
            client = CloudflareTunnelClient.create(Path.of(properties.getBinary().trim()).toAbsolutePath());
        } else {
            client = CloudflareTunnelClient.create();
        }
        client.url("http://127.0.0.1:" + record.getLocalPort());
        String mode = TunnelInputs.normalize(record.getMode(), defaultMode()).toLowerCase();
        if ("named".equals(mode)) {
            String token = StringUtils.hasText(record.getToken())
                    ? record.getToken().trim()
                    : properties.getToken();
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("named 模式必须填写 Cloudflare 隧道 Token");
            }
            client.token(token);
        }
        if (StringUtils.hasText(record.getDomain())) {
            client.hostname(record.getDomain().trim());
        }
        client.noAutoUpdate(properties.isNoAutoUpdate());
        if (StringUtils.hasText(properties.getLogLevel())) {
            client.logLevel(properties.getLogLevel());
        }
        if (properties.getStartTimeout() != null) {
            client.startTimeout(properties.getStartTimeout());
        }
        return client;
    }

    /**
     * 关闭客户端并吞掉异常。
     *
     * @param client 客户端
     */
    private static void closeQuietly(CloudflareTunnelClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.warn("[Cloudflared] 关闭客户端异常: {}", e.getMessage());
        }
    }
}
