package com.chua.starter.server.support.service.impl;

import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.server.support.constants.ServerSocketEvents;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.entity.ServerService;
import com.chua.starter.server.support.entity.ServerServiceOperationLog;
import com.chua.starter.server.support.enums.ServerServiceOperationType;
import com.chua.starter.server.support.mapper.ServerServiceMapper;
import com.chua.starter.server.support.model.ServerAiTaskPayload;
import com.chua.starter.server.support.model.ServerAiTaskTicket;
import com.chua.starter.server.support.model.ServerServiceAiAdvice;
import com.chua.starter.server.support.model.ServerServiceAiDraft;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerRealtimePublisher;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ServerServiceAiTaskService {

    private final Executor executor;
    private final ServerHostService serverHostService;
    private final ServerServiceMapper serverServiceMapper;
    private final ServerServiceAiAdvisor aiAdvisor;
    private final ServerServiceAiDraftAdvisor aiDraftAdvisor;
    private final ServerServiceOperationLogService operationLogService;
    private final ServerRealtimePublisher serverRealtimePublisher;

    public ServerServiceAiTaskService(
            @Qualifier(Constant.DEFAULT_EXECUTOR) Executor executor,
            ServerHostService serverHostService,
            ServerServiceMapper serverServiceMapper,
            ServerServiceAiAdvisor aiAdvisor,
            ServerServiceAiDraftAdvisor aiDraftAdvisor,
            ServerServiceOperationLogService operationLogService,
            ServerRealtimePublisher serverRealtimePublisher
    ) {
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
        this.serverHostService = serverHostService;
        this.serverServiceMapper = serverServiceMapper;
        this.aiAdvisor = aiAdvisor;
        this.aiDraftAdvisor = aiDraftAdvisor;
        this.operationLogService = operationLogService;
        this.serverRealtimePublisher = serverRealtimePublisher;
    }

    public ServerAiTaskTicket scheduleFailureDiagnosis(
            ServerService service,
            ServerHost host,
            ServerServiceOperationType operationType,
            Integer exitCode,
            String runtimeStatus,
            String output,
            Integer operationLogId
    ) {
        String taskId = UUID.randomUUID().toString();
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("DIAGNOSE_FAILURE")
                .status("PENDING")
                .serverId(host == null ? null : host.getServerId())
                .serverServiceId(service == null ? null : service.getServerServiceId())
                .operationLogId(operationLogId)
                .message("AI 诊断任务已受理")
                .build());
        CompletableFuture.runAsync(() -> runFailureDiagnosis(taskId, service, host, operationType, exitCode, runtimeStatus, output, operationLogId), executor);
        return ServerAiTaskTicket.builder()
                .taskId(taskId)
                .taskType("DIAGNOSE_FAILURE")
                .status("PENDING")
                .serverServiceId(service == null ? null : service.getServerServiceId())
                .operationLogId(operationLogId)
                .message("AI 诊断任务已受理")
                .build();
    }

    public ServerAiTaskTicket scheduleDraftGeneration(ServerService service) {
        String taskId = UUID.randomUUID().toString();
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("GENERATE_DRAFT")
                .status("PENDING")
                .serverId(service == null ? null : service.getServerId())
                .serverServiceId(service == null ? null : service.getServerServiceId())
                .message("AI 草稿生成任务已受理")
                .build());
        CompletableFuture.runAsync(() -> runDraftGeneration(taskId, service), executor);
        return ServerAiTaskTicket.builder()
                .taskId(taskId)
                .taskType("GENERATE_DRAFT")
                .status("PENDING")
                .serverServiceId(service == null ? null : service.getServerServiceId())
                .message("AI 草稿生成任务已受理")
                .build();
    }

    private void runFailureDiagnosis(
            String taskId,
            ServerService service,
            ServerHost host,
            ServerServiceOperationType operationType,
            Integer exitCode,
            String runtimeStatus,
            String output,
            Integer operationLogId
    ) {
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("DIAGNOSE_FAILURE")
                .status("RUNNING")
                .serverId(host == null ? null : host.getServerId())
                .serverServiceId(service == null ? null : service.getServerServiceId())
                .operationLogId(operationLogId)
                .message("AI 正在分析失败原因")
                .build());
        try {
            ServerServiceAiAdvice advice = aiAdvisor.diagnose(host, service, operationType, exitCode, runtimeStatus, output);
            ServerService fresh = reloadService(service == null ? null : service.getServerServiceId());
            if (advice == null || (!StringUtils.hasText(advice.getReason()) && !StringUtils.hasText(advice.getSolution()))) {
                updateServiceMessage(fresh, "执行失败，AI 未返回可用诊断");
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("DIAGNOSE_FAILURE")
                        .status("FAILED")
                        .serverId(host == null ? null : host.getServerId())
                        .serverServiceId(fresh == null ? null : fresh.getServerServiceId())
                        .operationLogId(operationLogId)
                        .message("AI 未返回可用诊断")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            ServerServiceOperationLog log = operationLogService.applyAiAdvice(
                    fresh,
                    host,
                    operationLogId,
                    "执行失败，AI 已生成诊断方案",
                    advice);
            updateServiceMessage(fresh, "执行失败，AI 已生成诊断方案");
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("DIAGNOSE_FAILURE")
                    .status("COMPLETED")
                    .serverId(host == null ? null : host.getServerId())
                    .serverServiceId(fresh == null ? null : fresh.getServerServiceId())
                    .operationLogId(operationLogId)
                    .message("AI 诊断完成")
                    .aiReason(advice.getReason())
                    .aiSolution(advice.getSolution())
                    .aiFixScript(advice.getFixScript())
                    .aiProvider(advice.getProvider())
                    .aiModel(advice.getModel())
                    .knowledgeId(log == null ? null : log.getKnowledgeId())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            ServerService fresh = reloadService(service == null ? null : service.getServerServiceId());
            updateServiceMessage(fresh, "执行失败，AI 诊断任务异常");
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("DIAGNOSE_FAILURE")
                    .status("FAILED")
                    .serverId(host == null ? null : host.getServerId())
                    .serverServiceId(fresh == null ? null : fresh.getServerServiceId())
                    .operationLogId(operationLogId)
                    .message("AI 诊断失败: " + e.getMessage())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        }
    }

    private void runDraftGeneration(String taskId, ServerService service) {
        publish(taskId, ServerAiTaskPayload.builder()
                .taskId(taskId)
                .taskType("GENERATE_DRAFT")
                .status("RUNNING")
                .serverId(service == null ? null : service.getServerId())
                .serverServiceId(service == null ? null : service.getServerServiceId())
                .message("AI 正在生成配置与脚本草稿")
                .build());
        try {
            ServerService fresh = reloadService(service == null ? null : service.getServerServiceId());
            ServerHost host = fresh == null || fresh.getServerId() == null ? null : serverHostService.getHost(fresh.getServerId());
            ServerServiceAiDraft draft = aiDraftAdvisor.generate(host, fresh);
            if (draft == null) {
                publish(taskId, ServerAiTaskPayload.builder()
                        .taskId(taskId)
                        .taskType("GENERATE_DRAFT")
                        .status("FAILED")
                        .serverId(host == null ? null : host.getServerId())
                        .serverServiceId(fresh == null ? null : fresh.getServerServiceId())
                        .message("AI 未返回可用草稿")
                        .finishedAt(System.currentTimeMillis())
                        .build());
                return;
            }
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("GENERATE_DRAFT")
                    .status("COMPLETED")
                    .serverId(host == null ? null : host.getServerId())
                    .serverServiceId(fresh == null ? null : fresh.getServerServiceId())
                    .message("AI 草稿已生成")
                    .aiProvider(draft.getProvider())
                    .aiModel(draft.getModel())
                    .draft(draft)
                    .finishedAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            publish(taskId, ServerAiTaskPayload.builder()
                    .taskId(taskId)
                    .taskType("GENERATE_DRAFT")
                    .status("FAILED")
                    .serverId(service == null ? null : service.getServerId())
                    .serverServiceId(service == null ? null : service.getServerServiceId())
                    .message("AI 草稿生成失败: " + e.getMessage())
                    .finishedAt(System.currentTimeMillis())
                    .build());
        }
    }

    private void updateServiceMessage(ServerService service, String message) {
        if (service == null || service.getServerServiceId() == null) {
            return;
        }
        service.setLastOperationTime(LocalDateTime.now());
        service.setLastOperationMessage(message);
        serverServiceMapper.updateById(service);
        ServerService refreshed = reloadService(service.getServerServiceId());
        if (refreshed == null) {
            return;
        }
        operationLogService.fillLatest(refreshed);
        serverRealtimePublisher.publish(
                ServerSocketEvents.MODULE,
                ServerSocketEvents.SERVER_SERVICE,
                refreshed.getServerServiceId(),
                refreshed);
    }

    private ServerService reloadService(Integer serviceId) {
        return serviceId == null ? null : serverServiceMapper.selectById(serviceId);
    }

    private void publish(String taskId, ServerAiTaskPayload payload) {
        serverRealtimePublisher.publish(ServerSocketEvents.MODULE, ServerSocketEvents.SERVER_AI_TASK, taskId, payload);
    }
}
