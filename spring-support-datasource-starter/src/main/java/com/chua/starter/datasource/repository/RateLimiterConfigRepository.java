package com.chua.starter.datasource.repository;

import com.chua.starter.datasource.entity.ConcurrentRateLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 限流器配置的 sqlite 持久化仓库。
 * <p>
 * 负责将限流器配置（名称、QPS、预热时间、超时时间、启用状态）读写到
 * sqlite 数据库的 并发_rate_限制 表，供 {@link com.chua.springboot.support.endpoint.ConcurrentEndpoint}
 * 端点通过 actuator API 进行增删查改。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Repository
public class RateLimiterConfigRepository {

    /**
     * Spring JDBC 模板，用于执行 SQL 语句
    */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造方法，注入 jdbctemplate。
     *
     * @param jdbcTemplate Spring 容器提供的 jdbctemplate Bean
     */
    public RateLimiterConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询所有限流器配置记录。
     *
     * @return 限流器配置列表，按名称排序；无记录时返回空列表
     */
    public List<ConcurrentRateLimiterConfig> findAll() {
        return jdbcTemplate.query(
                "SELECT name, permits_per_second, warmup_period_ms, timeout_ms, enabled FROM concurrent_rate_limiter ORDER BY name",
                (rs, rowNum) -> mapRow(rs)
        );
    }

    /**
     * 按名称查询单个限流器配置。
     *
     * @param name 限流器名称
     * @return 配置对象，不存在时返回 期权.空()
     */
    public Optional<ConcurrentRateLimiterConfig> findByName(String name) {
        List<ConcurrentRateLimiterConfig> list = jdbcTemplate.query(
                "SELECT name, permits_per_second, warmup_period_ms, timeout_ms, enabled FROM concurrent_rate_limiter WHERE name = ?",
                (rs, rowNum) -> mapRow(rs),
                name
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * 保存或更新限流器配置（UPSERT）。
     * <p>
     * sqlite 使用 插入 或 替换 语义：名称相同时覆盖旧记录，不存在时插入新记录。
     * </p>
     *
     * @param config 限流器配置对象，名称 不能为 空 或空
     * @return 影响的行数（1 = 插入，2 = 先删后插）
     */
    public int save(ConcurrentRateLimiterConfig config) {
        int rows = jdbcTemplate.update(
                "INSERT OR REPLACE INTO concurrent_rate_limiter "
                        + "(name, permits_per_second, warmup_period_ms, timeout_ms, enabled, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, datetime('now'))",
                ps -> {
                    ps.setString(1, config.getName());
                    ps.setDouble(2, config.getPermitsPerSecond());
                    ps.setLong(3, config.getWarmupPeriodMs());
                    ps.setLong(4, config.getTimeoutMs());
                    ps.setInt(5, config.isEnabled() ? 1 : 0);
                }
        );
        return rows;
    }

    /**
     * 按名称删除限流器配置。
     *
     * @param name 限流器名称
     * @return 影响的行数（1 = 删除成功，0 = 记录不存在）
     */
    public int delete(String name) {
        return jdbcTemplate.update(
                "DELETE FROM concurrent_rate_limiter WHERE name = ?",
                name
        );
    }

    /**
     * 将 结果设置 当前行映射为限流器配置对象。
     *
     * @param rs JDBC 结果集，已定位到当前行
     * @return 映射后的配置对象
     * @throws java.sql.SQLException 读取列失败时抛出
     */
    private ConcurrentRateLimiterConfig mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        ConcurrentRateLimiterConfig c = new ConcurrentRateLimiterConfig();
        c.setName(rs.getString("name"));
        c.setPermitsPerSecond(rs.getDouble("permits_per_second"));
        c.setWarmupPeriodMs(rs.getLong("warmup_period_ms"));
        c.setTimeoutMs(rs.getLong("timeout_ms"));
        c.setEnabled(rs.getInt("enabled") != 0);
        return c;
    }
}
