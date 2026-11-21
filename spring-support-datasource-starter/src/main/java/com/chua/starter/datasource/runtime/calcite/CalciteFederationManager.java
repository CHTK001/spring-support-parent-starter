// [P3C A20 豁免] A20/A30/A31: SQL/日志字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.calcite;

import com.chua.calcite.support.conversion.CalciteDataSourceConversion;
import com.chua.starter.datasource.runtime.model.DsCalciteFederation;
import com.chua.starter.datasource.runtime.model.FederationMember;
import com.chua.starter.datasource.runtime.store.DsRuntimeConfigStore;
import com.chua.starter.datasource.runtime.support.MemberDataSourcePool;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import com.chua.starter.datasource.support.DynamicDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.jdbc.CalciteConnection;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Calcite 联邦运行期管理器。
 *
 * <p>保存/刷新联邦配置后立即重建聚合数据源并替换注册表中的 calciteDataSource，
 * 新查询即刻使用新成员，无需重启宿主。成员连接池由 {@link MemberDataSourcePool}
 * 差量管理，换入换出不泄漏连接。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
public class CalciteFederationManager {

    /**
     * 聚合数据源在注册表中的固定键
     */
    public static final String BEAN_NAME = "calciteDataSource";

    private final DsRuntimeConfigStore store;
    private final MemberDataSourcePool pool;
    private final ApplicationContext applicationContext;
    private final CalciteDataSourceConversion conversion = new CalciteDataSourceConversion();

    /**
     * 当前生效的联邦配置快照（供概览页展示）
     */
    private final AtomicReference<List<DsCalciteFederation>> activeFederations = new AtomicReference<>(List.of());

    /**
     * 当前生效的联邦成员数据源个数（跨全部启用联邦去重后的 schema 数）
     */
    private volatile int activeMemberCount = 0;

    /**
     * 最近一次刷新时间（epoch millis，0 表示尚未刷新）
     */
    private volatile long lastRefreshAt = 0L;

    /**
     * 最近一次刷新结果描述
     */
    private volatile String lastRefreshResult = "尚未刷新";

    public CalciteFederationManager(DsRuntimeConfigStore store, MemberDataSourcePool pool,
                                    ApplicationContext applicationContext) {
        this.store = store;
        this.pool = pool;
        this.applicationContext = applicationContext;
    }

    /**
     * 按配置表当前启用的联邦重建聚合数据源（运行期热刷新入口）。
     *
     * @return 生效的成员 schema 名列表
     */
    public synchronized List<String> refresh() {
        List<DsCalciteFederation> federations = store.findEnabledFederations();
        // 先汇总全部联邦成员，再一次绑定，保证跨联邦的池差量清理正确
        List<FederationMember> allMembers = new ArrayList<>();
        Set<String> seenSchemas = new LinkedHashSet<>();
        for (DsCalciteFederation f : federations) {
            if (f.getMemberList() == null) {
                continue;
            }
            for (FederationMember m : f.getMemberList()) {
                if (m != null && m.getSchemaName() != null && !seenSchemas.add(m.getSchemaName())) {
                    log.warn("[ds-runtime] 联邦 schema 名重复: {}（后者覆盖前者）", m.getSchemaName());
                }
                allMembers.add(m);
            }
        }
        Map<String, DataSource> named = pool.bindFederation(allMembers);
        List<String> schemaNames = new ArrayList<>(named.keySet());

        try {
            if (named.isEmpty()) {
                // 无启用联邦：从注册表摘除聚合数据源，路由回退
                DataSourceContextSupport.DATA_SOURCE_MAP.remove(BEAN_NAME);
                activeFederations.set(List.of());
                activeMemberCount = 0;
                lastRefreshResult = "无启用联邦，聚合数据源已摘除";
            } else {
                DataSource federated = conversion.convertNamed(named);
                DataSourceContextSupport.addDatasource(BEAN_NAME, federated);
                activeFederations.set(List.copyOf(federations));
                activeMemberCount = named.size();
                lastRefreshResult = "生效成员 " + named.size() + " 个: " + named.keySet();
            }
            reResolveRouting();
            lastRefreshAt = System.currentTimeMillis();
            log.info("[ds-runtime] Calcite 联邦已刷新: {}", lastRefreshResult);
        } catch (Exception e) {
            activeFederations.set(List.of());
            activeMemberCount = 0;
            lastRefreshResult = "刷新失败: " + e.getMessage();
            log.error("[ds-runtime] Calcite 联邦刷新失败", e);
            throw new IllegalStateException("Calcite 联邦刷新失败: " + e.getMessage(), e);
        }
        return schemaNames;
    }

    /**
     * 启动完成后按配置表自动恢复已启用的联邦（重启不丢运行时）。
     * 失败只告警不阻断启动，可经刷新接口重试。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        try {
            if (!store.findEnabledFederations().isEmpty()) {
                refresh();
            }
        } catch (Exception e) {
            log.warn("[ds-runtime] 启动加载联邦配置失败（可调用刷新接口重试）: {}", e.getMessage());
        }
    }

    /**
     * 重新解析动态路由数据源的目标表（TARGET_DATA_SOURCES 引用未变，但需让
     * AbstractRoutingDataSource 重新 resolve，确保替换后的聚合数据源被路由感知）。
     */
    private void reResolveRouting() {
        try {
            DynamicDataSource dynamic = applicationContext.getBean(DynamicDataSource.class);
            dynamic.afterPropertiesSet();
        } catch (Exception e) {
            log.warn("[ds-runtime] 动态路由重解析失败（路由表仍为共享 Map，通常无碍）: {}", e.getMessage());
        }
    }

    /**
     * 联邦 SQL 试跑：从当前聚合数据源执行查询。
     *
     * @param sql 查询 SQL（建议显式限定 schema 名）
     * @return columns/rows/rowCount/elapsedMs
     */
    public Map<String, Object> query(String sql) {
        DataSource federated = DataSourceContextSupport.getDatasource(BEAN_NAME);
        if (federated == null) {
            throw new IllegalStateException("当前没有生效的 Calcite 联邦，请先保存并刷新联邦配置");
        }
        long start = System.currentTimeMillis();
        try (Connection conn = federated.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(safeSql(sql))) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<List<Object>> rows = new ArrayList<>();
            int limit = 200;
            while (rs.next() && rows.size() < limit) {
                List<Object> row = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    Object v = rs.getObject(i);
                    row.add(v instanceof java.sql.Timestamp t ? t.toLocalDateTime().toString()
                            : v instanceof java.sql.Date d ? d.toLocalDate().toString()
                            : v instanceof java.sql.Time t2 ? t2.toLocalTime().toString()
                            : v instanceof byte[] b ? "<binary " + b.length + "B>" : v);
                }
                rows.add(row);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("rowCount", rows.size());
            result.put("truncated", rows.size() >= limit);
            result.put("elapsedMs", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("联邦 SQL 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 当前生效的联邦配置快照。
     */
    public List<DsCalciteFederation> activeFederations() {
        return activeFederations.get();
    }

    /**
     * 当前生效的联邦成员数据源个数（非联邦配置条数）。
     */
    public int activeMemberCount() {
        return activeMemberCount;
    }

    /**
     * 最近刷新时间。
     */
    public long lastRefreshAt() {
        return lastRefreshAt;
    }

    /**
     * 最近刷新结果描述。
     */
    public String lastRefreshResult() {
        return lastRefreshResult;
    }

    /**
     * 保存后触发刷新并回填最新状态。
     */
    public DsCalciteFederation saveAndRefresh(DsCalciteFederation federation) {
        if (federation.getSchemaName() == null || federation.getSchemaName().isBlank()) {
            throw new IllegalArgumentException("联邦 schema 名不能为空");
        }
        if (federation.getMemberList() == null || federation.getMemberList().isEmpty()) {
            throw new IllegalArgumentException("联邦至少需要一个成员数据源");
        }
        federation.setMembers(store.writeFederationMembers(federation.getMemberList()));
        long id = store.saveFederation(federation);
        refresh();
        return store.findFederationById(id);
    }

    /**
     * 删除联邦并刷新（删除后该联邦成员池随之关闭）。
     */
    public void deleteAndRefresh(Long id) {
        store.deleteFederation(id);
        refresh();
    }

    private static String safeSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("select") || lower.startsWith("with") || lower.startsWith("show")
                || lower.startsWith("explain")) {
            return trimmed;
        }
        throw new IllegalArgumentException("试跑仅允许 SELECT/WITH/SHOW/EXPLAIN 语句");
    }
}
