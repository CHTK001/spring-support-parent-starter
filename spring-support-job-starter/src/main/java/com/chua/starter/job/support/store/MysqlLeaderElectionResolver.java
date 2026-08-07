package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.job.support.cluster.LeaderElectionResolver;
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
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RequiredArgsConstructor
public class MysqlLeaderElectionResolver implements LeaderElectionResolver {

    /**
     * sys_job_cluster Mapper
     */
    private final ClusterMapper clusterMapper;

    @Override
    public boolean tryAcquire(String group, String nodeId, int leaseSeconds) {
        long now = System.currentTimeMillis();
        long leaseUntil = now + leaseSeconds * 1000L;
        try {
            int rows = clusterMapper.update(
                    null,
                    Wrappers.<ClusterEntity>lambdaUpdate()
                            .set(ClusterEntity::getSysJobClusterLeaderNode, nodeId)
                            .set(ClusterEntity::getSysJobClusterLeaseUntilMs, leaseUntil)
                            .setSql("sys_job_cluster_version = sys_job_cluster_version + 1")
                            .eq(ClusterEntity::getSysJobClusterId, group)
                            .and(w -> w.isNull(ClusterEntity::getSysJobClusterLeaderNode)
                                    .or().eq(ClusterEntity::getSysJobClusterLeaderNode, nodeId)
                                    .or().lt(ClusterEntity::getSysJobClusterLeaseUntilMs, now))
            );
            if (rows > 0) {
                log.info("[job] 抢占 Leader 成功 (MySQL) group={} nodeId={} lease={}s",
                        group, nodeId, leaseSeconds);
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
     * @param nodeId       节点 ID
     * @param leaseSeconds 租约时长
     * @return 续约成功返回 true
     */
    public boolean renew(String group, String nodeId, int leaseSeconds) {
        long now = System.currentTimeMillis();
        long leaseUntil = now + leaseSeconds * 1000L;
        try {
            int rows = clusterMapper.update(
                    null,
                    Wrappers.<ClusterEntity>lambdaUpdate()
                            .set(ClusterEntity::getSysJobClusterLeaseUntilMs, leaseUntil)
                            .setSql("sys_job_cluster_version = sys_job_cluster_version + 1")
                            .eq(ClusterEntity::getSysJobClusterId, group)
                            .eq(ClusterEntity::getSysJobClusterLeaderNode, nodeId)
            );
            return rows > 0;
        } catch (Exception ex) {
            log.warn("[job] Leader 续约异常 group={} nodeId={} cause={}",
                    group, nodeId, ex.getMessage());
            return false;
        }
    }

    @Override
    public void release(String group, String nodeId) {
        try {
            int rows = clusterMapper.update(
                    null,
                    Wrappers.<ClusterEntity>lambdaUpdate()
                            .set(ClusterEntity::getSysJobClusterLeaderNode, null)
                            .set(ClusterEntity::getSysJobClusterLeaseUntilMs, 0L)
                            .setSql("sys_job_cluster_version = sys_job_cluster_version + 1")
                            .eq(ClusterEntity::getSysJobClusterId, group)
                            .eq(ClusterEntity::getSysJobClusterLeaderNode, nodeId)
            );
            log.info("[job] 释放 Leader 权 (MySQL) group={} nodeId={} rows={}",
                    group, nodeId, rows);
        } catch (Exception ex) {
            log.warn("[job] 释放 Leader 异常 group={} nodeId={} cause={}",
                    group, nodeId, ex.getMessage());
        }
    }
}