package com.chua.starter.datasource.repository;

import com.chua.starter.datasource.entity.ConcurrentDistributedLockConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 分布式锁配置的 sqlite 持久化仓库。
 * <p>
 * 负责将锁配置（名称、锁类型、公平性、等待时间、租约时间、启用状态）读写到
 * sqlite 数据库的 并发_distributed_锁 表，供 actuator 端点增删查改。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Repository
public class DistributedLockConfigRepository {

    /** Spring JDBC 模板，用于执行 SQL 语句 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造方法，注入 jdbctemplate。
     *
     * @param jdbcTemplate Spring 容器提供的 jdbctemplate Bean
     */
    public DistributedLockConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询所有分布式锁配置记录。
     *
     * @return 锁配置列表，按名称排序；无记录时返回空列表
     */
    public List<ConcurrentDistributedLockConfig> findAll() {
        return jdbcTemplate.query(
                "SELECT name, lock_type, fair, wait_time_ms, lease_time_ms, enabled FROM concurrent_distributed_lock ORDER BY name",
                (rs, rowNum) -> mapRow(rs)
        );
    }

    /**
     * 按名称查询单个分布式锁配置。
     *
     * @param name 锁名称
     * @return 配置对象，不存在时返回 期权.空()
     */
    public Optional<ConcurrentDistributedLockConfig> findByName(String name) {
        List<ConcurrentDistributedLockConfig> list = jdbcTemplate.query(
                "SELECT name, lock_type, fair, wait_time_ms, lease_time_ms, enabled FROM concurrent_distributed_lock WHERE name = ?",
                (rs, rowNum) -> mapRow(rs),
                name
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * 保存或更新分布式锁配置（UPSERT）。
     * <p>
     * sqlite 使用 插入 或 替换 语义：名称相同时覆盖旧记录，不存在时插入新记录。
     * </p>
     *
     * @param config 锁配置对象，名称 不能为 空 或空
     * @return 影响的行数
     */
    public int save(ConcurrentDistributedLockConfig config) {
        return jdbcTemplate.update(
                "INSERT OR REPLACE INTO concurrent_distributed_lock "
                        + "(name, lock_type, fair, wait_time_ms, lease_time_ms, enabled, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, datetime('now'))",
                ps -> {
                    ps.setString(1, config.getName());
                    ps.setString(2, config.getLockType());
                    ps.setInt(3, config.isFair() ? 1 : 0);
                    ps.setLong(4, config.getWaitTimeMs());
                    ps.setLong(5, config.getLeaseTimeMs());
                    ps.setInt(6, config.isEnabled() ? 1 : 0);
                }
        );
    }

    /**
     * 按名称删除分布式锁配置。
     *
     * @param name 锁名称
     * @return 影响的行数（1 = 删除成功，0 = 记录不存在）
     */
    public int delete(String name) {
        return jdbcTemplate.update(
                "DELETE FROM concurrent_distributed_lock WHERE name = ?",
                name
        );
    }

    /**
     * 将 结果设置 当前行映射为分布式锁配置对象。
     *
     * @param rs JDBC 结果集，已定位到当前行
     * @return 映射后的配置对象
     * @throws java.sql.SQLException 读取列失败时抛出
     */
    private ConcurrentDistributedLockConfig mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        ConcurrentDistributedLockConfig c = new ConcurrentDistributedLockConfig();
        c.setName(rs.getString("name"));
        c.setLockType(rs.getString("lock_type"));
        c.setFair(rs.getInt("fair") != 0);
        c.setWaitTimeMs(rs.getLong("wait_time_ms"));
        c.setLeaseTimeMs(rs.getLong("lease_time_ms"));
        c.setEnabled(rs.getInt("enabled") != 0);
        return c;
    }
}
