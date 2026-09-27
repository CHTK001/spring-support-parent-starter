package com.chua.starter.monitor.support.model;

/**
 * ZooKeeper 集群节点指标（取自四字命令 {@code mntr}）。
 *
 * <p>每个集群节点一行；{@code zkServerState} 为 leader/follower/standalone，
 * 前端据此高亮 leader 行。个别指标在未放开四字命令时为 null。</p>
 *
 * @param host                    节点地址 host:port
 * @param zkVersion               zk_version
 * @param zkAvgLatency            zk_avg_latency
 * @param zkMinLatency            zk_min_latency
 * @param zkMaxLatency            zk_max_latency
 * @param zkPacketsReceived       zk_packets_received
 * @param zkPacketsSent           zk_packets_sent
 * @param zkNumAliveConnections   zk_num_alive_connections
 * @param zkOutstandingRequests  zk_outstanding_requests
 * @param zkZnodeCount            zk_znode_count
 * @param zkWatchCount            zk_watch_count
 * @param zkApproximateDataSize   zk_approximate_data_size
 * @param zkOpenFileDescriptorCount zk_open_file_descriptor_count
 * @param zkServerState           zk_server_state（leader/follower/standalone）
 * @param zkPeerState             zk_peer_state，可能为空
 * @param error                   四字命令失败原因，成功时为 null
 * @author CH
 * @since 4.0.0.43
 */
public record ZkClusterNodeVO(
        String host,
        String zkVersion,
        Long zkAvgLatency,
        Long zkMinLatency,
        Long zkMaxLatency,
        Long zkPacketsReceived,
        Long zkPacketsSent,
        Long zkNumAliveConnections,
        Long zkOutstandingRequests,
        Long zkZnodeCount,
        Long zkWatchCount,
        Long zkApproximateDataSize,
        Long zkOpenFileDescriptorCount,
        String zkServerState,
        String zkPeerState,
        String error
) {
}
