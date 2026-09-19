package com.chua.starter.eduai.collect.support;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.eduai.collect.spi.QuestionSourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 采集数据源注册表：经 common-starter 自研 SPI 体系（{@code ServiceProvider}）加载
 * {@code META-INF/extensions} 注册的全部 {@link QuestionSourceProvider} 实现，按 type 建索引。
 * <p>
 * 第三方扩展只需在自己 jar 内实现接口并写同名 extensions 文件，零侵入 eduai。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
public class QuestionSourceProviderRegistry {

    /**
     * type（大写）到 Provider 的索引
     */
    private final Map<String, QuestionSourceProvider> providers = new LinkedHashMap<>();

    /**
     * 构造并经 SPI 建索引。
     */
    public QuestionSourceProviderRegistry() {
        try {
            ServiceProvider.of(QuestionSourceProvider.class).collect(this::register);
        } catch (Exception e) {
            log.error("eduai 采集 SPI 加载失败", e);
        }
        log.info("题库采集数据源已加载: {}", providers.keySet());
    }

    /**
     * 按任务的来源类型取 Provider。
     *
     * @param sourceType 来源类型
     * @return Provider，未注册时为 null
     */
    public QuestionSourceProvider find(String sourceType) {
        return sourceType == null ? null : providers.get(sourceType.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 当前可用来源类型清单（供前端下拉/校验）。
     *
     * @return 类型集合
     */
    public Set<String> availableTypes() {
        return providers.keySet();
    }

    private void register(QuestionSourceProvider provider) {
        if (provider == null || provider.type() == null || provider.type().isBlank()) {
            return;
        }
        String key = provider.type().trim().toUpperCase(Locale.ROOT);
        QuestionSourceProvider exists = providers.get(key);
        if (exists != null && exists.getClass() != provider.getClass()) {
            log.warn("采集数据源类型重复, 保留先到实现: type={} exists={} ignored={}",
                    key, exists.getClass().getName(), provider.getClass().getName());
            return;
        }
        providers.putIfAbsent(key, provider);
    }
}
