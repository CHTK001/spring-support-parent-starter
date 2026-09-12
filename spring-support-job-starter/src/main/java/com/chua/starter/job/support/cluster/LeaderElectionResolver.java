package com.chua.starter.job.support.cluster;

/**
   * 铅 选举解析器 SPI, 用于生产环境接入真实选主 (MySQL 行锁 / Redis SETNX / etcd 等)。
 *
 * <p>默认实现见 {@link LeaderElector} 内部 CAS, 单进程自认 Leader,
 * 适合单机或调试; 多进程场景请实现本接口覆盖。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface LeaderElectionResolver {

    /**
     * 尝试抢占 Leader。
     *
     * @param group        命名空间
     * @param nodeId       当前节点标识
     * @param leaseSeconds 租约时长
     * @return 抢占成功返回 true
     */
    boolean tryAcquire(String group, String nodeId, int leaseSeconds);

    /**
     * 释放 Leader 权。
     *
     * @param group  命名空间
     * @param nodeId 当前节点标识
     */
    void release(String group, String nodeId);
}
