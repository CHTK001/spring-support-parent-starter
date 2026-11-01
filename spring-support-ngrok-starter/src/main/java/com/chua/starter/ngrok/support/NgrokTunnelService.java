package com.chua.starter.ngrok.support;

import com.chua.ngrok.support.NgrokClient;
import com.chua.starter.common.support.tunnel.TunnelFileStore;
import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.common.support.tunnel.TunnelView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ngrok.Http;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ngrok 隧道编排服务：映射的增删改查、按需启停、文件持久化与启动恢复。
 *
 * <p>与 {@link NgrokAutoConfiguration} 的分工：自动配置只负责「按 application.yml 在启动时
 * 建一条默认隧道」；本服务负责「页面创建的任意端口映射」。两者共用同一套
 * {@link NgrokProperties} 凭据，但各自持有独立的 {@link NgrokClient} 会话，
 * 互不影响。</p>
 *
 * <p>持久化：{@link TunnelRecord} 由 {@link TunnelFileStore} 写到
 * {@code plugin.ngrok.store-dir}（默认 {@code ~/.chua/tunnel/ngrok}），
 * 一条映射一个 JSON 文件，不依赖数据库。运行态只留在内存。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public class NgrokTunnelService {

    /**
     * ngrok authtoken 环境变量名。
     */
    private static final String ENV_AUTHTOKEN = "NGROK_AUTHTOKEN";

    /**
     * 提供方标识。
     */
    public static final String PROVIDER = "ngrok";

    /**
     * 全局配置。
     */
    private final NgrokProperties properties;

    /**
     * 文件存储器。
     */
    private final TunnelFileStore store;

    /**
     * 资源加载器（读取私有部署 CA 证书）。
     */
    private final ResourceLoader resourceLoader;

    /**
     * 运行中的客户端：映射 id → 会话。
     */
    private final Map<String, NgrokClient> clients = new ConcurrentHashMap<>();

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
     * @param properties     全局配置
     * @param resourceLoader 资源加载器（可为空）
     */
    public NgrokTunnelService(NgrokProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.store = new TunnelFileStore(resolveStoreDir(properties), mapper);
        log.info("[Ngrok] 映射持久化目录: {}（已加载 {} 条）", store.getDir(), store.findAll().size());
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
        log.info("[Ngrok] 新增映射 {}（{} → 公网）", record.getName(), record.getLocalPort());
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
            log.info("[Ngrok] 已删除映射 {}", id);
        }
        return removed;
    }

    /**
     * 启动映射。
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
        NgrokClient client = buildClient(record);
        try {
            client.connect();
            openTunnel(client, record);
            clients.put(id, client);
            startedAt.put(id, System.currentTimeMillis());
            List<String> urls = client.getUrls();
            log.info("[Ngrok] 映射已启动 {}（{} → {}）", record.getName(), record.getLocalPort(), urls);
            return toView(record);
        } catch (RuntimeException e) {
            closeQuietly(client);
            errors.put(id, e.getMessage());
            throw new IllegalStateException("ngrok 隧道启动失败: " + e.getMessage(), e);
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
        NgrokClient client = clients.remove(id);
        startedAt.remove(id);
        if (client != null) {
            closeQuietly(client);
            log.info("[Ngrok] 映射已停止 {}", record.getName());
        }
        return toView(record);
    }

    /**
     * 模块能力探测：凭据是否已配置、持久化目录等。
     *
     * @return 能力信息
     */
    public Map<String, Object> capabilities() {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("provider", PROVIDER);
        caps.put("authtokenConfigured", resolveAuthtoken(null) != null);
        caps.put("storeDir", store.getDir().toString());
        caps.put("autoStart", properties.isAutoStart());
        caps.put("autoStartStored", properties.isAutoStartStored());
        return caps;
    }

    /**
     * 应用就绪后恢复「自动启动」的映射。
     *
     * <p>放在 {@link ApplicationReadyEvent} 之后，确保 Web 容器与配置都已就绪，
     * 且与自动配置里的默认隧道互不干扰。</p>
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
                log.warn("[Ngrok] 恢复映射 {} 失败: {}", record.getName(), e.getMessage());
            }
        }
    }

    /**
     * 容器关闭时关闭全部会话。
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, NgrokClient> entry : clients.entrySet()) {
            closeQuietly(entry.getValue());
        }
        clients.clear();
        startedAt.clear();
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
        String mode = normalize(record.getMode(), "http");
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
     * 构造隧道元数据（控制台可见）。
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
        String mode = normalize(input.getMode(), "http").toLowerCase();
        if (!List.of("http", "tcp", "tls").contains(mode)) {
            throw new IllegalArgumentException("不支持的 ngrok 模式: " + mode);
        }
        if ("tcp".equals(mode) && !StringUtils.hasText(input.getRemoteAddress())) {
            throw new IllegalArgumentException("tcp 模式必须填写远程地址");
        }
        target.setLocalPort(port);
        target.setMode(mode);
        target.setName(StringUtils.hasText(input.getName())
                ? input.getName().trim()
                : PROVIDER + "-" + port);
        target.setDomain(trimToNull(input.getDomain()));
        target.setRemoteAddress(trimToNull(input.getRemoteAddress()));
        target.setBasicAuth(trimToNull(input.getBasicAuth()));
        target.setAuthtoken(trimToNull(input.getAuthtoken()));
        target.setMetadata(trimToNull(input.getMetadata()));
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
        NgrokClient client = clients.get(record.getId());
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
    private static void closeQuietly(NgrokClient client) {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("[Ngrok] 关闭客户端异常: {}", e.getMessage());
        }
    }

    /**
     * 解析持久化目录。
     *
     * @param properties 配置
     * @return 目录
     */
    private static Path resolveStoreDir(NgrokProperties properties) {
        if (StringUtils.hasText(properties.getStoreDir())) {
            return Path.of(properties.getStoreDir().trim());
        }
        return Path.of(System.getProperty("user.home"), ".chua", "tunnel", "ngrok");
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
