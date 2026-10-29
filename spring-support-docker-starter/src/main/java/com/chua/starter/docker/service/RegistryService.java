package com.chua.starter.docker.service;

import com.chua.starter.docker.spi.DockerRegistryProvider;
import com.chua.starter.docker.spi.RegistryConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 镜像库管理服务 —— JSON 文件持久化 + CRUD + 跨库并发检索 + 标签 + 连通性测试。
 *
 * <p>持久化文件 {@code ~/.monitor/docker/registries.json}（UTF-8 无 BOM，临时文件 +
 * move 写入，读改写由文件锁串行化）。密码只在本服务内部使用：对外视图一律剔除
 * {@code password}，只回 {@code hasPassword}。</p>
 *
 * <p>镜像库类型由 SPI 提供：{@link DockerRegistryProvider} 的 Spring Bean 集合按
 * {@link DockerRegistryProvider#type()} 建索引，找不到实现直接报错，不做静默降级。
 * 内置 Docker Hub 记录 {@code builtin=true}，不可删除，但可以改镜像加速地址与凭据。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Slf4j
@Service
public class RegistryService {

    /**
     * 内置 Docker Hub 的固定标识
     */
    public static final String BUILTIN_ID = "dockerhub";

    /**
     * 内置 Docker Hub 的类型
     */
    private static final String TYPE_DOCKERHUB = "dockerhub";

    /**
     * 检索默认条数
     */
    private static final int DEFAULT_LIMIT = 25;

    /**
     * 单库检索最多返回条数
     */
    private static final int MAX_LIMIT = 100;

    /**
     * 密码占位符：前端回传该值时视为「未修改密码」
     */
    private static final String PASSWORD_MASK = "******";

    /**
     * JSON 读写器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 镜像库持久化
     */
    private final JsonFileStore store;

    /**
     * 类型 → 实现
     */
    private final Map<String, DockerRegistryProvider> providerIndex = new LinkedHashMap<>();

    /**
     * 跨库并发检索执行器
     */
    private final ExecutorService searchPool = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 构造服务并注册 SPI 实现、补齐内置记录。
     *
     * @param providers Spring 注入的全部镜像库实现
     */
    public RegistryService(List<DockerRegistryProvider> providers) {
        if (providers != null) {
            for (DockerRegistryProvider provider : providers) {
                providerIndex.put(provider.type(), provider);
            }
        }
        this.store = new JsonFileStore(Paths.get(System.getProperty("user.home"),
                ".monitor", "docker", "registries.json"), objectMapper);
        ensureBuiltin();
    }

    /**
     * 镜像库视图列表（不含密码）
     *
     * @return 视图列表，内置库排在最前
     */
    public List<Map<String, Object>> list() {
        List<RegistryConfig> configs = loadAll();
        configs.sort(Comparator.comparing((RegistryConfig c) -> !Boolean.TRUE.equals(c.getBuiltin()))
                .thenComparing(c -> c.getName() == null ? "" : c.getName()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (RegistryConfig config : configs) {
            out.add(view(config));
        }
        return out;
    }

    /**
     * 新增或更新镜像库。
     *
     * @param body 表单：id/name/type/url/username/password/namespace/enabled
     * @return 保存后的视图（不含密码）
     */
    public Map<String, Object> save(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("镜像库配置不能为空");
        }
        String id = text(body.get("id"));
        String type = text(body.get("type"));
        String name = text(body.get("name"));
        String url = text(body.get("url"));
        String username = text(body.get("username"));
        String password = text(body.get("password"));
        String namespace = text(body.get("namespace"));
        Object enabledRaw = body.get("enabled");
        boolean enabled = enabledRaw == null || Boolean.parseBoolean(String.valueOf(enabledRaw));

        if (type.isEmpty()) {
            type = TYPE_DOCKERHUB;
        }
        if (!providerIndex.containsKey(type)) {
            throw new IllegalArgumentException("不支持的镜像库类型: " + type
                    + "，可用类型: " + providerIndex.keySet());
        }
        if (name.isEmpty()) {
            name = providerIndex.get(type).displayName();
        }

        store.lock.lock();
        try {
            List<RegistryConfig> configs = loadAll();
            RegistryConfig target = null;
            for (RegistryConfig config : configs) {
                if (id.equals(config.getId())) {
                    target = config;
                    break;
                }
            }
            boolean created = target == null;
            if (created) {
                target = new RegistryConfig();
                target.setId(id.isEmpty() ? UUID.randomUUID().toString() : id);
                target.setBuiltin(false);
                target.setCreatedAt(now());
                configs.add(target);
            }
            boolean builtin = Boolean.TRUE.equals(target.getBuiltin());
            target.setName(name);
            if (!builtin) {
                target.setType(type);
            }
            target.setUrl(url);
            target.setUsername(username);
            target.setNamespace(namespace);
            target.setEnabled(enabled);
            target.setUpdatedAt(now());
            if (!password.isEmpty() && !PASSWORD_MASK.equals(password)) {
                target.setPassword(password);
            }
            store.write(configs);
            log.info("[docker] {} 镜像库 {} ({})", created ? "新增" : "更新", target.getId(), target.getName());
            return view(target);
        } finally {
            store.lock.unlock();
        }
    }

    /**
     * 删除镜像库（内置库不可删除）。
     *
     * @param id 库标识
     * @return 是否删除
     */
    public boolean delete(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("镜像库标识不能为空");
        }
        store.lock.lock();
        try {
            List<RegistryConfig> configs = loadAll();
            RegistryConfig target = null;
            for (RegistryConfig config : configs) {
                if (id.equals(config.getId())) {
                    target = config;
                    break;
                }
            }
            if (target == null) {
                return false;
            }
            if (Boolean.TRUE.equals(target.getBuiltin())) {
                throw new IllegalArgumentException("内置镜像库不可删除: " + target.getName());
            }
            configs.remove(target);
            store.write(configs);
            return true;
        } finally {
            store.lock.unlock();
        }
    }

    /**
     * 测试连通性（含凭据校验）。
     *
     * @param id 库标识
     * @return {ok, message}
     */
    public Map<String, Object> test(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        RegistryConfig config;
        try {
            config = config(id);
        } catch (Exception e) {
            out.put("ok", false);
            out.put("message", e.getMessage());
            return out;
        }
        try {
            boolean ok = providerFor(config.getType()).test(config);
            out.put("ok", ok);
            out.put("message", ok ? "连接成功" : "连接失败，请检查地址、凭据与网络");
        } catch (Exception e) {
            log.warn("[docker] 镜像库测试异常 id={}: {}", id, e.getMessage());
            out.put("ok", false);
            out.put("message", "连接失败: " + e.getMessage());
        }
        return out;
    }

    /**
     * 检索镜像。
     *
     * <p>{@code registryId} 为空时并发检索全部启用镜像库，结果按 pullRef 去重后
     * 按星标降序、官方优先排序；单库失败只记日志，不影响其它库的结果。</p>
     *
     * @param keyword    关键词，至少 2 个字符
     * @param registryId 指定镜像库，可空
     * @param limit      单库返回上限
     * @return 统一结构：registryId/registryName/name/description/stars/official/pullRef
     */
    public List<Map<String, Object>> search(String keyword, String registryId, Integer limit) {
        String word = keyword == null ? "" : keyword.trim();
        if (word.length() < 2) {
            return new ArrayList<>();
        }
        int size = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        if (registryId != null && !registryId.isBlank()) {
            RegistryConfig config = config(registryId);
            if (!config.enabledOrDefault()) {
                throw new IllegalArgumentException("镜像库已禁用: " + config.getName());
            }
            return decorate(config, word, size);
        }
        List<RegistryConfig> enabled = new ArrayList<>();
        for (RegistryConfig config : loadAll()) {
            if (config.enabledOrDefault()) {
                enabled.add(config);
            }
        }
        List<CompletableFuture<List<Map<String, Object>>>> tasks = new ArrayList<>();
        for (RegistryConfig config : enabled) {
            tasks.add(CompletableFuture.supplyAsync(() -> decorate(config, word, size), searchPool));
        }
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (CompletableFuture<List<Map<String, Object>>> task : tasks) {
            try {
                for (Map<String, Object> row : task.join()) {
                    merged.putIfAbsent(String.valueOf(row.get("pullRef")), row);
                }
            } catch (Exception e) {
                log.warn("[docker] 并发检索单库失败: {}", e.getMessage());
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(merged.values());
        out.sort(Comparator
                .comparing((Map<String, Object> row) -> !Boolean.TRUE.equals(row.get("official")))
                .thenComparing(row -> -((Number) row.getOrDefault("stars", 0)).intValue()));
        return out;
    }

    /**
     * 查询镜像标签。
     *
     * @param id    库标识
     * @param image 镜像名（不含 registry host）
     * @return 标签列表
     */
    public List<String> tags(String id, String image) {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("镜像名不能为空");
        }
        RegistryConfig config = config(id);
        return providerFor(config.getType()).tags(config, image.trim());
    }

    /**
     * 可用镜像库类型（供前端下拉）。
     *
     * @return [{type, displayName}]
     */
    public List<Map<String, String>> types() {
        List<Map<String, String>> out = new ArrayList<>();
        for (DockerRegistryProvider provider : providerIndex.values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("type", provider.type());
            item.put("displayName", provider.displayName());
            out.add(item);
        }
        return out;
    }

    /**
     * 把镜像名解析为可直接用于 {@code docker pull|run} 的引用。
     *
     * @param registryId 库标识，可空
     * @param image      镜像名
     * @return pullRef；未指定库时原样返回镜像名
     */
    public String pullRef(String registryId, String image) {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("镜像名不能为空");
        }
        String name = image.trim();
        if (registryId == null || registryId.isBlank()) {
            return name;
        }
        RegistryConfig config = config(registryId);
        if (!config.enabledOrDefault()) {
            throw new IllegalArgumentException("镜像库已禁用: " + config.getName());
        }
        String ref = providerFor(config.getType()).pullRef(config, name);
        return ref == null || ref.isBlank() ? name : ref;
    }

    /**
     * 取完整配置（含密码）。
     *
     * @param id 库标识
     * @return 配置
     */
    public RegistryConfig config(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("镜像库标识不能为空");
        }
        for (RegistryConfig config : loadAll()) {
            if (id.equals(config.getId())) {
                return config;
            }
        }
        throw new IllegalArgumentException("镜像库不存在: " + id);
    }

    /**
     * 取 {@code docker login} 需要的主机名。
     *
     * @param config 镜像库配置
     * @return 主机名；Docker Hub 无加速地址时返回 {@code docker.io}
     */
    public String loginHost(RegistryConfig config) {
        String host = hostOf(config.getUrl());
        return host.isEmpty() ? "docker.io" : host;
    }

    /**
     * 从库地址解析主机名（去协议、路径与 {@code /v2}）。
     *
     * @param url 库地址
     * @return 主机名，可能为空
     */
    public String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String value = url.trim();
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        value = value.trim().toLowerCase();
        if ("hub.docker.com".equals(value) || "docker.io".equals(value)
                || "index.docker.io".equals(value) || "registry-1.docker.io".equals(value)) {
            return "";
        }
        return value;
    }

    /**
     * 按类型取实现，缺失时报错。
     *
     * @param type 类型
     * @return 实现
     */
    private DockerRegistryProvider providerFor(String type) {
        DockerRegistryProvider provider = providerIndex.get(type);
        if (provider == null) {
            throw new IllegalStateException("没有可用的镜像库实现，type=" + type
                    + "，已注册: " + providerIndex.keySet());
        }
        return provider;
    }

    /**
     * 单库检索并补上来源库信息。
     *
     * @param config 库配置
     * @param word   关键词
     * @param size   条数
     * @return 结果行
     */
    private List<Map<String, Object>> decorate(RegistryConfig config, String word, int size) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            for (Map<String, Object> row : providerFor(config.getType()).search(config, word, size)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("registryId", config.getId());
                item.put("registryName", config.getName());
                item.put("name", row.getOrDefault("name", ""));
                item.put("description", row.getOrDefault("description", ""));
                item.put("stars", row.getOrDefault("stars", 0));
                item.put("official", row.getOrDefault("official", false));
                item.put("pullRef", row.getOrDefault("pullRef", ""));
                out.add(item);
            }
        } catch (Exception e) {
            log.warn("[docker] 镜像库 {} 检索失败: {}", config.getName(), e.getMessage());
        }
        return out;
    }

    /**
     * 读取全部配置
     *
     * @return 配置列表
     */
    private List<RegistryConfig> loadAll() {
        return store.readList(RegistryConfig.class);
    }

    /**
     * 对外视图：剔除密码，补 hasPassword
     *
     * @param config 配置
     * @return 视图
     */
    private Map<String, Object> view(RegistryConfig config) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", config.getId());
        out.put("name", config.getName());
        out.put("type", config.getType());
        out.put("url", config.getUrl());
        out.put("username", config.getUsername());
        out.put("namespace", config.getNamespace());
        out.put("enabled", config.enabledOrDefault());
        out.put("builtin", Boolean.TRUE.equals(config.getBuiltin()));
        out.put("hasPassword", config.getPassword() != null && !config.getPassword().isEmpty());
        return out;
    }

    /**
     * 保证内置 Docker Hub 记录存在（首次启动时落盘）。
     */
    private void ensureBuiltin() {
        store.lock.lock();
        try {
            List<RegistryConfig> configs = loadAll();
            for (RegistryConfig config : configs) {
                if (BUILTIN_ID.equals(config.getId())) {
                    return;
                }
            }
            RegistryConfig builtin = new RegistryConfig();
            builtin.setId(BUILTIN_ID);
            builtin.setName("Docker Hub");
            builtin.setType(TYPE_DOCKERHUB);
            builtin.setUrl("https://hub.docker.com");
            builtin.setEnabled(true);
            builtin.setBuiltin(true);
            builtin.setCreatedAt(now());
            builtin.setUpdatedAt(now());
            configs.add(0, builtin);
            store.write(configs);
            log.info("[docker] 已初始化内置镜像库 Docker Hub");
        } catch (Exception e) {
            log.warn("[docker] 初始化内置镜像库失败: {}", e.getMessage());
        } finally {
            store.lock.unlock();
        }
    }

    /**
     * null 安全取串并去空白
     *
     * @param value 原值
     * @return 非 null 字符串
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 当前时间（ISO-8601）
     *
     * @return 时间串
     */
    public static String now() {
        return Instant.now().toString();
    }
}
