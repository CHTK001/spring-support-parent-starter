package com.chua.starter.job.support.cluster;

import java.time.Instant;

/**
 * 集群 Leader 状态快照。
 *
 * @author CH
 * @since 4.0.0.42
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
