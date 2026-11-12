package com.chua.starter.datasourcehub.support.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceMapper;
import com.chua.starter.tenant.server.support.TenantDatasourceInfo;
import com.chua.starter.tenant.server.support.TenantDatasourceLookup;
import com.chua.starter.tenant.server.support.TenantDatasourceOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 租户模块「数据源台账查询口」的本态实现。
 *
 * <p>租户侧只定义 {@link TenantDatasourceLookup} 这一个窄接口（见其说明：不让两个 starter
 * 互相依赖），装了数据源中心的实例在这里补上实现，租户填了
 * {@code sys_tenant_datasource_id} 才解析得到实例信息。</p>
 *
 * <p><b>只接受可用的 JDBC 实例</b>：停用（{@code datasource_status=0}）、非 jdbc 类型
 * （redis/nginx 等）、或库名本身是一段完整 JDBC URL（合并/桥接类记录）的台账行一律返回
 * {@code null}。租户库必须是可直接建连接池的普通 JDBC 数据源，拿错类型去建池只会在
 * 运行期报错，而在配置期就挡住才能给出可读的原因。</p>
 *
 * <p><b>库名不下发</b>：返回的 {@link TenantDatasourceInfo} 的 {@code dbName} 固定为
 * {@code null}，由租户记录自己决定——同一实例往往承载多个租户，用台账里的库名路由会串库。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Slf4j
@Component
@ConditionalOnClass(name = "com.chua.starter.tenant.server.support.TenantDatasourceLookup")
@RequiredArgsConstructor
public class DatasourceTenantLookupAdapter implements TenantDatasourceLookup {

    /**
     * 启用状态
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 可承载租户库的数据源类型
     */
    private static final String TYPE_JDBC = "jdbc";

    /**
     * 数据源台账查询
     */
    private final MsDatasourceMapper datasourceMapper;

    @Override
    public TenantDatasourceInfo find(long datasourceId) {
        MsDatasource row = datasourceMapper.selectById(datasourceId);
        if (row == null) {
            log.warn("[租户数据源] 台账数据源不存在: id={}", datasourceId);
            return null;
        }
        if (row.getDatasourceStatus() != null && row.getDatasourceStatus() != STATUS_ENABLED) {
            log.warn("[租户数据源] 台账数据源已停用，不能作为租户库: id={}, name={}",
                    datasourceId, row.getDatasourceName());
            return null;
        }
        if (row.getDatasourceType() != null && !TYPE_JDBC.equalsIgnoreCase(row.getDatasourceType())) {
            log.warn("[租户数据源] 台账数据源类型不是 jdbc，不能作为租户库: id={}, type={}",
                    datasourceId, row.getDatasourceType());
            return null;
        }
        String database = row.getDatasourceDatabase();
        if (database != null && database.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:")) {
            log.warn("[租户数据源] 台账数据源存的是完整 JDBC URL（合并/桥接类记录），不能作为租户库实例: id={}",
                    datasourceId);
            return null;
        }
        return new TenantDatasourceInfo(
                row.getDatasourceHost(),
                row.getDatasourcePort(),
                null,
                row.getDatasourceUsername(),
                DatasourceSecrets.decode(row.getDatasourcePassword()),
                TenantDatasourceInfo.Origin.DATASOURCE_HUB);
    }

    /**
     * 列出可作为租户库的台账数据源。
     *
     * <p>只返回「启用中的 jdbc 实例」，且库名字段不像完整 JDBC URL —— 与 {@link #find} 同一套
     * 过滤条件，避免下拉里出现选中后必然失败的项。<b>不含口令</b>。</p>
     *
     * @return 可选项
     */
    @Override
    public List<TenantDatasourceOption> options() {
        List<MsDatasource> rows = datasourceMapper.selectList(
                new LambdaQueryWrapper<MsDatasource>()
                        .and(w -> w.isNull(MsDatasource::getDatasourceStatus)
                                .or().eq(MsDatasource::getDatasourceStatus, STATUS_ENABLED))
                        .and(w -> w.isNull(MsDatasource::getDatasourceType)
                                .or().eq(MsDatasource::getDatasourceType, TYPE_JDBC))
                        .orderByDesc(MsDatasource::getDatasourceTopFlag)
                        .orderByAsc(MsDatasource::getId));
        List<TenantDatasourceOption> options = new ArrayList<>(rows.size());
        for (MsDatasource row : rows) {
            if (row.getId() == null) {
                continue;
            }
            String database = row.getDatasourceDatabase();
            if (database != null && database.trim().toLowerCase(Locale.ROOT).startsWith("jdbc:")) {
                continue;
            }
            Boolean online = row.getDatasourceLastTestOk() == null ? null : row.getDatasourceLastTestOk() == 1;
            options.add(new TenantDatasourceOption(row.getId(),
                    row.getDatasourceName() == null ? ("数据源#" + row.getId()) : row.getDatasourceName(),
                    TYPE_JDBC, row.getDatasourceHost(), row.getDatasourcePort(), database, online));
        }
        return options;
    }
}
