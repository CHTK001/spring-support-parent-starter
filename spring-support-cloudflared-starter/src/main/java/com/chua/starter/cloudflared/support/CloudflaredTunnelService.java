package com.chua.starter.cloudflared.support;

import com.chua.cloudflared.support.CloudflareTunnelClient;
import com.chua.cloudflared.support.CloudflaredTool;
import com.chua.starter.common.support.tunnel.TunnelFileStore;
import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.common.support.tunnel.TunnelView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cloudflare Tunnel 编排服务：映射的增删改查、按需启停、文件持久化与启动恢复。
 *
 * <p>与 {@link CloudflaredAutoConfiguration} 的分工：自动配置只负责「按 application.yml
 * 在启动时建一条默认隧道」；本服务负责「页面创建的任意端口映射」。每个映射独占一个
 * {@code cloudflared} 子进程，停止时优雅退出、超时强杀。</p>
 *
 * <p>持久化：{@link TunnelRecord} 由 {@link TunnelFileStore} 写到
 * {@code plugin.cloudflared.store-dir}（默认 {@code ~/.chua/tunnel/cloudflared}），
 * 一条映射一个 JSON 文件，不依赖数据库。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class CloudflaredTunnelService {

    /**
     * 提供方标识。
     */
    public static final String PROVIDER = "cloudflared";

    /**
     * 全局配置。
     */
    private final CloudflaredProperties properties;

    /**
     * 文件存储器。
     */
    private final TunnelFileStore store;

    /**
     * 运行中的子进程客户端：映射 id → 客户端。
     */
    private final Map<String, CloudflareTunnelClient> clients = new ConcurrentHashMap<>();

    /**
     * 启动时间：映射 id → 毫秒时间戳。
     */
    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();

    /**
     * 最近一次启动错误：映射 id → 错误信息。
     */
    private final Map<String, String> errors = new ConcurrentHashMap<>();

    /**
     * 创建服务。
     *
     * @param properties 全局配置
     */
    public CloudflaredTunnelService(CloudflaredProperties properties) {
        this.properties = properties;
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.store = new TunnelFileStore(resolveStoreDir(properties), mapper);
        log.info("[Cloudflared] 映射持久化目录: {}（已加载 {} 条）", store.getDir(), store.findAll().size());
    }

    /**
     * 全部映射（含运行态）。
     *
     * @return 映射列表
     */
    public List<TunnelView> list() {
        return store.findAll().stream().map(this::toView).toList();
    }

    /**
     * 单条映射。
     *
     * @param id 标识
     * @return 映射
     */
    public TunnelView get(String id) {
        return toView(require(id));
    }

    /**
     * 新建映射（不自动启动）。
     *
     * @param input 页面提交的配置
     * @return 保存后的映射
     */
    public TunnelView create(TunnelRecord input) {
        TunnelRecord record = new TunnelRecord();
        record.setProvider(PROVIDER);
        applyInput(record, input);
        store.save(record);
        log.info("[Cloudflared] 新增映射 {}（{} → 公网）", record.getName(), record.getLocalPort());
        return toView(record);
    }

    /**
     * 更新映射。运行中的映射会先停止，再按新配置重启。
     *
     * @param id    标识
     * @param input 新配置
     * @return 保存后的映射
     */
    public TunnelView update(String id, TunnelRecord input) {
        TunnelRecord record = require(id);
        boolean running = clients.containsKey(id);
        if (running) {
            stop(id);
        }
        applyInput(record, input);
        store.save(record);
        if (running) {
            start(id);
        }
        return toView(record);
    }

    /**
     * 删除映射（运行中会先停止）。
     *
     * @param id 标识
     * @return 是否删除成功
     */
    public boolean delete(String id) {
        require(id);
        stop(id);
        boolean removed = store.delete(id).isPresent();
        if (removed) {
            log.info("[Cloudflared] 已删除映射 {}", id);
        }
        return removed;
    }

    /**
     * 启动映射（阻塞等待公网地址，最长 {@code plugin.cloudflared.start-timeout}）。
     *
     * @param id 标识
     * @return 启动后的映射
     */
    public TunnelView start(String id) {
        TunnelRecord record = require(id);
        if (clients.containsKey(id)) {
            return toView(record);
        }
        errors.remove(id);
        CloudflareTunnelClient client = null;
        try {
            // 与 ngrok 侧同理：构建客户端也在 try 内。缺失 Token / 非法模式等
            // 前置校验在 buildClient 中抛出，若留在 try 外，「错误信息」列会一直是空。
            client = buildClient(record);
            client.start();
            clients.put(id, client);
            startedAt.put(id, System.currentTimeMillis());
            log.info("[Cloudflared] 映射已启动 {}（{} → {}）", record.getName(), record.getLocalPort(), client.getUrls());
            return toView(record);
        } catch (RuntimeException e) {
            closeQuietly(client);
            errors.put(id, e.getMessage());
            throw new IllegalStateException("Cloudflare 隧道启动失败: " + e.getMessage(), e);
        }
    }

    /**
     * 停止映射（保留配置）。
     *
     * @param id 标识
     * @return 停止后的映射
     */
    public TunnelView stop(String id) {
        TunnelRecord record = require(id);
        CloudflareTunnelClient client = clients.remove(id);
        startedAt.remove(id);
        if (client != null) {
            closeQuietly(client);
            log.info("[Cloudflared] 映射已停止 {}", record.getName());
        }
        return toView(record);
    }

    /**
     * 模块能力探测：cloudflared 是否可执行、持久化目录等。
     *
     * @return 能力信息
     */
    public Map<String, Object> capabilities() {
        CloudflaredTool tool = new CloudflaredTool();
        boolean available = tool.isAvailable();
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("provider", PROVIDER);
        caps.put("binaryAvailable", available);
        caps.put("binaryPath", available ? tool.locate().map(Path::toString).orElse(null) : null);
        caps.put("tokenConfigured", StringUtils.hasText(properties.getToken()));
        caps.put("storeDir", store.getDir().toString());
        caps.put("autoStart", properties.isAutoStart());
        caps.put("autoStartStored", properties.isAutoStartStored());
        return caps;
    }

    /**
     * 应用就绪后恢复「自动启动」的映射。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreAutoStart() {
        if (!properties.isAutoStartStored()) {
            return;
        }
        for (TunnelRecord record : store.findAll()) {
            if (!record.isEnabled() || !record.isAutoStart()) {
                continue;
            }
            try {
                start(record.getId());
            } catch (RuntimeException e) {
                log.warn("[Cloudflared] 恢复映射 {} 失败: {}", record.getName(), e.getMessage());
            }
        }
    }

    /**
     * 容器关闭时关闭全部子进程。
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, CloudflareTunnelClient> entry : clients.entrySet()) {
            closeQuietly(entry.getValue());
        }
        clients.clear();
        startedAt.clear();
    }

    /**
     * 按记录构建客户端。
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
        String mode = normalize(record.getMode(), "quick").toLowerCase();
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
     * 把页面提交的字段合并进记录并校验。
     *
     * @param target 目标记录
     * @param input  页面提交
     */
    private void applyInput(TunnelRecord target, TunnelRecord input) {
        if (input == null) {
            throw new IllegalArgumentException("映射配置不能为空");
        }
        int port = input.getLocalPort();
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("本地端口必须在 1 - 65535 之间");
        }
        String mode = normalize(input.getMode(), "quick").toLowerCase();
        if (!List.of("quick", "named").contains(mode)) {
            throw new IllegalArgumentException("不支持的 Cloudflare 模式: " + mode);
        }
        if ("named".equals(mode)
                && !StringUtils.hasText(input.getToken())
                && !StringUtils.hasText(properties.getToken())) {
            throw new IllegalArgumentException("named 模式必须填写 Cloudflare 隧道 Token");
        }
        target.setLocalPort(port);
        target.setMode(mode);
        target.setName(StringUtils.hasText(input.getName())
                ? input.getName().trim()
                : PROVIDER + "-" + port);
        target.setDomain(trimToNull(input.getDomain()));
        target.setToken(trimToNull(input.getToken()));
        target.setRemark(trimToNull(input.getRemark()));
        target.setAutoStart(input.isAutoStart());
        target.setEnabled(input.isEnabled());
        target.setProvider(PROVIDER);
    }

    /**
     * 转运行态视图。
     *
     * @param record 记录
     * @return 视图
     */
    private TunnelView toView(TunnelRecord record) {
        TunnelView view = new TunnelView();
        BeanUtils.copyProperties(record, view);
        CloudflareTunnelClient client = clients.get(record.getId());
        view.setRunning(client != null);
        view.setPublicUrls(client == null ? List.of() : client.getUrls());
        view.setStartedAt(startedAt.get(record.getId()));
        view.setError(errors.get(record.getId()));
        return view;
    }

    /**
     * 查询记录，不存在时抛出业务异常。
     *
     * @param id 标识
     * @return 记录
     */
    private TunnelRecord require(String id) {
        return store.find(id).orElseThrow(() -> new IllegalArgumentException("映射不存在: " + id));
    }

    /**
     * 关闭客户端并吞掉异常。
     *
     * @param client 客户端
     */
    private static void closeQuietly(CloudflareTunnelClient client) {
        if (null == client) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.warn("[Cloudflared] 关闭客户端异常: {}", e.getMessage());
        }
    }

    /**
     * 解析持久化目录。
     *
     * @param properties 配置
     * @return 目录
     */
    private static Path resolveStoreDir(CloudflaredProperties properties) {
        if (StringUtils.hasText(properties.getStoreDir())) {
            return Path.of(properties.getStoreDir().trim());
        }
        return Path.of(System.getProperty("user.home"), ".chua", "tunnel", "cloudflared");
    }

    /**
     * 去空白，空串返回 null。
     *
     * @param value 原值
     * @return 处理后的值
     */
    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 默认值兜底。
     *
     * @param value        原值
     * @param defaultValue 默认值
     * @return 处理后的值
     */
    private static String normalize(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /**
     * 按标识查询（供诊断/测试使用）。
     *
     * @param id 标识
     * @return 记录
     */
    public Optional<TunnelRecord> findRecord(String id) {
        return store.find(id);
    }
}
