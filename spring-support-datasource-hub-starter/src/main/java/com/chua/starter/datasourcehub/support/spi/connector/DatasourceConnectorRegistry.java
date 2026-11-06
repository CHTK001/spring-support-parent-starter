// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.spi.connector;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.spi.connector.impl.AppDatasourceConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据源连接扩展点路由器：宿主侧唯一「该用哪个实现」的判断点。
 *
 * <p><b>与 {@code MiddlewareClientRegistry} 同构</b>：每次现取而不是构造期快照，
 * 这样 OSGi bundle 在运行期装上/卸掉都能立刻生效；单个实现初始化失败只跳过它，
 * 不让整条链路不可用。</p>
 *
 * <p><b>分发顺序</b>：按 {@link ServiceDefinition#getOrder()} 降序（数值大的优先）
 * 逐个调用 {@link DatasourceConnector#supports(String)}，第一个认领的胜出。
 * 应用主库由 {@code app} 独占、合并源先于 JDBC 族，顺序已由各实现的 order 固定，
 * 新增实现只需给出更小的 order，不必改这里。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
public class DatasourceConnectorRegistry {

    /**
     * 扩展点提供者
     */
    private final ServiceProvider<DatasourceConnector> providers =
            ServiceProvider.of(DatasourceConnector.class);

    /**
     * 已装配的实现（按 {@link ServiceDefinition} 优先级排序）。
     *
     * @return 实现列表；一个都没有时为空列表
     */
    public List<DatasourceConnector> ordered() {
        List<DatasourceConnector> result = new ArrayList<>();
        try {
            for (ServiceDefinition definition : providers.getPriorityServiceDefinitions()) {
                DatasourceConnector connector;
                try {
                    connector = definition.getObj(providers.getServiceAutowire());
                } catch (Throwable t) {
                    // 单个实现初始化失败不应让整条链路不可用（bundle 装了一半时很常见）
                    log.warn("[datasource-hub] 跳过初始化失败的连接扩展: {}",
                            String.valueOf(definition.getName()), t);
                    continue;
                }
                if (connector != null) {
                    result.add(connector);
                }
            }
        } catch (Throwable t) {
            log.warn("[datasource-hub] 扫描 DatasourceConnector 扩展失败: {}", t.getMessage());
        }
        return result;
    }

    /**
     * 按类型取实现。
     *
     * @param type 数据源类型；null 或空按应用主库处理
     * @return 实现；一个都不认领时返回 app 实现（由它报告「未配置默认数据源」）
     */
    public DatasourceConnector resolve(String type) {
        String normalized = type == null || type.isBlank()
                ? AppDatasourceConnector.TYPE_APP : type.trim().toLowerCase(java.util.Locale.ROOT);
        DatasourceConnector fallback = null;
        for (DatasourceConnector connector : ordered()) {
            if (fallback == null && AppDatasourceConnector.TYPE_APP.equals(connector.name())) {
                fallback = connector;
            }
            if (connector.supports(normalized)) {
                return connector;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("没有可用的数据源连接实现: " + normalized);
    }

    /**
     * 按台账记录取实现。
     *
     * <p>ID 为 null 或 {@code 0}，以及记录缺失时都按应用主库处理：这是数据源中心
     * 既有的对外约定（{@code DEFAULT_DATASOURCE_ID}），调用方不需要自己判断。</p>
     *
     * @param datasourceId 数据源 ID
     * @param row          台账记录；可为 null
     * @return 实现
     */
    public DatasourceConnector resolve(Long datasourceId, MsDatasource row) {
        if (row != null && row.getDatasourceType() != null && !row.getDatasourceType().isBlank()) {
            return resolve(row.getDatasourceType());
        }
        return resolve((String) null);
    }

    /**
     * 清退缓存连接：交给每一个实现自行处理。
     *
     * <p><b>为什么不是「先解析再清退」</b>：删除数据源时台账行已经先被删掉，
     * 此时按类型解析根本拿不到类型，会被当成应用主库，于是 JDBC 那份缓存连接
     * 谁都不清 —— 表现为「改了连接参数但还在用旧连接」或删了数据源 socket 不释放。
     * 清退是幂等且廉价的（没有缓存的实现直接忽略），逐个通知才是正确口径。</p>
     *
     * @param datasourceId 数据源 ID
     */
    public void evict(Long datasourceId) {
        if (datasourceId == null) {
            return;
        }
        for (DatasourceConnector connector : ordered()) {
            try {
                connector.evict(datasourceId);
            } catch (RuntimeException e) {
                log.debug("[datasource-hub] 清退连接缓存失败 id={} connector={} reason={}",
                        datasourceId, connector.name(), e.getMessage());
            }
        }
    }
}
