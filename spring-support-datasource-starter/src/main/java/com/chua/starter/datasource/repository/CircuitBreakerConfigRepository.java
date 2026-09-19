package com.chua.starter.datasource.repository;

import com.chua.starter.datasource.entity.ConcurrentCircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 熔断器配置的数据库持久化仓库。
 * <p>
 * 负责将熔断器配置（名称、失败阈值、成功阈值、等待时间、启用状态）读写到
 * concurrent_circuit_breaker 表，供 actuator 端点增删查改。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Repository
public class CircuitBreakerConfigRepository {

    /**
     * Spring JDBC 模板，用于执行 SQL 语句
    */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造方法，注入 jdbctemplate。
     *
     * @param jdbcTemplate Spring 容器提供的 jdbctemplate Bean
     */
    public CircuitBreakerConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询所有熔断器配置记录。
     *
     * @return 熔断器配置列表，按名称排序；无记录时返回空列表
     */
    public List<ConcurrentCircuitBreakerConfig> findAll() {
        return jdbcTemplate.query(
                "SELECT name, failure_threshold, success_threshold, wait_duration_ms, enabled FROM concurrent_circuit_breaker ORDER BY name",
                (rs, rowNum) -> mapRow(rs)
        );
    }

    /**
     * 按名称查询单个熔断器配置。
     *
     * @param name 熔断器名称
     * @return 配置对象，不存在时返回 期权.空()
     */
    public Optional<ConcurrentCircuitBreakerConfig> findByName(String name) {
        List<ConcurrentCircuitBreakerConfig> list = jdbcTemplate.query(
                "SELECT name, failure_threshold, success_threshold, wait_duration_ms, enabled FROM concurrent_circuit_breaker WHERE name = ?",
                (rs, rowNum) -> mapRow(rs),
                name
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * 保存或更新熔断器配置（UPSERT）。
     * <p>
     * 先按主键 UPDATE，未命中再 INSERT；不使用方言专属的插入或替换语法，
     * 以便在 MySQL、H2 上通用。
     * </p>
     *
     * @param config 熔断器配置对象，名称 不能为 空 或空
     * @return 影响的行数
     */
    public int save(ConcurrentCircuitBreakerConfig config) {
        int updated = jdbcTemplate.update(
                "UPDATE concurrent_circuit_breaker "
                        + "SET failure_threshold = ?, success_threshold = ?, wait_duration_ms = ?, enabled = ?, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE name = ?",
                ps -> {
                    ps.setInt(1, config.getFailureThreshold());
                    ps.setInt(2, config.getSuccessThreshold());
                    ps.setLong(3, config.getWaitDurationMs());
                    ps.setInt(4, config.isEnabled() ? 1 : 0);
                    ps.setString(5, config.getName());
                }
        );
        if (updated > 0) {
            return updated;
        }
        return jdbcTemplate.update(
                "INSERT INTO concurrent_circuit_breaker "
                        + "(name, failure_threshold, success_threshold, wait_duration_ms, enabled, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                ps -> {
                    ps.setString(1, config.getName());
                    ps.setInt(2, config.getFailureThreshold());
                    ps.setInt(3, config.getSuccessThreshold());
                    ps.setLong(4, config.getWaitDurationMs());
                    ps.setInt(5, config.isEnabled() ? 1 : 0);
                }
        );
    }

    /**
     * 按名称删除熔断器配置。
     *
     * @param name 熔断器名称
     * @return 影响的行数（1 = 删除成功，0 = 记录不存在）
     */
    public int delete(String name) {
        return jdbcTemplate.update(
                "DELETE FROM concurrent_circuit_breaker WHERE name = ?",
                name
        );
    }

    /**
     * 将 结果设置 当前行映射为熔断器配置对象。
     *
     * @param rs JDBC 结果集，已定位到当前行
     * @return 映射后的配置对象
     * @throws java.sql.SQLException 读取列失败时抛出
     */
    private ConcurrentCircuitBreakerConfig mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        ConcurrentCircuitBreakerConfig c = new ConcurrentCircuitBreakerConfig();
        c.setName(rs.getString("name"));
        c.setFailureThreshold(rs.getInt("failure_threshold"));
        c.setSuccessThreshold(rs.getInt("success_threshold"));
        c.setWaitDurationMs(rs.getLong("wait_duration_ms"));
        c.setEnabled(rs.getInt("enabled") != 0);
        return c;
    }
}
