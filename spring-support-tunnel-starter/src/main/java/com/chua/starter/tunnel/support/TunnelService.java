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
 * 过期公网地址当成事实。</p>
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
     * 创建编排服务。
     *
     * <p>刻意不持有 SSE 推送器：本类不依赖 {@code spring-web}，非 Web 应用
     * （纯后台跑穿透）也能装配。推送由 Web 层的控制器按需创建。</p>
     *
     * @param properties 统一配置
     * @param registry   提供方注册表
     */
    public TunnelService(TunnelProperties properties, TunnelProviderRegistry registry) {
        this.properties = properties;
        this.registry = registry;
        this.store = new TunnelStore(properties, registry);
    }

    /**
     * 全部映射（含运行态）。
     *
     * @param providerFilter 仅该提供方；为空表示全部
     * @return 映射列表
     */
    public List<TunnelView> list(String providerFilter) {
        return store.findAll(providerFilter).stream().map(this::toView).toList();
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
     * 启动映射。
     *
     * @param id 标识
     * @return 启动后的映射
     */
    public TunnelView start(String id) {
        TunnelRecord record = store.require(id);
        if (sessions.containsKey(id)) {
            return toView(record);
        }
        TunnelProvider provider = registry.require(record.getProvider());
        errors.remove(id);
        try {
            // open() 在 try 内：凭据缺失、二进制不存在等前置校验都在实现内部抛出，
            // 必须写进 errors，否则页面「错误信息」列永远是空的。
            TunnelSession session = provider.open(record);
            sessions.put(id, session);
            startedAt.put(id, System.currentTimeMillis());
            log.info("[Tunnel] 映射已启动 {}（{} → {}，提供方 {}）",
                    record.getName(), record.getLocalPort(), session.publicUrls(), provider.name());
            return toView(record);
        } catch (RuntimeException e) {
            errors.put(id, e.getMessage());
            throw new IllegalStateException(provider.displayName() + " 隧道启动失败: " + e.getMessage(), e);
        }
    }

    /**
     * 停止映射（保留配置）。
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
                start(record.getId());
            } catch (RuntimeException e) {
                log.warn("[Tunnel] 恢复映射 {} 失败: {}", record.getName(), e.getMessage());
            }
        }
    }

    /**
     * 容器关闭时关闭全部会话。
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, TunnelSession> entry : sessions.entrySet()) {
            closeQuietly(entry.getValue(), entry.getKey());
        }
        sessions.clear();
        startedAt.clear();
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
}
