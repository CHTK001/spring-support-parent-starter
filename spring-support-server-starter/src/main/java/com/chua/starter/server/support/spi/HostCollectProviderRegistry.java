package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.enums.HostCollectType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
* 主机采集 提供者 注册中心
* <p>
* 启动时扫描全部 {@link HostCollectProvider} Bean，按 {@link HostCollectType} 建索引，
* 调度器 / Agent 上报端点据此路由到具体采集实现。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Component
public class HostCollectProviderRegistry {

    /** 采集方式 -> 实现索引 */
    private final Map<HostCollectType, HostCollectProvider> index = new EnumMap<>(HostCollectType.class);

    /**
    * 构造：注册全部 提供者
    *
    * @param providers 提供者 列表
    */
    @Autowired
    public HostCollectProviderRegistry(List<HostCollectProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            log.warn("[collect] 未发现 HostCollectProvider 实现");
            return;
        }
        for (HostCollectProvider provider : providers) {
            HostCollectType type = provider.type();
            if (type == null) {
                continue;
            }
            index.put(type, provider);
            log.info("[collect] 注册采集 Provider: type={}, impl={}", type, provider.getClass().getSimpleName());
        }
    }

    /**
    * 解析指定采集方式的 提供者
    *
    * @param type 采集方式
    * @return Optional，可能为空
    */
    public Optional<HostCollectProvider> resolve(HostCollectType type) {
        if (type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(index.get(type));
    }

    /**
    * 所有已注册采集方式
    *
    * @return 不可变视图
    */
    public Map<HostCollectType, String> describe() {
        Map<HostCollectType, String> out = new EnumMap<>(HostCollectType.class);
        index.forEach((t, p) -> out.put(t, p.getClass().getSimpleName()));
        return out;
    }
}
