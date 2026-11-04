package com.chua.starter.tunnel.support;

import com.chua.starter.common.support.tunnel.TunnelFileStore;
import com.chua.starter.common.support.tunnel.TunnelRecord;
import com.chua.starter.tunnel.properties.TunnelProperties;
import com.chua.starter.tunnel.spi.TunnelProvider;
import com.chua.starter.tunnel.spi.TunnelProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多提供方共用的映射存储器。
 *
 * <p>底层仍复用 {@link TunnelFileStore}（一条映射一个 JSON 文件），本类只负责
 * 「按提供方分目录」与「跨提供方按 id 检索」：</p>
 * <ul>
 *   <li>目录布局：{@code <根目录>/<provider>/<id>.json}，与改造前的
 *       {@code ~/.chua/tunnel/ngrok}、{@code ~/.chua/tunnel/cloudflared} 完全一致，
 *       升级后历史映射继续可用；</li>
 *   <li>提供方可通过 {@link TunnelProvider#storeDir()} 覆盖自己的目录；</li>
 *   <li>映射 id 是服务端生成的 UUID，全局唯一，因此「按 id 操作」不需要再带
 *       provider 前缀——这是统一入口能做到「一个 id 走通全部接口」的前提。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
public class TunnelStore {

    /**
     * 统一配置。
     */
    private final TunnelProperties properties;

    /**
     * 提供方注册表（决定有哪些子目录）。
     */
    private final TunnelProviderRegistry registry;

    /**
     * JSON 序列化器。
     */
    private final ObjectMapper mapper;

    /**
     * provider → 文件存储器（懒创建）。
     */
    private final Map<String, TunnelFileStore> stores = new ConcurrentHashMap<>();

    /**
     * 创建存储器。
     *
     * @param properties 统一配置
     * @param registry   提供方注册表
     */
    public TunnelStore(TunnelProperties properties, TunnelProviderRegistry registry) {
        this.properties = properties;
        this.registry = registry;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 取某个提供方的文件存储器（懒创建并加载磁盘已有记录）。
     *
     * @param providerName 提供方名称
     * @return 文件存储器
     */
    public TunnelFileStore of(String providerName) {
        return stores.computeIfAbsent(providerName, key -> {
            Path dir = resolveDir(key);
            TunnelFileStore store = new TunnelFileStore(dir, mapper);
            log.info("[Tunnel] 提供方 {} 的映射目录: {}（已加载 {} 条）",
                    key, store.getDir(), store.findAll().size());
            return store;
        });
    }

    /**
     * 解析某个提供方的存储目录。
     *
     * @param providerName 提供方名称
     * @return 目录
     */
    private Path resolveDir(String providerName) {
        Optional<TunnelProvider> provider = registry.find(providerName);
        String override = provider.map(TunnelProvider::storeDir).orElse(null);
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        return Path.of(properties.resolveStoreDir(), providerName);
    }

    /**
     * 全部映射（跨提供方），按更新时间倒序。
     *
     * @param providerFilter 仅返回该提供方的记录；为空表示不过滤
     * @return 记录列表
     */
    public List<TunnelRecord> findAll(String providerFilter) {
        List<TunnelRecord> all = new ArrayList<>();
        if (providerFilter != null && !providerFilter.isBlank()) {
            all.addAll(of(providerFilter.trim()).findAll());
        } else {
            for (String name : registry.names()) {
                all.addAll(of(name).findAll());
            }
        }
        all.sort((a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return all;
    }

    /**
     * 按 id 跨提供方查找。
     *
     * @param id 标识
     * @return 记录
     */
    public Optional<TunnelRecord> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        for (String name : registry.names()) {
            Optional<TunnelRecord> hit = of(name).find(id);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    /**
     * 按 id 跨提供方查找，不存在时抛业务异常。
     *
     * @param id 标识
     * @return 记录
     */
    public TunnelRecord require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("映射不存在: " + id));
    }

    /**
     * 保存记录到其所属提供方的目录。
     *
     * @param record 记录
     * @return 保存后的记录
     */
    public TunnelRecord save(TunnelRecord record) {
        String provider = record.getProvider();
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("映射缺少提供方（provider）");
        }
        return of(provider.trim()).save(record);
    }

    /**
     * 删除记录。
     *
     * @param id 标识
     * @return 被删除的记录
     */
    public Optional<TunnelRecord> delete(String id) {
        Optional<TunnelRecord> found = find(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return of(found.get().getProvider()).delete(id);
    }
}
