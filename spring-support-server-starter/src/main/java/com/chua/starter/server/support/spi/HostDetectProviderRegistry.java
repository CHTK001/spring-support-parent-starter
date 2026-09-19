package com.chua.starter.server.support.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主机硬件探测提供者注册中心
 * <p>
 * 启动时扫描全部 {@link HostDetectProvider} Bean，按系统类型建索引，
 * {@code ServerHostServiceImpl.detectHardware} 据此路由到具体探测实现。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class HostDetectProviderRegistry {

    /** 系统类型 -> 探测实现索引 */
    private final Map<String, HostDetectProvider> index = new ConcurrentHashMap<>();

    /**
    * 构造：注册全部探测提供者
    *
    * @param providers 探测提供者列表
    */
    @Autowired
    public HostDetectProviderRegistry(List<HostDetectProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            log.warn("[detect] 未发现 HostDetectProvider 实现");
            return;
        }
        for (HostDetectProvider provider : providers) {
            String osType = provider.osType();
            if (osType == null || osType.isBlank()) {
                continue;
            }
            index.put(osType.toUpperCase(), provider);
            log.info("[detect] 注册探测 Provider: osType={}, impl={}", osType, provider.getClass().getSimpleName());
        }
    }

    /**
     * 按系统类型解析探测提供者
     *
     * @param osType 系统类型（LINUX/MACOS/WINDOWS）
     * @return Optional，可能为空
     */
    public Optional<HostDetectProvider> resolve(String osType) {
        if (osType == null || osType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(index.get(osType.toUpperCase()));
    }

    /**
     * 是否支持指定系统类型
     *
     * @param osType 系统类型
     * @return 是否支持
     */
    public boolean supports(String osType) {
        return resolve(osType).isPresent();
    }
}
