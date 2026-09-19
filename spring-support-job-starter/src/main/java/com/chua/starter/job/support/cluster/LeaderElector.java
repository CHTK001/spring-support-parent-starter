package com.chua.starter.job.support.cluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Leader 选举器 (本地状态层)。
 *
 * <p>当前进程内维护"本地是否为 Leader"的状态, 实际抢占由
 * {@link LeaderElectionResolver} SPI 实现 (默认 CAS 占位,
 * 类路径 含 MyBatis-Plus 时切换为 {@code MysqlLeaderElectionResolver})。</p>
 *
 * <h3>使用</h3>
 * <pre>{@code
 * LeaderElector elector = new LeaderElector("node-1");
 * LeaderElectionResolver resolver = ...;
 * boolean ok = resolver.tryAcquire("payment", "node-1", 15);
 * if (ok) {
 *     elector.markLeader("payment");
 *     // 启动单秒循环
 * }
 * }</pre>kLeader("payment");
 *     // 启动单秒循环
 * }
 * }</pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
public class LeaderElector {

    /**
     * 当前进程节点标识
     */
    private final String nodeId;

    /**
     * 铅 状态标记 (CAS)
     */
    private final AtomicBoolean leader = new AtomicBoolean(false);

    /**
     * 当前所属命名空间
     */
    private volatile String currentGroup;

    /**
     * 标记当前节点为指定命名空间的 Leader (本地态)。
     *
     * @param group 命名空间
     */
    public void markLeader(String group) {
        leader.set(true);
        currentGroup = group;
        log.info("[job] 本地标记为 Leader nodeId={} group={}", nodeId, group);
    }

    /**
     * 主动释放 Leader 权 (本地态 + 调用 SPI release)。
     *
     * @param resolver 选举解析器
     */
    public void release(LeaderElectionResolver resolver) {
        if (leader.compareAndSet(true, false)) {
            String group = currentGroup;
            log.info("[job] 释放 Leader 权 nodeId={} group={}", nodeId, group);
            if (resolver != null && group != null) {
                resolver.release(group, nodeId);
            }
        }
    }

    /**
     * 仅释放本地态 (不由 SPI 释放, 多用于心跳线程)。
     */
    public void release() {
        if (leader.compareAndSet(true, false)) {
            log.info("[job] 释放本地 Leader 态 nodeId={} group={}", nodeId, currentGroup);
        }
    }

    /**
     * 当前是否为 Leader。
     *
     * @return Leader 时返回 true
     */
    public boolean isLeader() {
        return leader.get();
    }

    /**
     * 当前所属命名空间。
     *
     * @return 命名空间
     */
    public String currentGroup() {
        return currentGroup;
    }

    /**
     * 节点标识。
     *
     * @return 节点 标识
     */
    public String nodeId() {
        return nodeId;
    }

    /**
     * 本地 CAS 抢占 (无 SPI 时的兜底实现)。
     *
     * @param group        命名空间
     * @param leaseSeconds 租约时长 (秒)
     * @return 抢占成功返回 true
     */
    public boolean tryAcquireLeader(String group, int leaseSeconds) {
        if (leader.compareAndSet(false, true)) {
            currentGroup = group;
            log.info("[job] 抢占 Leader 成功 (CAS 占位) nodeId={} group={} lease={}s",
                    nodeId, group, leaseSeconds);
            return true;
        }
        return false;
    }
}
