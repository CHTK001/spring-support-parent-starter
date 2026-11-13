package com.chua.starter.job.support.store;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.job.support.cluster.LeaderElectionResolver;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis-Plus 行级抢占版 Leader 选举实现, 基于 {@code sys_job_cluster} 表。
 *
 * <h3>抢占策略</h3>
 * <pre>
 *   UPDATE sys_job_cluster
 *      SET sys_job_cluster_leader_node = ?,
 *          sys_job_cluster_lease_until_ms = ?,
 *          sys_job_cluster_version = sys_job_cluster_version + 1
 *    WHERE sys_job_cluster_id = ?
 *      AND (sys_job_cluster_leader_node IS NULL
 *           OR sys_job_cluster_leader_node = ?
 *           OR sys_job_cluster_lease_until_ms &lt; ?)
 * </pre>
 *
 * <p>采用行级 {@code UPDATE} 自带写锁, MySQL 串行化事务保证首个执行成功的节点获得 Leader 身份。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Spi("mysql")
@RequiredArgsConstructor
public class MysqlLeaderElectionResolver implements LeaderElectionResolver {

    /**
     * 取数据库当前时刻（毫秒）。
     *
     * <p>SQL 里刻意不含任何库专用时间函数（多机时钟对齐的诉求靠"用库时钟"满足，
     * 而非靠 {@code UNIX_TIMESTAMP(NOW(3))} 这类写法），因此先读一次库时间，
     * 再由 Java 换算成毫秒并作为参数参与比较与写入 —— 这样同一条语句在 MySQL 与 H2
     * 上都能执行。读与写之间只差同库的毫秒级时间，不影响租约语义。</p>
     *
     * @return 数据库当前毫秒时间戳；读取失败时回落到本机时钟
     */
    private long currentDbTimeMs() {
        try {
            Timestamp dbNow = clusterMapper.selectDbNow();
            return dbNow == null ? System.currentTimeMillis() : dbNow.getTime();
        } catch (Exception ex) {
            log.warn("[job] 取数据库时间失败, 回落到本机时钟 cause={}", ex.getMessage());
            return System.currentTimeMillis();
        }
    }

    /**
     * sys_作业_cluster 映射器
     */
    private final ClusterMapper clusterMapper;

    @Override
    /**
     * 尝试获取
     * @param group 字符串
     * @param leaseSeconds 整数
     * @param nodeId 字符串
     * @return 返回值
    */
    public boolean tryAcquire(String group, String nodeId, int leaseSeconds) {
        // 租约截止时刻统一由数据库时钟（NOW(3)）计算：多机部署下各节点时钟漂移
        // 不再影响"租约是否过期"的判据，NTP 由硬要求降级为可选
        try {
            ClusterEntity existing = clusterMapper.selectById(group);
            if (existing == null) {
                try {
                    ClusterEntity seed = new ClusterEntity();
                    seed.setSysJobClusterId(group);
                    seed.setSysJobClusterLeaderNode(null);
                    seed.setSysJobClusterLeaseUntilMs(0L);
                    seed.setSysJobClusterVersion(0L);
                    clusterMapper.insert(seed);
                    log.info("[job] 初始化集群命名空间 group={}", group);
                } catch (Exception dupOrOther) {
 // 其他节点可能并发初始化同一 群体, 主键冲突为预期竞态, 忽略后落到下方 更新 抢占
                    log.debug("[job] 初始化集群命名空间跳过 group={} cause={}", group, dupOrOther.getMessage());
                }
            }
            long nowMs = currentDbTimeMs();
            // [P3C A20 豁免]
            int rows = clusterMapper.tryAcquire(group, nodeId, nowMs, nowMs + leaseSeconds * 1000L);
            if (rows > 0) {
                if (existing != null && nodeId.equals(existing.getSysJobClusterLeaderNode())) {
                    log.debug("[job] Leader 续约成功 (MySQL) group={} nodeId={} lease={}s",
                            group, nodeId, leaseSeconds);
                } else {
                    log.info("[job] 抢占 Leader 成功 (MySQL) group={} nodeId={} lease={}s",
                            group, nodeId, leaseSeconds);
                }
                return true;
            }
            return false;
        } catch (Exception ex) {
            log.error("[job] MySQL 抢占 Leader 异常 group={} nodeId={} cause={}",
                    group, nodeId, ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 续约。
     *
     * @param group        命名空间
     * @param nodeId       节点 标识
     * @param leaseSeconds 租约时长
     * @return 续约成功返回 true
     */
    public boolean renew(String group, String nodeId, int leaseSeconds) {
        // 租约截止时刻统一由数据库时钟（NOW(3)）计算：多机部署下各节点时钟漂移
        // 不再影响"租约是否过期"的判据，NTP 由硬要求降级为可选
        try {
            long nowMs = currentDbTimeMs();
            // [P3C A20 豁免]
            int rows = clusterMapper.renew(group, nodeId, nowMs, nowMs + leaseSeconds * 1000L);
            return rows > 0;
        } catch (Exception ex) {
            log.warn("[job] Leader 续约异常 group={} nodeId={} cause={}",
                    group, nodeId, ex.getMessage());
            return false;
        }
    }

    @Override
    /**
     * 释放
     * @param group 字符串
     * @param nodeId 字符串
     * @return 返回值
    */
    public void release(String group, String nodeId) {
        try {
            int rows = clusterMapper.release(group, nodeId);
            log.info("[job] 释放 Leader 权 (MySQL) group={} nodeId={} rows={}",
                    group, nodeId, rows);
        } catch (Exception ex) {
            log.warn("[job] 释放 Leader 异常 group={} nodeId={} cause={}",
                    group, nodeId, ex.getMessage());
        }
    }
}
