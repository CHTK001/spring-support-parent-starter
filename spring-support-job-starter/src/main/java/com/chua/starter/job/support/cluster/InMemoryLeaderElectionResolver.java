package com.chua.starter.job.support.cluster;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
   * 默认 (CAS 占位) 铅 选举解析器。
 *
 * <p>仅在单进程内有效, 适用于单机模式或测试场景;
   * 多 调度器 节点请实现 {@link LeaderElectionResolver} SPI 接入真实选主
 * (例如 {@code MysqlLeaderElectionResolver})。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class InMemoryLeaderElectionResolver implements LeaderElectionResolver {

    /**
      * 本地内存中的 铅 记录, 钥匙 = 群体
     */
    private final ConcurrentMap<String, String> leaderMap = new ConcurrentHashMap<>();

    /**
      * 本地内存中的租约到期时间戳, 钥匙 = 群体
     */
    private final ConcurrentMap<String, Long> leaseMap = new ConcurrentHashMap<>();

    @Override
    /** 尝试获取 */
    public boolean tryAcquire(String group, String nodeId, int leaseSeconds) {
        long now = System.currentTimeMillis();
        Long lease = leaseMap.get(group);
        String currentLeader = leaderMap.get(group);
        boolean expired = lease == null || lease < now;
        boolean mine = nodeId.equals(currentLeader);
        if (currentLeader == null || expired || mine) {
            leaderMap.put(group, nodeId);
            leaseMap.put(group, now + leaseSeconds * 1000L);
            return true;
        }
        return false;
    }

    @Override
    /** 释放 */
    public void release(String group, String nodeId) {
        leaderMap.remove(group);
        leaseMap.remove(group);
    }
}