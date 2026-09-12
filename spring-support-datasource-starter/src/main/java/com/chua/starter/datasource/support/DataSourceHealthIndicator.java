package com.chua.starter.datasource.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
* 数据源健康检查指示器
* <p>
* 集成 Spring Boot Actuator，在 {@code /actuator/health} 端点中
* 展示所有已注册数据源的连接健康状态。
* </p>
*
* <h3>输出示例</h3>
* <pre>{@code
* {
*   "status": "UP",
*   "components": {
*     "dataSource": {
*       "status": "UP",
*       "details": {
*         "master":    { "status": "UP",   "responseTime": 5  },
*         "slave_1":   { "status": "UP",   "responseTime": 8  },
*         "slave_2":   { "status": "DOWN", "error": "连接超时" }
*       }
*     }
*   }
* }
* }</pre>时" }
*       }
*     }
*   }
* }
* }</pre>
*
* @since 2026/07/14
* @author CH
 */
@Slf4j
public class DataSourceHealthIndicator extends AbstractHealthIndicator {

    /** 连接验证超时（秒） */
    /** 校验_超时_seconds */
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;

    /** 运行时异常白名单（不标记为 DOWN，避免循环） */
    /** Transient_错误_前缀 */
    private static final String[] TRANSIENT_ERROR_PREFIXES = {
            "CommunicationsException", "SocketException", "InterruptedException"
    };

    /**
    * 数据源健康indicator。
     */
    public DataSourceHealthIndicator() {
        /**
        * 父。
        * @param "数据源健康检查失败" "数据源健康检查失败"
         */
        super("数据源健康检查失败");
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        Map<String, DataSource> allDataSources = DataSourceContextSupport.getAllDataSources();

        if (allDataSources.isEmpty()) {
            builder.outOfService().withDetail("message", "没有已注册的数据源");
            return;
        }

        Map<String, Object> dsDetails = new LinkedHashMap<>();
        boolean allUp = true;
        int upCount = 0;
        int downCount = 0;

        for (Map.Entry<String, DataSource> entry : allDataSources.entrySet()) {
            String dsName = entry.getKey();
            DataSource dataSource = entry.getValue();

            if (dataSource == null) {
                dsDetails.put(dsName, Map.of("status", "UNKNOWN", "message", "数据源实例为 null"));
                allUp = false;
                downCount++;
                continue;
            }

            // 对每个数据源执行快速连接验证
            Map<String, Object> dsHealth = checkSingleDataSource(dsName, dataSource);
            dsDetails.put(dsName, dsHealth);

            if ("UP".equals(dsHealth.get("status"))) {
                upCount++;
            } else {
                allUp = false;
                downCount++;
            }
        }

        // 构建整体健康状态
        builder.withDetail("total", allDataSources.size());
        builder.withDetail("up", upCount);
        builder.withDetail("down", downCount);
        builder.withDetail("dataSources", dsDetails);

        if (allUp) {
            builder.up();
        } else if (upCount == 0) {
            builder.down();
        } else {
            // 部分数据源不可用 — 使用 DEGRADED 状态
            builder.status(new Status("DEGRADED", "部分数据源不可用"));
        }
    }

    /**
    * 检查单个数据源的健康状态
    * @param name 名称
    * @param dataSource 数据源
    * @return 检查单个数据源的结果
     */
    private Map<String, Object> checkSingleDataSource(String name, DataSource dataSource) {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();

        /**
        * 尝试。
        * @param dataSource.getConnection( 数据源.getconnection(
         */
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(VALIDATION_TIMEOUT_SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (valid) {
                result.put("status", "UP");
                result.put("responseTime", elapsed);
            } else {
                result.put("status", "DOWN");
                result.put("responseTime", elapsed);
                result.put("error", "连接验证返回 false");
            }
        } catch (SQLException e) {
            long elapsed = System.currentTimeMillis() - start;
            String errorMsg = e.getMessage();

            // 暂时性错误：仍然标记为 UP 但附带警告
            if (isTransientError(errorMsg)) {
                result.put("status", "UP");
                result.put("warning", errorMsg);
                result.put("responseTime", elapsed);
                log.warn("[数据源健康] {} 暂时性连接异常: {}", name, errorMsg);
            } else {
                result.put("status", "DOWN");
                result.put("error", errorMsg != null ? errorMsg : e.getClass().getSimpleName());
                result.put("responseTime", elapsed);
                log.warn("[数据源健康] {} 连接检查失败: {}", name, errorMsg);
            }
        }

        return result;
    }

    /**
    * 判断是否为暂时性错误（网络抖动、超时等）
    * @param errorMsg 错误msg
    * @return 是否transient错误的结果
     */
    private boolean isTransientError(String errorMsg) {
        if (errorMsg == null) {
            return false;
        }
        for (String prefix : TRANSIENT_ERROR_PREFIXES) {
            if (errorMsg.contains(prefix)) {
                return true;
            }
        }
        return false;
    }
}
