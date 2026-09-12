package com.chua.starter.datasource.monitor;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
* SQL 监听 Actuator 端点
*
* <p>暴露数据源 SQL 执行统计信息，端点路径 {@code /actuator/sqlmonitor}。
* 支持 获取 查询统计、POST 重置统计。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Endpoint(id = "sqlmonitor")
public class SqlMonitorEndpoint {

    /**
    * 监控门面
     */
    private final DataSourceSqlMonitor monitor;

    /**
    * 端点构造器
    *
    * @param monitor 监控门面
     */
    public SqlMonitorEndpoint(DataSourceSqlMonitor monitor) {
        this.monitor = monitor;
    }

    /**
    * 查询 SQL 执行统计
    *
    * @return 统计信息（按数据源分组）
     */
    @ReadOperation
    public Map<String, Object> statistics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", monitor.isEnabled());
        result.put("dataSources", snapshot());
        return result;
    }

    /**
    * 重置统计
    *
    * @return 提示信息
     */
    @WriteOperation
    public Map<String, Object> reset() {
        monitor.resetStatistics();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "SQL 统计已重置");
        result.put("enabled", monitor.isEnabled());
        return result;
    }

    /**
    * 生成各数据源统计快照
    *
    * @return 数据源名 → 统计明细
     */
    private Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, SqlStatistics> entry : monitor.getAllStatistics().entrySet()) {
            SqlStatistics statistics = entry.getValue();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("total", statistics.getTotalCount().get());
            detail.put("success", statistics.getSuccessCount().get());
            detail.put("fail", statistics.getFailCount().get());
            detail.put("slowSql", statistics.getSlowSqlCount().get());
            detail.put("avgCostMs", statistics.getAvgCostMs());
            snapshot.put(entry.getKey(), detail);
        }
        return snapshot;
    }
}
