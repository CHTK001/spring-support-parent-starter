package com.chua.starter.datapool.support.middleware;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.datasource.support.middleware.MiddlewareNotInstalledException;
import com.chua.datasource.support.middleware.mongodb.MongoManageProvider;
import com.chua.datasource.support.middleware.mongodb.MongoManageTarget;
import com.chua.starter.datapool.support.model.DataSourceVO;
import com.chua.starter.datapool.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datapool.support.service.DatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MongoDB 管理面取用入口：把「按 ID 取数据源 → 转连接目标 → 找 SPI 实现」收敛到一处。
 *
 * <p>本模块<b>不</b>依赖 MongoDB 官方驱动：管理实现由 {@code utils-support-mongodb-starter}
 * （或同等的 OSGi bundle）提供，未安装时抛 {@link MiddlewareNotInstalledException}，
 * 由控制层转成「功能未开放」提示，而不是编译期把驱动拖进数据源中心。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoManageSupport {

    /**
     * 数据源类型标识
     */
    public static final String DS_TYPE = "mongodb";

    /**
     * 数据源服务
     */
    private final DatasourceService datasourceService;

    /**
     * SPI 提供方
     */
    private final ServiceProvider<MongoManageProvider> providers =
            ServiceProvider.of(MongoManageProvider.class);

    /**
     * 取管理面实现。
     *
     * <p>每次现取而不是构造期快照：OSGi bundle 可以运行期安装/卸载，
     * 快照会导致「装上后用不了、卸掉后还在」。</p>
     *
     * @return 实现
     * @throws MiddlewareNotInstalledException 未安装实现
     */
    public MongoManageProvider provider() {
        MongoManageProvider impl;
        try {
            impl = providers.getExtension(MongoManageProvider.SPI_NAME);
        } catch (RuntimeException e) {
            log.debug("[mongodb] SPI 实现取用失败: {}", e.getMessage());
            impl = null;
        }
        if (impl == null) {
            throw new MiddlewareNotInstalledException(DS_TYPE);
        }
        return impl;
    }

    /**
     * 按数据源 ID 取连接目标（ID 为空时退化为第一个 mongodb 数据源）。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 连接目标
     */
    public MongoManageTarget target(Long datasourceId) {
        return toTarget(MiddlewareDatasources.require(datasourceService, datasourceId, DS_TYPE));
    }

    /**
     * 解析连通性测试目标：给了地址就即席探测，否则按登记的数据源探测。
     *
     * @param request 测试载荷，可为空
     * @return 连接目标
     */
    public MongoManageTarget testTarget(MiddlewareTestDTO request) {
        if (request == null || request.host() == null || request.host().isBlank()) {
            return target(request == null ? null : request.datasourceId());
        }
        if (request.datasourceId() != null && request.datasourceId() > 0L) {
            return target(request.datasourceId());
        }
        // 即席探测：contextPath 借道承载默认逻辑库名（与 Neo4j / ZooKeeper 口径一致）
        return new MongoManageTarget(0L, "ad-hoc", request.host().trim(),
                request.port() == null ? 0 : request.port(), request.contextPath(),
                request.username(), request.password(), "");
    }

    /**
     * 数据源行 → 连接目标。
     *
     * @param ds 数据源行
     * @return 连接目标
     */
    private static MongoManageTarget toTarget(DataSourceVO ds) {
        return new MongoManageTarget(
                ds.id() == null ? 0L : ds.id(),
                ds.name(),
                ds.host(),
                ds.port() == null ? 0 : ds.port(),
                ds.database(),
                ds.username(),
                ds.password(),
                ds.updateTime() == null ? "" : ds.updateTime());
    }
}
