package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.core.utils.DigestUtils;
import com.chua.starter.server.support.config.ServerManagementProperties;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.entity.ServerService;
import com.chua.starter.server.support.entity.ServerServiceAiKnowledge;
import com.chua.starter.server.support.entity.ServerServiceOperationLog;
import com.chua.starter.server.support.enums.ServerServiceOperationType;
import com.chua.starter.server.support.mapper.ServerServiceAiKnowledgeMapper;
import com.chua.starter.server.support.mapper.ServerServiceOperationLogMapper;
import com.chua.starter.server.support.model.ServerServiceAiAdvice;
import com.chua.starter.server.support.util.ServerCommandSupport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerServiceOperationLogService {

    private final ServerServiceOperationLogMapper operationLogMapper;
    private final ServerServiceAiKnowledgeMapper aiKnowledgeMapper;
    private final ServerManagementProperties properties;
    private final ServerAuditExecutor auditExecutor;

    public ServerServiceOperationLog record(
            ServerService service,
            ServerHost host,
            ServerServiceOperationType operationType,
            ServerCommandSupport.CommandResult commandResult,
            String runtimeStatus,
            String message,
            ServerServiceAiAdvice advice
    ) {
        return auditExecutor.supply(() -> {
            Integer knowledgeId = upsertKnowledge(service, host, commandResult == null ? null : commandResult.output(), advice);
            ServerServiceOperationLog log = new ServerServiceOperationLog();
            log.setServerServiceId(service == null ? null : service.getServerServiceId());
            log.setServerId(host == null ? null : host.getServerId());
            log.setOperationType(operationType == null ? null : operationType.name());
            log.setSuccess(commandResult != null && commandResult.success());
            log.setExitCode(commandResult == null ? null : commandResult.exitCode());
            log.setRuntimeStatus(runtimeStatus);
            log.setOperationMessage(message);
            log.setOperationOutput(commandResult == null ? null : commandResult.output());
            log.setAiReason(advice == null ? null : advice.getReason());
            log.setAiSolution(advice == null ? null : advice.getSolution());
            log.setAiFixScript(advice == null ? null : advice.getFixScript());
            log.setAiProvider(advice == null ? null : advice.getProvider());
            log.setAiModel(advice == null ? null : advice.getModel());
            log.setKnowledgeId(knowledgeId);
            log.setExpireAt(LocalDateTime.now().plusDays(Math.max(1, properties.getServiceOperation().getLogRetentionDays())));
            operationLogMapper.insert(log);
            return log;
        });
    }

    public List<ServerServiceOperationLog> listRecent(Integer serverServiceId, Integer limit) {
        int size = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        return operationLogMapper.selectList(Wrappers.<ServerServiceOperationLog>lambdaQuery()
                .eq(ServerServiceOperationLog::getServerServiceId, serverServiceId)
                .orderByDesc(ServerServiceOperationLog::getCreateTime, ServerServiceOperationLog::getServerServiceOperationLogId)
                .last("limit " + size));
    }

    public ServerServiceOperationLog getLatest(Integer serverServiceId) {
        return operationLogMapper.selectOne(Wrappers.<ServerServiceOperationLog>lambdaQuery()
                .eq(ServerServiceOperationLog::getServerServiceId, serverServiceId)
                .orderByDesc(ServerServiceOperationLog::getCreateTime, ServerServiceOperationLog::getServerServiceOperationLogId)
                .last("limit 1"));
    }

    public ServerServiceOperationLog getLatestFailure(Integer serverServiceId) {
        return operationLogMapper.selectOne(Wrappers.<ServerServiceOperationLog>lambdaQuery()
                .eq(ServerServiceOperationLog::getServerServiceId, serverServiceId)
                .eq(ServerServiceOperationLog::getSuccess, Boolean.FALSE)
                .orderByDesc(ServerServiceOperationLog::getCreateTime, ServerServiceOperationLog::getServerServiceOperationLogId)
                .last("limit 1"));
    }

    public ServerServiceOperationLog applyAiAdvice(
            ServerService service,
            ServerHost host,
            Integer operationLogId,
            String message,
            ServerServiceAiAdvice advice
    ) {
        return auditExecutor.supply(() -> {
            if (operationLogId == null) {
                return null;
            }
            ServerServiceOperationLog log = operationLogMapper.selectById(operationLogId);
            if (log == null) {
                return null;
            }
            Integer knowledgeId = upsertKnowledge(service, host, log.getOperationOutput(), advice);
            if (StringUtils.hasText(message)) {
                log.setOperationMessage(message);
            }
            log.setAiReason(advice == null ? null : advice.getReason());
            log.setAiSolution(advice == null ? null : advice.getSolution());
            log.setAiFixScript(advice == null ? null : advice.getFixScript());
            log.setAiProvider(advice == null ? null : advice.getProvider());
            log.setAiModel(advice == null ? null : advice.getModel());
            log.setKnowledgeId(knowledgeId);
            operationLogMapper.updateById(log);
            return operationLogMapper.selectById(operationLogId);
        });
    }

    public void fillLatest(ServerService service) {
        if (service == null || service.getServerServiceId() == null) {
            return;
        }
        ServerServiceOperationLog latest = getLatest(service.getServerServiceId());
        if (latest == null) {
            return;
        }
        service.setLatestOperationLogId(latest.getServerServiceOperationLogId());
        service.setLatestOperationType(latest.getOperationType());
        service.setLatestOperationSuccess(latest.getSuccess());
        service.setLatestOperationOutput(latest.getOperationOutput());
        service.setLatestAiReason(latest.getAiReason());
        service.setLatestAiSolution(latest.getAiSolution());
        service.setLatestAiFixScript(latest.getAiFixScript());
        service.setLatestAiProvider(latest.getAiProvider());
        service.setLatestAiModel(latest.getAiModel());
        service.setLatestKnowledgeId(latest.getKnowledgeId());
    }

    @Scheduled(cron = "0 20 3 * * ?")
    public void cleanupExpiredLogs() {
        auditExecutor.run(() -> operationLogMapper.delete(Wrappers.<ServerServiceOperationLog>lambdaQuery()
                .isNotNull(ServerServiceOperationLog::getExpireAt)
                .lt(ServerServiceOperationLog::getExpireAt, LocalDateTime.now())));
    }

    private Integer upsertKnowledge(
            ServerService service,
            ServerHost host,
            String output,
            ServerServiceAiAdvice advice
    ) {
        if (advice == null || (!StringUtils.hasText(advice.getReason()) && !StringUtils.hasText(advice.getSolution()))) {
            return null;
        }
        String knowledgeKey = DigestUtils.md5Hex(String.join("|",
                safe(service == null ? null : service.getServiceName()),
                safe(service == null ? null : service.getServiceType()),
                safe(host == null ? null : host.getServerType()),
                safe(host == null ? null : host.getOsType()),
                safe(advice.getReason()),
                safe(advice.getSolution())).toLowerCase(Locale.ROOT));
        ServerServiceAiKnowledge knowledge = aiKnowledgeMapper.selectOne(Wrappers.<ServerServiceAiKnowledge>lambdaQuery()
                .eq(ServerServiceAiKnowledge::getKnowledgeKey, knowledgeKey)
                .last("limit 1"));
        if (knowledge == null) {
            knowledge = new ServerServiceAiKnowledge();
            knowledge.setKnowledgeKey(knowledgeKey);
        }
        knowledge.setServiceName(service == null ? null : service.getServiceName());
        knowledge.setServiceType(service == null ? null : service.getServiceType());
        knowledge.setServerType(host == null ? null : host.getServerType());
        knowledge.setOsType(host == null ? null : host.getOsType());
        knowledge.setReason(advice.getReason());
        knowledge.setSolution(advice.getSolution());
        knowledge.setFixScript(advice.getFixScript());
        knowledge.setProvider(advice.getProvider());
        knowledge.setModel(advice.getModel());
        knowledge.setSampleOutput(limit(output, 4000));
        if (knowledge.getServerServiceAiKnowledgeId() == null) {
            aiKnowledgeMapper.insert(knowledge);
        } else {
            aiKnowledgeMapper.updateById(knowledge);
        }
        return knowledge.getServerServiceAiKnowledgeId();
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
