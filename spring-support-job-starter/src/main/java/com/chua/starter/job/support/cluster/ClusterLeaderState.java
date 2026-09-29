package com.chua.starter.job.support.cluster;

import java.time.Instant;

/**
 * 集群 Leader 状态快照。
 *
 * <p>描述选主租约命名空间下某一时刻的视图：谁持有租约、租约何时到期、以及本节点是否就是那个持有者。
 * 本记录只承载快照，不含续约或抢占行为；相关动作由 {@link LeaderElector} 与
 * {@link LeaderElectionResolver} 完成。</p>
 *
 * @param group         选主租约命名空间，即 {@link LeaderElectionResolver#tryAcquire(String, String, int)} 的第一个入参
 *                      （配置项 {@code plugin.job.cluster.election-namespace}，留空时按应用名推导）。注意它是租约命名空间，
 *                      <b>不是</b>作业归属分组 {@code plugin.job.cluster.group}：作业分组允许跨部署重名，租约命名空间不允许，
 *                      两者混用会让两个独立部署抢同一条租约行。本记录不做归一化，传入什么就存什么
 * @param leaderNodeId  当前持有租约的节点标识，与 {@link LeaderElector#nodeId()} 同一套节点 ID 体系（如 {@code node-1}）；
 *                      无人持有租约（从未被抢占、或已被 {@code release} 清掉）时为 {@code null}，
 *                      此时 {@code leaseExpireAt} 通常也一并为空
 * @param leaseExpireAt 租约到期时刻，绝对时间点（{@link Instant}，UTC 时区），由持有者按租约时长
 *                      （{@code tryAcquire} 的 {@code leaseSeconds}，秒）推算写入。到达该时刻后租约视为过期、
 *                      其他节点即可抢占，故判断过期必须比较本时刻而非猜测墙钟；无租约记录时为 {@code null}
 * @param currentLeader 本节点当前是否为该命名空间的 Leader，对应 {@link LeaderElector#isLeader()} 的进程内本地态。
 *                      原生 {@code boolean}，不可为 {@code null}。与 {@code leaderNodeId} 的一致性由生成快照的一方保证，
 *                      快照本身可能已过期，使用前应自行核对 {@code leaseExpireAt}
 *
 * @since 4.0.0.42
 * @author CH
 */
public record ClusterLeaderState(
        String group,
        String leaderNodeId,
        Instant leaseExpireAt,
        boolean currentLeader
) {

    /**
     * 当前节点是否为 Leader。
     *
     * @return 是返回 true
     */
    public boolean isCurrentLeader() {
        return currentLeader;
    }
}
