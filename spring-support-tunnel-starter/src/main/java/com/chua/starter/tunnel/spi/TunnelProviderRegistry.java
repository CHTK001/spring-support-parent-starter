package com.chua.starter.tunnel.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 提供方注册表：从 Spring 容器收集全部 {@link TunnelProvider} 并按名称索引。
 *
 * <p>收集规则与 queue 的 SPI 一致——「引入哪个实现 jar 就有哪个提供方」：
 * 容器里有几个 {@code TunnelProvider} Bean 就有几个提供方，不需要额外的
 * 注册表配置。名称重复会在启动时直接报错，避免两个实现争抢同一份
 * {@code provider} 字段导致映射归属错乱。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
public class TunnelProviderRegistry {

    /**
     * 名称 → 提供方（保持注入顺序，页面下拉顺序稳定）。
     */
    private final Map<String, TunnelProvider> providers = new LinkedHashMap<>();

    /**
     * 构建注册表。
     *
     * @param discovered Spring 容器里的全部提供方，可为空集合
     */
    public TunnelProviderRegistry(List<TunnelProvider> discovered) {
        List<TunnelProvider> list = discovered == null ? List.of() : discovered;
        for (TunnelProvider provider : list) {
            String name = provider.name();
            if (!StringUtils.hasText(name)) {
                throw new IllegalStateException("TunnelProvider 的 name() 不能为空: "
                        + provider.getClass().getName());
            }
            String key = name.trim().toLowerCase(Locale.ROOT);
            TunnelProvider exists = providers.putIfAbsent(key, provider);
            if (exists != null) {
                throw new IllegalStateException("TunnelProvider 名称重复: " + key
                        + "（" + exists.getClass().getName() + " 与 "
                        + provider.getClass().getName() + "）");
            }
            log.info("[Tunnel] 注册提供方 {}（{}），可用={}",
                    key, provider.getClass().getSimpleName(), provider.isAvailable());
        }
        if (providers.isEmpty()) {
            log.warn("[Tunnel] 未发现任何 TunnelProvider 实现：请引入 spring-support-tunnel-ngrok-starter "
                    + "或 spring-support-tunnel-cloudflared-starter");
        }
    }

    /**
     * 按名称查找。
     *
     * @param name 名称（大小写不敏感）
     * @return 提供方
     */
    public Optional<TunnelProvider> find(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * 按名称查找，不存在时抛业务异常。
     *
     * @param name 名称
     * @return 提供方
     * @throws IllegalArgumentException 不存在
     */
    public TunnelProvider require(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException(
                "未知的内网穿透提供方: " + name + "，当前已注册: " + providers.keySet()));
    }

    /**
     * 全部提供方（按注册顺序）。
     *
     * @return 提供方列表
     */
    public List<TunnelProvider> all() {
        return List.copyOf(providers.values());
    }

    /**
     * 全部提供方名称。
     *
     * @return 名称列表
     */
    public List<String> names() {
        return List.copyOf(providers.keySet());
    }

    /**
     * 是否为空。
     *
     * @return 没有实现返回 true
     */
    public boolean isEmpty() {
        return providers.isEmpty();
    }

    /**
     * 提供方装饰：补上 name / displayName，便于实现类少写样板。
     *
     * @param provider 提供方
     * @return 只读视图
     */
    static Map<String, Object> describe(TunnelProvider provider) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", provider.name());
        row.put("displayName", provider.displayName());
        row.put("available", provider.isAvailable());
        row.put("modes", provider.supportedModes());
        row.put("defaultMode", provider.defaultMode());
        row.put("capabilities", provider.capabilities() == null
                ? Collections.emptyMap() : provider.capabilities());
        return row;
    }

    /**
     * 批量描述，供页面渲染提供方下拉与能力提示。
     *
     * @return 描述列表
     */
    public List<Map<String, Object>> describeAll() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TunnelProvider provider : providers.values()) {
            rows.add(describe(provider));
        }
        return rows;
    }
}
