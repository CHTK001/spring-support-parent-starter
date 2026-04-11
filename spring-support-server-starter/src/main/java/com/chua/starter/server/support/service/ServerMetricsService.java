package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsTaskSettings;
import com.chua.starter.server.support.model.ServerMetricsTaskSettingsRequest;
import java.util.List;

public interface ServerMetricsService {

    /**
     * 返回指定服务器最新一次缓存的指标快照。
     */
    ServerMetricsSnapshot getSnapshot(Integer serverId);

    /**
     * 返回指定服务器的详情视图，包含磁盘分区与网络接口明细。
     */
    ServerMetricsDetail getDetail(Integer serverId);

    /**
     * 列出所有服务器的最新指标快照。
     */
    List<ServerMetricsSnapshot> listSnapshots();

    /**
     * 立即触发一次全量指标采集。
     */
    List<ServerMetricsSnapshot> refreshMetrics();

    /**
     * 查询指定服务器的指标历史。
     */
    List<ServerMetricsSnapshot> listHistory(Integer serverId, Integer minutes);

    /**
     * 返回当前指标采集任务配置以及 job-starter 同步状态。
     */
    ServerMetricsTaskSettings getTaskSettings();

    /**
     * 更新指标采集任务配置，并同步到底层调度器。
     */
    ServerMetricsTaskSettings updateTaskSettings(ServerMetricsTaskSettingsRequest request);

    /**
     * job-starter 不可用时的本地刷新兜底入口。
     */
    void scheduledRefreshFallback();

    /**
     * job-starter 不可用时的本地清理兜底入口。
     */
    void cleanupExpiredHistoryFallback();
}
