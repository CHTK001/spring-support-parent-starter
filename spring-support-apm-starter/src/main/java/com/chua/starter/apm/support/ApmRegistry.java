package com.chua.starter.apm.support;

import com.chua.common.support.apm.OtnlEngine;
import com.chua.common.support.apm.model.OtnlStatus;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.apm.support.model.ApmDescriptor;
import com.chua.starter.apm.support.properties.ApmProperties;
import com.chua.starter.apm.support.spi.ApmProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * APM 引擎注册表：自动探测「当前系统有没有 APM、是哪一个」。
 * <p>
 * 探测规则（与需求 1.5 一致）：
 * </p>
 * <ol>
 *   <li>扫描 {@link ApmProvider} 的 SPI 扩展，按 {@link ApmProvider#order()} 升序；</li>
 *   <li>配置了 {@code plugin.apm.type} 时只认指定的那一个（不可用也照实报告原因）；</li>
 *   <li>未配置时取<b>第一个可用</b>的提供者 —— 例如同一台机器既挂了 SkyWalking agent
 *       又有 hotspot agent，则以 SkyWalking 为准（order 更小）；</li>
 *   <li>一个都不可用时不抛异常，返回「无 APM」描述，前端渲染降级视图。</li>
 * </ol>
 * <p>
 * 探测结果缓存 {@code plugin.apm.detectInterval} 毫秒，前端轮询状态不会把后端探活打穿。
 * </p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class ApmRegistry implements AutoCloseable {

    private final ApmProperties properties;

    private volatile List<ApmProvider> candidates = List.of();
    private volatile ApmProvider current;
    private volatile boolean detected;
    private volatile long detectedAt;

    /**
     * 构造注册表
     *
     * @param properties 配置
     */
    public ApmRegistry(ApmProperties properties) {
        this.properties = properties;
    }

    /**
     * 重新探测（忽略缓存）
     */
    public synchronized void detect() {
        List<ApmProvider> providers = new ArrayList<>();
        try {
            ServiceProvider.of(ApmProvider.class).forEach((name, provider) -> providers.add(provider));
        } catch (Throwable e) {
            log.debug("[APM] 扫描 ApmProvider 扩展失败: {}", e.getMessage());
        }
        providers.sort(Comparator.comparingInt(ApmProvider::order));
        candidates = List.copyOf(providers);

        ApmProvider selected = null;
        String required = properties.getType();
        if (required != null && !required.isBlank()) {
            for (ApmProvider provider : providers) {
                if (required.equalsIgnoreCase(provider.name())) {
                    selected = provider.isAvailable() ? provider : null;
                    if (selected == null) {
                        log.warn("[APM] 指定引擎 {} 不可用: {}", provider.name(), provider.status().message());
                    }
                    break;
                }
            }
            if (selected == null && providers.stream().noneMatch(p -> required.equalsIgnoreCase(p.name()))) {
                log.warn("[APM] 指定引擎 {} 未引入，请检查依赖坐标", required);
            }
        } else {
            for (ApmProvider provider : providers) {
                if (provider.isAvailable()) {
                    selected = provider;
                    break;
                }
            }
        }
        current = selected;
        detected = true;
        detectedAt = System.currentTimeMillis();

        if (selected == null) {
            log.info("[APM] 未探测到可用 APM 引擎（候选 {} 个），前端将显示降级视图", candidates.size());
        } else {
            OtnlStatus status = selected.status();
            log.info("[APM] 选中引擎 {}（版本 {}，模式 {}，后端 {}）",
                    selected.name(), status.version(), selected.mode(), status.endpoint());
        }
    }

    /**
     * 当前选中的提供者
     *
     * @return 提供者，无可用 APM 时为空
     */
    public Optional<ApmProvider> provider() {
        ensureDetected();
        return Optional.ofNullable(current);
    }

    /**
     * 当前选中的引擎
     *
     * @return 引擎，无可用 APM 时返回 null
     */
    public OtnlEngine engine() {
        return provider().map(ApmProvider::engine).orElse(null);
    }

    /**
     * 全部候选提供者
     *
     * @return 候选列表（按优先级升序）
     */
    public List<ApmProvider> candidates() {
        ensureDetected();
        return candidates;
    }

    /**
     * 当前状态描述（供 {@code /status} 与前端首屏判断用）
     *
     * @return 描述
     */
    public ApmDescriptor describe() {
        ensureDetected();
        List<ApmDescriptor.ApmCandidate> list = new ArrayList<>(candidates.size());
        for (ApmProvider provider : candidates) {
            OtnlStatus status = provider.status();
            list.add(new ApmDescriptor.ApmCandidate(provider.name(), status.available(),
                    provider.mode(), status.version(), status.endpoint(), status.message()));
        }
        if (!properties.isEnabled()) {
            // 关掉就是关掉：present 仍为 true 会让前端渲染观测页，然后每个接口都报「模块已关闭」，
            // 用户看到的是「页面在、数据没了」。这里提前降级，原因由 absenceReason() 给。
            return ApmDescriptor.absent(false, absenceReason(), list);
        }
        ApmProvider selected = current;
        if (selected == null) {
            return ApmDescriptor.absent(properties.isEnabled(), absenceReason(), list);
        }
        OtnlStatus status = selected.status();
        return new ApmDescriptor(status.available(), properties.isEnabled(), selected.name(),
                status.version(), selected.mode(), status.endpoint(), status.capabilities(),
                status.message(), list, System.currentTimeMillis());
    }

    /**
     * 无可用 APM 时的原因说明（直接回显给用户）
     *
     * @return 说明
     */
    private String absenceReason() {
        if (!properties.isEnabled()) {
            return "APM 模块已关闭（plugin.apm.enabled=false）";
        }
        if (candidates.isEmpty()) {
            return "当前服务器未引入任何 APM 实现模块（可选：apm-skywalking / apm-hotspot）";
        }
        String required = properties.getType();
        if (required != null && !required.isBlank()) {
            return "配置指定引擎 " + required + " 不可用";
        }
        StringBuilder builder = new StringBuilder("已探测 ")
                .append(candidates.size()).append(" 个 APM 引擎，但均不可用：");
        for (int i = 0; i < candidates.size(); i++) {
            ApmProvider provider = candidates.get(i);
            if (i > 0) {
                builder.append("；");
            }
            builder.append(provider.name()).append("（").append(provider.status().message()).append("）");
        }
        return builder.toString();
    }

    /**
     * 缓存失效时重新探测
     */
    private void ensureDetected() {
        long now = System.currentTimeMillis();
        long ttl = Math.max(1_000L, properties.getDetectInterval());
        if (!detected || now - detectedAt > ttl) {
            detect();
        }
    }

    @Override
    public void close() {
        for (ApmProvider provider : candidates) {
            try {
                provider.close();
            } catch (Exception e) {
                log.debug("[APM] 关闭引擎 {} 失败: {}", provider.name(), e.getMessage());
            }
        }
        candidates = List.of();
        current = null;
        detected = false;
    }
}
