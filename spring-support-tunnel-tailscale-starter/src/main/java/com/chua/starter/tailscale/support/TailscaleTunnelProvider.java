// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.tailscale.support;

import com.chua.tailscale.support.TailscaleTool;
import com.chua.tailscale.support.TailscaleTunnelClient;
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
 * Tailscale Funnel 提供方实现：把「本机端口 → Tailscale 公网地址」接入统一入口。
 *
 * <p>与 ngrok 侧的差别只在厂商细节：每个映射独占一个 {@code tailscale}
 * 子进程，模式是 {@code http}（默认，Funnel 提供 HTTPS 地址）或 {@code tcp}
 *（Funnel 把设定的公网 TCP 端口暴露到 Tailnet）。
 * 增删改查、持久化、启动恢复等公共流程由
 * {@code spring-support-tunnel-starter} 统一提供。</p>
 *
 * <p>注意：<b>Tailscale 没有单条隧道的 Token</b>，设备必须先加入 Tailnet
 * （{@code tailscale up} 或通过 {@code AuthKey} 认证），
 * {@link #capabilities()} 中的 {@code authKeyConfigured} 会告诉页面设备是否已准备就绪。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
public class TailscaleTunnelProvider implements TunnelProvider {

    /**
     * 提供方标识。
     */
    public static final String NAME = "tailscale";

    /**
     * 支持的模式。
     */
    private static final List<String> MODES = List.of("http", "tcp");

    /**
     * Funnel 允许暴露的公网 TCP 端口（非 root / 未提权的设备只能使用这些端口）。
     */
    private static final List<Integer> TCP_PORTS = List.of(443, 8443, 10000);

    /**
     * 全局配置。
     */
    private final TailscaleProperties properties;

    /**
     * 创建提供方。
     *
     * @param properties 全局配置
     */
    public TailscaleTunnelProvider(TailscaleProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String displayName() {
        return "Tailscale Funnel";
    }

    /**
     * 是否能在本机找到 {@code tailscale} 可执行文件（已登录 Tailnet 的设备，
     * 守护进程是否在运行交给 {@code funnel} 子进程去报错）。
     *
     * @return 找到可执行文件返回 true
     */
    @Override
    public boolean isAvailable() {
        return new TailscaleTool().isAvailable();
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
     * 沿用既有配置 {@code plugin.tailscale.store-dir}。
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
        TailscaleTool tool = new TailscaleTool();
        boolean available = tool.isAvailable();
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("provider", NAME);
        caps.put("binaryAvailable", available);
        caps.put("binaryPath", available ? tool.locate().map(Path::toString).orElse(null) : null);
        caps.put("authKeyConfigured", StringUtils.hasText(properties.resolveAuthKey()));
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
        TunnelInputs.requireMode(mode, MODES, "Tailscale");
        target.setMode(mode);
        target.setDomain(TunnelInputs.trimToNull(input.getDomain()));
        // ngrok / cloudflared 专属字段在 tailscale 记录上必须清空，避免两套配置互相污染。
        target.setAuthtoken(null);
        target.setRemoteAddress(null);
        target.setBasicAuth(null);
        target.setMetadata(null);
        target.setToken(null);
        TunnelInputs.applyCommon(target, input, NAME);
    }

    @Override
    public TunnelSession open(TunnelRecord record) {
        TailscaleTunnelClient client = null;
        try {
            client = buildClient(record);
            client.start();
            return new TailscaleSession(client);
        } catch (RuntimeException e) {
            closeQuietly(client);
            throw e;
        }
    }

    /**
     * Tailscale 会话：持有一个已启动的 {@code tailscale funnel} 子进程客户端。
     */
    private static final class TailscaleSession implements TunnelSession {

        /**
         * 客户端。
         */
        private final TailscaleTunnelClient client;

        /**
         * 关闭标记（保证 close 幂等）。
         */
        private boolean closed;

        /**
         * 创建会话。
         *
         * @param client 客户端
         */
        private TailscaleSession(TailscaleTunnelClient client) {
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
                log.warn("[Tailscale] 关闭客户端异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 按记录构建子进程客户端。
     *
     * @param record 映射记录
     * @return 客户端
     */
    private TailscaleTunnelClient buildClient(TunnelRecord record) {
        TailscaleTunnelClient client;
        if (StringUtils.hasText(properties.getBinary())) {
            client = TailscaleTunnelClient.create(Path.of(properties.getBinary().trim()).toAbsolutePath());
        } else {
            client = TailscaleTunnelClient.create();
        }
        client.url("http://127.0.0.1:" + record.getLocalPort());
        String mode = TunnelInputs.normalize(record.getMode(), defaultMode()).toLowerCase();
        // Funnel 只允许在 443 / 8443 / 10000 暴露 TCP（非 root 设备），http 模式不设 --tcp。
        if ("tcp".equals(mode)) {
            client.tcpPort(String.valueOf(TCP_PORTS.get(0)));
        }
        String authKey = properties.resolveAuthKey();
        if (StringUtils.hasText(authKey)) {
            client.authKey(authKey.trim());
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
    private static void closeQuietly(TailscaleTunnelClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.warn("[Tailscale] 关闭客户端异常: {}", e.getMessage());
        }
    }
}
