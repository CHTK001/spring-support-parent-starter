package com.chua.starter.datasource.monitor;

import com.chua.common.support.concurrent.circuitbreaker.CircuitBreakerFlow;
import com.chua.common.support.concurrent.lock.LockFlow;
import com.chua.common.support.concurrent.rate.RateLimiterFlow;
import com.chua.sqlite.support.engine.SqliteEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.util.HashMap;
import java.util.Map;

/**
* sqlite 引擎 Actuator 端点
* <p>
* 暴露 sqlite 引擎运行状态，包括数据库文件路径、连接数、表结构等信息。
* 端点路径 {@code /actuator/sqlite}。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@WebEndpoint(id = "sqlite")
@ConditionalOnClass(SqliteEngine.class)
@ConditionalOnMissingBean
public class SqliteEngineEndpoint {

    private final SqliteEngine engine; // engine

    /**
    * sqliteengine端点。
    * @param engine engine
    */
    public SqliteEngineEndpoint(SqliteEngine engine) {
        this.engine = engine;
    }

    /**
    * 查询 sqlite 引擎基本信息。
    * @return 信息的结果
    */
    @ReadOperation
    public Map<String, Object> info() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", true);
        result.put("name", engine.getDefaultDataSourceName());
        try {
            result.put("url", engine.getDataSource("default").url());
        } catch (Exception e) {
            result.put("url", "unknown");
        }
        return result;
    }

    /**
    * 查询 sqlite 引擎统计信息（表数量等）。
    * @return stats的结果
    */
    @ReadOperation
    public Map<String, Object> stats() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.putAll(info());
            result.put("engineType", "sqlite");
        } catch (Exception e) {
            log.warn("[SqliteEngineEndpoint] 获取统计信息失败", e);
        }
        return result;
    }
}
