package com.chua.starter.server.support.service.impl;

import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.server.support.constants.ServerSocketEvents;
import com.chua.starter.server.support.entity.ServerAlertEvent;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerAiTaskPayload;
import com.chua.starter.server.support.model.ServerAiTaskTicket;
import com.chua.starter.server.support.model.ServerHostAiAdvice;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.chua.starter.server.support.service.ServerAlertService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerMetricsService;
import com.chua.starter.server.support.service.ServerRealtimePublisher;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ServerHostAiTaskService {

    private final Executor executor;
    private final ServerHostService serverHostService;
    private final ServerMetricsService serverMetricsService;
    private final ServerAlertService serverAlertService;
    private final ServerHostAiAdvisor serverHostAiAdvisor;
    private final ServerRealtimePublisher serverRealtimePublisher;

    public ServerHostAiTaskService(
            @Qualifier(Constant.DEFAULT_EXECUTOR) Executor executor,
            ServerHostService serverHostService,
            ServerMetricsService serverMetricsService,
            ServerAlertService serverAlertService,
            ServerHostAiAdvisor serverHostAiAdvisor,
            ServerRealtimePublisher serverRealtimePublisher
    ) {
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
        this.serverHostService = serverHostService;
        this.serverMetricsService = serverMetricsService;
        this.serverAlertService = serverAlertService;
        this.serverHostAiAdvisor = serverHostAiAdvisor;
        this.serverRealtimePublisher = serverRealtimePublisher;
    }

    public ServerAiTaskTicket scheduleStabilityAnalysis(Integer serverId) {
        String taskId = UUID.randomUUID().toString();
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("ANALYZE_HOST_STABILITY")
                .status("PENDING")
                .serverId(serverId)
                .message("AI 稳定性分析任务已受理")
                .build());
        CompletableFuture.runAsync(() -> runStabilityAnalysis(taskId, serverId), executor);
        return ServerAiTaskTicket.builder()
                .taskId(taskId)
                .taskType("ANALYZE_HOST_STABILITY")
                .status("PENDING")
                .message("AI 稳定性分析任务已受理")
                .build();
    }

    private void runStabilityAnalysis(String taskId, Integer serverId) {
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("ANALYZE_HOST_STABILITY")
                .status("RUNNING")
                .serverId(serverId)
                .message("AI 正在分析当前服务器稳定性")
                .build());
        try {
            ServerHost host = serverHostService.getHost(serverId);
            if (host == null) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("ANALYZE_HOST_STABILITY")
                        .status("FAILED")
                        .serverId(serverId)
                        .message("未找到服务器")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            ServerMetricsSnapshot snapshot = serverMetricsService.getSnapshot(serverId);
            ServerMetricsDetail detail = serverMetricsService.getDetail(serverId);
            List<ServerAlertEvent> alerts = serverAlertService.listAlerts(serverId, 5);
            ServerHostAiAdvice advice = serverHostAiAdvisor.analyze(host, snapshot, detail, alerts);
            if (advice == null || !StringUtils.hasText(advice.getSummary())) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("ANALYZE_HOST_STABILITY")
                        .status("FAILED")
                        .serverId(serverId)
                        .message("AI 未返回有效的稳定性分析结果")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("ANALYZE_HOST_STABILITY")
                    .status("COMPLETED")
                    .serverId(serverId)
                    .message("AI 稳定性分析完成" + (StringUtils.hasText(advice.getRiskLevel()) ? " · " + advice.getRiskLevel() : ""))
                    .aiReason(advice.getSummary())
                    .aiSolution(advice.getSuggestion())
                    .aiProvider(advice.getProvider())
                    .aiModel(advice.getModel())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("ANALYZE_HOST_STABILITY")
                    .status("FAILED")
                    .serverId(serverId)
                    .message("AI 稳定性分析失败: " + e.getMessage())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        }
    }

    private void publish(String taskId, ServerAiTaskPayload payload) {
        serverRealtimePublisher.publish(ServerSocketEvents.MODULE, ServerSocketEvents.SERVER_AI_TASK, taskId, payload);
    }
}
