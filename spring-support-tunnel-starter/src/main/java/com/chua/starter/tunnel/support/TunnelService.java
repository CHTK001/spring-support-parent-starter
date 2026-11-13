package com.chua.starter.tunnel.support;

import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.common.support.tunnel.TunnelView;
import com.chua.starter.tunnel.properties.TunnelProperties;
import com.chua.starter.tunnel.spi.TunnelProvider;
import com.chua.starter.tunnel.spi.TunnelProviderRegistry;
import com.chua.starter.tunnel.spi.TunnelSession;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内网穿透统一编排服务：映射的增删改查、按需启停、持久化与启动恢复。
 *
 * <p>改造前 ngrok / cloudflared 各有一个 {@code XxxTunnelService}，其中
 * {@code list/get/create/update/delete/stop/restoreAutoStart/toView/require}
 * 几乎逐字相同，真正不同的只有「怎么建立隧道」。现在这些公共逻辑只保留一份，
 * 差异全部下沉到 {@link TunnelProvider} 实现。</p>
 *
 * <p>运行态（会话、启动时间、最近错误）只存内存，不落盘——避免进程重启后把
 * 过期公网地址当成事实。例外是「所有权」：多实例共享同一映射目录（如本机
 * 8082/8083 两套后端）时，同一账号/域名只允许一条隧道被一个实例运行，
 * 因此启动成功的实例会把自身控制地址写入记录的 {@code owner} 字段，停止时清空；
 * 其他实例启动同一条隧道前先看该字段并尝试接管，从根上避免 ERR_NGROK_334
 * 这类「域名已被在线占用」冲突。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
public class TunnelService {

    /**
     * 统一配置。
     */
    private final TunnelProperties properties;

    /**
     * 提供方注册表。
     */
    private final TunnelProviderRegistry registry;

    /**
     * 映射存储器。
     */
    private final TunnelStore store;

    /**
     * 本实例控制地址（{@code http://host:port}，已去尾斜杠；空串表示不启用所有权）。
     */
    private final String selfBaseUrl;

    /**
     * 运行中的会话：映射 id → 会话。
     */
    private final Map<String, TunnelSession> sessions = new ConcurrentHashMap<>();

    /**
     * 启动时间：映射 id → 毫秒时间戳。
     */
    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();

    /**
     * 最近一次启动错误：映射 id → 错误信息。
     */
    private final Map<String, String> errors = new ConcurrentHashMap<>();

    /**
     * 运行期持有的记录锁：映射 id → 锁文件通道。
     *
     * <p>文件锁在整段运行期间持有（{@code <目录>/<id>.lock}），进程退出后由
     * 操作系统自动释放；其他实例 {@code tryLock} 失败即知「这条隧道正被谁运行着」。</p>
     */
    private final Map<String, FileChannel> locks = new ConcurrentHashMap<>();

    /**
     * 跨实例控制调用的连接超时（毫秒）：本地/内网控制面，超时越短越不会拖住页面。
     */
    private static final long CONTROL_CONNECT_TIMEOUT_MILLIS = 1500L;

    /**
     * 接管时等待对方释放文件锁的轮询次数（30 × 200ms ≈ 6s，覆盖正常停止的耗时）。
     */
    private static final int TAKEOVER_WAIT_ROUNDS = 30;

    /**
     * 接管时每轮轮询间隔（毫秒）。
     */
    private static final long TAKEOVER_WAIT_INTERVAL_MILLIS = 200L;

    /**
     * HTTP 2xx 成功区间下界。
     */
    private static final int HTTP_SUCCESS_MIN = 200;

    /**
     * HTTP 2xx 成功区间上界（不含）。
     */
    private static final int HTTP_SUCCESS_MAX_EXCLUSIVE = 300;

    /**
     * 跨实例控制调用的 HTTP 客户端（只用于请求对方停止隧道）。
     */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(CONTROL_CONNECT_TIMEOUT_MILLIS))
            .build();

    /**
     * 创建编排服务。
     *
     * <p>刻意不持有 SSE 推送器：本类不依赖 {@code spring-web}，非 Web 应用
     * （纯后台跑穿透）也能装配。推送由 Web 层的控制器按需创建。</p>
     *
     * @param properties  统一配置
     * @param registry    提供方注册表
     * @param selfBaseUrl 本实例控制地址；为空或空白时所有权机制不生效（退化为旧行为）
     */
    public TunnelService(TunnelProperties properties, TunnelProviderRegistry registry, String selfBaseUrl) {
        this.properties = properties;
        this.registry = registry;
        this.selfBaseUrl = normalizeBaseUrl(selfBaseUrl);
        this.store = new TunnelStore(properties, registry);
    }

    /**
     * 规整实例控制地址：去空白、去尾斜杠；空白返回空串。
     *
     * @param base 原始地址
     * @return 规整后的地址
     */
    private static String normalizeBaseUrl(String base) {
        if (base == null || base.isBlank()) {
            return "";
        }
        String trimmed = base.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /**
     * 全部映射（含运行态）。
     *
     * <p>排序口径：运行中的在最前，禁用的在最后，其余按创建时间先后排在中间；
     * 页面「链路卡」按这个顺序渲染，正在服务的映射永远在最上面。</p>
     *
     * @param providerFilter 仅该提供方；为空表示全部
     * @return 排好序的映射列表
     */
    public List<TunnelView> list(String providerFilter) {
        Comparator<TunnelView> order = Comparator
                .comparing((TunnelView view) -> !view.isRunning())
                .thenComparing(view -> !view.isEnabled())
                .thenComparingLong(TunnelView::getCreatedAt);
        return store.findAll(providerFilter).stream().map(this::toView).sorted(order).toList();
    }

    /**
     * 全部映射快照（供 SSE 推送使用，不过滤）。
     *
     * @return 映射列表
     */
    public List<TunnelView> listAll() {
        return list(null);
    }

    /**
     * 单条映射。
     *
     * @param id 标识
     * @return 映射
     */
    public TunnelView get(String id) {
        return toView(store.require(id));
    }

    /**
     * 新建映射（不自动启动）。
     *
     * @param input 页面提交的配置，必须带 {@code provider}
     * @return 保存后的映射
     */
    public TunnelView create(TunnelRecord input) {
        if (input == null) {
            throw new IllegalArgumentException("映射配置不能为空");
        }
        TunnelProvider provider = resolveCreateProvider(input);
        TunnelRecord record = new TunnelRecord();
        record.setProvider(provider.name());
        input.setProvider(provider.name());
        input.setOwner(null);
        applyProviderInput(provider, record, input);
        store.save(record);
        log.info("[Tunnel] 新增映射 {}（{} → 公网，提供方 {}）",
                record.getName(), record.getLocalPort(), record.getProvider());
        return toView(record);
    }

    /**
     * 更新映射。运行中会先停止再按新配置重启。
     *
     * <p>提供方不可变更：变更提供方等价于新建，允许原地改会让「已启动的会话
     * 属于旧实现」这种状态难以自洽。</p>
     *
     * @param id    标识
     * @param input 新配置
     * @return 保存后的映射
     */
    public TunnelView update(String id, TunnelRecord input) {
        TunnelRecord record = store.require(id);
        TunnelProvider provider = registry.require(record.getProvider());
        boolean running = sessions.containsKey(id);
        if (running) {
            stop(id);
        }
        input.setProvider(record.getProvider());
        input.setOwner(null);
        applyProviderInput(provider, record, input);
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
        store.require(id);
        stop(id);
        boolean removed = store.delete(id).isPresent();
        if (removed) {
            errors.remove(id);
            log.info("[Tunnel] 已删除映射 {}", id);
        }
        return removed;
    }

    /**
     * 启动映射（手动操作：若正被另一实例运行，自动接管——先请对方停止再启动）。
     *
     * @param id 标识
     * @return 启动后的映射
     */
    public TunnelView start(String id) {
        return start(id, true);
    }

    /**
     * 启动映射。
     *
     * @param id       标识
     * @param takeover 是否允许接管其他实例正在运行的同一映射。
     *                 手动启动为 true；启动恢复（autoStart）为 false，避免实例间互相抢域名。
     * @return 启动后的映射
     */
    private TunnelView start(String id, boolean takeover) {
        TunnelRecord record = store.require(id);
        if (sessions.containsKey(id)) {
            return toView(record);
        }
        TunnelProvider provider = registry.require(record.getProvider());
        errors.remove(id);

        if (!ownershipEnabled()) {
            return openUnlocked(record, provider);
        }

        // 手动启动：先请求当前 owner 停止，再尝试拿锁。兼容「对方仍是旧版 jar」
        // 的场景——旧实例不持锁，但停止接口同样有效，先停掉再启动即独占。
        if (takeover) {
            requestStopIfOther(record, record.getOwner());
        }

        FileChannel lock = acquireLock(record, takeover);
        if (lock == null) {
            // 锁被其他活实例持有且接管失败/不允许接管
            String owner = ownerOf(id);
            String where = owner == null || owner.isBlank() ? "另一实例" : owner;
            String hint = takeover ? "，对方未能在等待时间内释放" : "，等待对方停止后重试";
            errors.put(id, "隧道正被 " + where + " 运行" + hint);
            throw new IllegalStateException(provider.displayName() + " 隧道启动失败: " + errors.get(id));
        }
        try {
            // 持锁成功：声明所有权后再建立隧道，其他实例此时能看到 owner 并尝试接管
            record.setOwner(selfBaseUrl);
            store.save(record);
            TunnelSession session = provider.open(record);
            sessions.put(id, session);
            startedAt.put(id, System.currentTimeMillis());
            log.info("[Tunnel] 映射已启动 {}（{} → {}，提供方 {}，owner={}）",
                    record.getName(), record.getLocalPort(), session.publicUrls(), provider.name(), selfBaseUrl);
            return toView(store.require(id));
        } catch (RuntimeException e) {
            releaseLock(id, lock);
            TunnelRecord fresh = store.require(id);
            if (selfBaseUrl.equals(fresh.getOwner())) {
                fresh.setOwner(null);
                store.save(fresh);
            }
            errors.put(id, e.getMessage());
            throw new IllegalStateException(provider.displayName() + " 隧道启动失败: " + e.getMessage(), e);
        }
    }

    /**
     * 未启用所有权机制时的启动（旧行为：直接建立隧道，不做锁与归属）。
     *
     * @param record   记录
     * @param provider 提供方
     * @return 启动后的映射
     */
    private TunnelView openUnlocked(TunnelRecord record, TunnelProvider provider) {
        try {
            TunnelSession session = provider.open(record);
            sessions.put(record.getId(), session);
            startedAt.put(record.getId(), System.currentTimeMillis());
            log.info("[Tunnel] 映射已启动 {}（{} → {}，提供方 {}）",
                    record.getName(), record.getLocalPort(), session.publicUrls(), provider.name());
            return toView(store.require(record.getId()));
        } catch (RuntimeException e) {
            errors.put(record.getId(), e.getMessage());
            throw new IllegalStateException(provider.displayName() + " 隧道启动失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取记录锁：能拿到说明没有其他实例在运行这条隧道。
     *
     * <p>锁文件 {@code <提供方目录>/<id>.lock} 在整段运行期间持有，进程退出后由
     * 操作系统自动释放，因此「活着的占用者」用文件锁即可可靠识别，owner 字段只
     * 负责告诉我们是哪台实例（便于接管时调用对方停止）。</p>
     *
     * @param record   记录
     * @param takeover 占用时是否接管（请求对方停止后重试）
     * @return 持有的锁通道；未取得返回 {@code null}
     */
    private FileChannel acquireLock(TunnelRecord record, boolean takeover) {
        Path lockPath = lockPath(record);
        FileChannel channel;
        try {
            Files.createDirectories(lockPath.getParent());
            channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (Exception e) {
            log.warn("[Tunnel] 打开锁文件失败 {}: {}", lockPath, e.getMessage());
            return null;
        }
        if (tryLock(channel) != null) {
            locks.put(record.getId(), channel);
            return channel;
        }
        // 被其他实例占用
        if (!takeover) {
            log.info("[Tunnel] 映射 {} 正被其他实例运行，自动恢复跳过（不接管）", record.getName());
            closeQuietly(channel);
            return null;
        }
        String owner = ownerOf(record.getId());
        boolean stopRequested = requestStopIfOther(record, owner);
        // 等待对方释放（正常停止或进程退出都会释放文件锁）
        for (int i = 0; i < TAKEOVER_WAIT_ROUNDS; i++) {
            sleepQuietly(TAKEOVER_WAIT_INTERVAL_MILLIS);
            if (tryLock(channel) != null) {
                locks.put(record.getId(), channel);
                return channel;
            }
            // 对方刚拿锁还没写 owner 时，这里会动态发现并补发停止请求
            if (!stopRequested) {
                String live = ownerOf(record.getId());
                stopRequested = requestStopIfOther(record, live);
            }
        }
        log.warn("[Tunnel] 接管映射 {} 超时，owner={}", record.getName(), owner);
        closeQuietly(channel);
        return null;
    }

    /**
     * 若记录 owner 是其他实例则请求其停止。
     *
     * @param record 记录（仅日志用）
     * @param owner  当前 owner 地址
     * @return 是否已发出停止请求
     */
    private boolean requestStopIfOther(TunnelRecord record, String owner) {
        if (owner != null && !owner.isBlank() && !owner.equals(selfBaseUrl)) {
            log.info("[Tunnel] 接管映射 {}：请求 {} 停止", record.getName(), owner);
            requestStop(owner, record.getId());
            return true;
        }
        return false;
    }

    /**
     * 尝试获取非阻塞文件锁。
     *
     * @param channel 锁文件通道
     * @return 成功返回锁；失败或同 JVM 已持有返回 {@code null}
     */
    private static FileLock tryLock(FileChannel channel) {
        try {
            return channel.tryLock();
        } catch (Exception e) {
            log.debug("[Tunnel] 获取文件锁失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 释放运行期锁并关闭通道。
     *
     * @param id      映射标识
     * @param channel 锁文件通道
     */
    private void releaseLock(String id, FileChannel channel) {
        if (channel == null) {
            return;
        }
        locks.remove(id);
        closeQuietly(channel);
    }

    /**
     * 记录锁文件路径。
     *
     * @param record 记录
     * @return 锁文件路径
     */
    private Path lockPath(TunnelRecord record) {
        return store.of(record.getProvider()).getDir().resolve(record.getId() + ".lock");
    }

    /**
     * 当前记录的 owner（可能已被对方清空）。
     *
     * @param id 标识
     * @return owner 地址；无则 {@code null}
     */
    private String ownerOf(String id) {
        TunnelRecord record = store.find(id).orElse(null);
        return record == null ? null : record.getOwner();
    }

    /**
     * 请求指定实例停止隧道（跨实例控制，用于接管）。
     *
     * @param owner 对方实例控制地址
     * @param id    映射标识
     * @return 对方返回成功
     */
    private static boolean requestStop(String owner, String id) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(owner + "/ms/tunnel/" + id + "/stop"))
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= HTTP_SUCCESS_MIN
                    && response.statusCode() < HTTP_SUCCESS_MAX_EXCLUSIVE;
        } catch (Exception e) {
            log.debug("[Tunnel] 请求 {} 停止隧道 {} 失败: {}", owner, id, e.getMessage());
            return false;
        }
    }

    /**
     * 是否启用所有权机制。
     *
     * @return 启用了返回 true
     */
    private boolean ownershipEnabled() {
        return !selfBaseUrl.isEmpty();
    }

    /**
     * 停止映射（保留配置）。
     *
     * <p>本实例没在运行但记录 owner 指向其他实例时，也请求对方停止——「停止」
     * 语义是让这条隧道在任何实例上都不再运行，接管与删除共用同一口径。</p>
     *
     * @param id 标识
     * @return 停止后的映射
     */
    public TunnelView stop(String id) {
        TunnelRecord record = store.require(id);
        TunnelSession session = sessions.remove(id);
        startedAt.remove(id);
        if (session != null) {
            closeQuietly(session, record.getProvider());
            log.info("[Tunnel] 映射已停止 {}", record.getName());
        } else if (ownershipEnabled()) {
            String owner = record.getOwner();
            if (owner != null && !owner.isBlank() && !owner.equals(selfBaseUrl)) {
                log.info("[Tunnel] 停止远端隧道 {}（owner={}）", record.getName(), owner);
                requestStop(owner, id);
            }
        }
        FileChannel channel = locks.remove(id);
        closeQuietly(channel);
        if (ownershipEnabled() && selfBaseUrl.equals(record.getOwner())) {
            record.setOwner(null);
            store.save(record);
        }
        return toView(record);
    }

    /**
     * 全部提供方描述（含能力、支持模式），供页面渲染下拉。
     *
     * @return 描述列表
     */
    public List<Map<String, Object>> providers() {
        return registry.describeAll();
    }

    /**
     * 单个提供方的能力信息。
     *
     * @param provider 提供方名称
     * @return 能力信息
     */
    public Map<String, Object> capabilities(String provider) {
        return registry.require(provider).capabilities();
    }

    /**
     * 应用就绪后恢复「自动启动」的映射。
     *
     * <p>逐条独立 try：单条失败（凭据过期、端口占用）只记错误，不影响其余映射，
     * 也不阻塞应用启动。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreAutoStart() {
        if (!properties.isRestoreOnStartup()) {
            return;
        }
        for (TunnelRecord record : store.findAll(null)) {
            if (!record.isEnabled() || !record.isAutoStart()) {
                continue;
            }
            TunnelProvider provider = registry.find(record.getProvider()).orElse(null);
            if (provider == null || !provider.autoStartStored()) {
                continue;
            }
            try {
                // 启动恢复不接管：同一条隧道已被其他实例运行时跳过，避免互相抢域名
                start(record.getId(), false);
            } catch (RuntimeException e) {
                log.warn("[Tunnel] 恢复映射 {} 失败: {}", record.getName(), e.getMessage());
            }
        }
    }

    /**
     * 容器关闭时关闭全部会话并释放全部记录锁。
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, TunnelSession> entry : sessions.entrySet()) {
            closeQuietly(entry.getValue(), entry.getKey());
        }
        sessions.clear();
        startedAt.clear();
        for (FileChannel channel : locks.values()) {
            closeQuietly(channel);
        }
        locks.clear();
    }

    /**
     * 解析新建时使用的提供方。
     *
     * <p>未指定且只有一个已注册实现时自动选中它——这样单实现部署下页面不必
     * 传 provider，接口更省事；多实现时必须显式指定，避免误建。</p>
     *
     * @param input 页面提交
     * @return 提供方
     */
    private TunnelProvider resolveCreateProvider(TunnelRecord input) {
        String name = input.getProvider();
        if (name == null || name.isBlank()) {
            List<TunnelProvider> all = registry.all();
            if (all.size() == 1) {
                return all.get(0);
            }
            throw new IllegalArgumentException("请指定提供方（provider）：当前可选 " + registry.names());
        }
        return registry.require(name);
    }

    /**
     * 调用实现的校验逻辑；把 IllegalArgumentException 之外的类型统一包装，
     * 保证 REST 层拿到的都是可读消息。
     *
     * @param provider 提供方
     * @param target   目标记录
     * @param input    页面提交
     */
    private void applyProviderInput(TunnelProvider provider, TunnelRecord target, TunnelRecord input) {
        // 先做公共校验与公共字段：端口非法/空输入应立刻失败，
        // 不必等到实现里去判断（实现只需关心自己的模式与专属字段）。
        TunnelInputs.requirePort(input);
        provider.applyInput(target, input);
        // 实现可能漏写 provider，这里兜底，避免记录落到错误目录。
        target.setProvider(provider.name());
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
        TunnelSession session = sessions.get(record.getId());
        view.setRunning(session != null);
        List<String> urls;
        try {
            urls = session == null ? List.of() : session.publicUrls();
        } catch (RuntimeException e) {
            urls = List.of();
            log.debug("[Tunnel] 读取公网地址失败 {}: {}", record.getId(), e.getMessage());
        }
        view.setPublicUrls(urls == null ? List.of() : urls);
        view.setStartedAt(startedAt.get(record.getId()));
        view.setError(errors.get(record.getId()));
        view.setOwner(record.getOwner());
        return view;
    }

    /**
     * 关闭会话并吞掉异常。
     *
     * @param session 会话
     * @param label   标识（仅日志用）
     */
    private static void closeQuietly(TunnelSession session, String label) {
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (Exception e) {
            log.warn("[Tunnel] 关闭隧道异常 {}: {}", label, e.getMessage());
        }
    }

    /**
     * 关闭锁通道并吞掉异常（关闭通道即释放文件锁）。
     *
     * @param channel 锁文件通道
     */
    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (Exception e) {
            log.debug("[Tunnel] 关闭锁文件异常: {}", e.getMessage());
        }
    }

    /**
     * 短睡眠（仅用于等待对方释放锁），中断时直接返回。
     *
     * @param millis 毫秒
     */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
