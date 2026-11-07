package com.chua.starter.template.support.service;

import com.chua.starter.template.support.model.TemplateProviderInfo;
import com.chua.starter.template.support.model.TemplateProviderSyncResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 来源提供商注册表
 *
 * <p>收割上下文里全部 {@link ProviderSource}，按提供商 ID 建索引，
 * 供模板服务在列提供商、同步某个提供商时反查归属模块。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Slf4j
@Component
public class ProviderSourceRegistry {

    /** 提供商 ID → 上报它的来源 */
    private final Map<String, ProviderSource> ownerMap = new LinkedHashMap<>();

    /** 来源标识 → 来源 */
    private final Map<String, ProviderSource> sourceMap = new LinkedHashMap<>();

    public ProviderSourceRegistry(List<ProviderSource> sources) {
        if (sources == null || sources.isEmpty()) {
            log.info("[Template] 未发现来源提供商上报者，左栏只展示库内提供商");
            return;
        }
        for (ProviderSource source : sources) {
            if (source == null || source.source() == null) {
                continue;
            }
            String key = source.source().trim();
            if (sourceMap.putIfAbsent(key, source) != null) {
                log.warn("[Template] 来源 {} 已注册提供商上报者，忽略重复: {}",
                        key, source.getClass().getName());
                continue;
            }
            List<TemplateProviderInfo> reported = source.providers();
            if (reported == null) {
                continue;
            }
            for (TemplateProviderInfo info : reported) {
                if (info != null && info.getId() != null) {
                    ownerMap.put(info.getId(), source);
                }
            }
            log.info("[Template] 注册来源提供商上报者: source={} class={}",
                    key, source.getClass().getName());
        }
    }

    /**
     * 汇总全部来源上报的提供商
     *
     * @return 提供商列表
     */
    public List<TemplateProviderInfo> allProviders() {
        List<TemplateProviderInfo> result = new ArrayList<>();
        for (ProviderSource source : sourceMap.values()) {
            List<TemplateProviderInfo> reported = source.providers();
            if (reported != null) {
                result.addAll(reported);
            }
        }
        return result;
    }

    /**
     * 反查某个来源标识由哪个来源模块维护
     *
     * <p>与 {@link #ownerOf(String)} 的区别：这条按「来源」（message / paper）查，
     * 用于模板详情这种手上只有 {@code template_info_source} 的场景。</p>
     *
     * @param source 来源标识
     * @return 来源，未注册返回 {@code null}
     */
    public ProviderSource sourceOf(String source) {
        return source == null ? null : sourceMap.get(source.trim());
    }

    /**
     * 反查某个提供商由哪个来源维护
     *
     * @param providerId 提供商 ID
     * @return 来源，库内自建提供商返回 {@code null}
     */
    public ProviderSource ownerOf(String providerId) {
        return providerId == null ? null : ownerMap.get(providerId);
    }

    /**
     * 同步某个提供商的模板（转交给维护它的来源模块）
     *
     * @param providerId 提供商 ID
     * @return 同步结果；库内自建提供商返回零变更
     */
    public TemplateProviderSyncResult sync(String providerId) {
        ProviderSource owner = ownerOf(providerId);
        if (owner == null) {
            return new TemplateProviderSyncResult();
        }
        return owner.sync(providerId);
    }
}
