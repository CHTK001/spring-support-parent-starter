package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.core.utils.DigestUtils;
import com.chua.starter.server.support.constants.ServerSocketEvents;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.entity.ServerService;
import com.chua.starter.server.support.entity.ServerServiceOperationLog;
import com.chua.starter.server.support.enums.ServerServiceOperationType;
import com.chua.starter.server.support.mapper.ServerHostMapper;
import com.chua.starter.server.support.mapper.ServerServiceMapper;
import com.chua.starter.server.support.model.ServerAiTaskTicket;
import com.chua.starter.server.support.model.ServerServiceAiAdvice;
import com.chua.starter.server.support.model.ServerServiceCommandResult;
import com.chua.starter.server.support.model.ServerServiceConfigWriteRequest;
import com.chua.starter.server.support.model.ServerServiceUpsertRequest;
import com.chua.starter.server.support.service.ServerFileService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerRealtimePublisher;
import com.chua.starter.server.support.service.ServerServiceService;
import com.chua.starter.server.support.util.ServerCommandSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerServiceServiceImpl extends ServiceImpl<ServerServiceMapper, ServerService> implements ServerServiceService {

    private final ServerHostService serverHostService;
    private final ServerHostMapper serverHostMapper;
    private final ServerHostCommandExecutor hostCommandExecutor;
    private final ServerServiceDiscoveryService serverServiceDiscoveryService;
    private final ServerServiceOperationLogService operationLogService;
    private final ServerFileService serverFileService;
    private final ServerRealtimePublisher serverRealtimePublisher;
    private final ServerServiceAiTaskService aiTaskService;
    private final ServerAuditExecutor auditExecutor;
    private final ObjectMapper objectMapper;

    @Override
    public List<ServerService> listServices(Integer serverId, Boolean enabled) {
        List<ServerService> services = list(Wrappers.<ServerService>lambdaQuery()
                .eq(serverId != null, ServerService::getServerId, serverId)
                .eq(enabled != null, ServerService::getEnabled, enabled)
                .orderByAsc(ServerService::getServerId, ServerService::getServiceName, ServerService::getServerServiceId));
        services.forEach(this::hydrate);
        return services;
    }

    @Override
    public ServerService getService(Integer id) {
        ServerService service = getOne(Wrappers.<ServerService>lambdaQuery()
                .eq(ServerService::getServerServiceId, id)
                .last("limit 1"));
        if (service == null) {
            throw new IllegalStateException("服务器服务不存在: " + id);
        }
        hydrate(service);
        return service;
    }

    @Override
    public ServerService saveService(ServerService service) {
        normalize(service, resolveHost(service));
        if (service.getServerServiceId() == null) {
            auditExecutor.run(() -> save(service));
        } else {
            auditExecutor.run(() -> updateById(service));
        }
        ServerService saved = getService(service.getServerServiceId());
        publishService(saved);
        return saved;
    }

    @Override
    public void deleteService(Integer id) {
        auditExecutor.run(() -> removeById(id));
    }

    @Override
    public ServerService saveManagedService(ServerServiceUpsertRequest request) {
        ServerHost host = resolveHost(request);
        ServerService service = getManagedService(request.getSoftInstallationId());
        if (service == null && StringUtils.hasText(request.getServiceName())) {
            service = getOne(Wrappers.<ServerService>lambdaQuery()
                    .eq(ServerService::getServerId, host.getServerId())
                    .eq(ServerService::getServiceName, request.getServiceName())
                    .last("limit 1"));
        }
        if (service == null) {
            service = new ServerService();
        }
        service.setServerId(host.getServerId());
        service.setServiceName(trim(request.getServiceName()));
        service.setServiceType(trim(request.getServiceType()));
        service.setSoftPackageId(normalizeOptionalId(request.getSoftPackageId()));
        service.setSoftPackageVersionId(normalizeOptionalId(request.getSoftPackageVersionId()));
        service.setSoftInstallationId(normalizeOptionalId(request.getSoftInstallationId()));
        service.setInstallPath(trim(request.getInstallPath()));
        service.setConfigPathsJson(trim(request.getConfigPathsJson()));
        service.setLogPathsJson(trim(request.getLogPathsJson()));
        service.setConfigTemplate(trim(request.getConfigTemplate()));
        service.setInitScript(trim(request.getInitScript()));
        service.setInstallScript(trim(request.getInstallScript()));
        service.setUninstallScript(trim(request.getUninstallScript()));
        service.setDetectScript(trim(request.getDetectScript()));
        service.setRegisterScript(trim(request.getRegisterScript()));
        service.setUnregisterScript(trim(request.getUnregisterScript()));
        service.setStartScript(trim(request.getStartScript()));
        service.setStopScript(trim(request.getStopScript()));
        service.setRestartScript(trim(request.getRestartScript()));
        service.setStatusScript(trim(request.getStatusScript()));
        service.setDescription(trim(request.getDescription()));
        service.setMetadataJson(trim(request.getMetadataJson()));
        if (request.getEnabled() != null) {
            service.setEnabled(request.getEnabled());
        }
        normalize(service, host);
        ServerService target = service;
        if (target.getServerServiceId() == null) {
            auditExecutor.run(() -> save(target));
        } else {
            auditExecutor.run(() -> updateById(target));
        }
        ServerService saved = getService(target.getServerServiceId());
        publishService(saved);
        return saved;
    }

    @Override
    public ServerService getBySoftInstallationId(Integer installationId) {
        ServerService service = getManagedService(installationId);
        if (service == null) {
            throw new IllegalStateException("当前安装未绑定服务器服务: " + installationId);
        }
        hydrate(service);
        return service;
    }

    @Override
    public boolean deleteBySoftInstallationId(Integer installationId) {
        ServerService service = getManagedService(installationId);
        if (service == null) {
            return false;
        }
        return auditExecutor.supply(() -> removeById(service.getServerServiceId()));
    }

    @Override
    public List<ServerService> detectServices(Integer serverId) throws Exception {
        ServerHost host = requireHost(serverId);
        List<ServerService> services = serverServiceDiscoveryService.detectAndSync(host);
        services.forEach(this::normalizeDetectedService);
        services.forEach(this::hydrate);
        services.forEach(this::publishService);
        return services;
    }

    @Override
    public List<ServerServiceOperationLog> listOperationLogs(Integer serverServiceId, Integer limit) {
        return operationLogService.listRecent(serverServiceId, limit);
    }

    @Override
    public ServerAiTaskTicket generateAiDraft(Integer id) {
        ServerService service = getService(id);
        return aiTaskService.scheduleDraftGeneration(service);
    }

    @Override
    public ServerServiceCommandResult writeConfig(Integer id, ServerServiceConfigWriteRequest request) throws Exception {
        ServerService service = getService(id);
        ServerHost host = requireHost(service.getServerId());
        String targetPath = resolveConfigPath(service, request);
        if (!StringUtils.hasText(targetPath)) {
            return buildConfigWriteFailure(service, host, "未配置可写入的配置路径");
        }
        String content = resolveConfigContent(service, request);
        if (content == null) {
            return buildConfigWriteFailure(service, host, "未配置可写入的配置内容");
        }
        try {
            serverFileService.writeContent(host.getServerId(), targetPath, content);
        } catch (Exception e) {
            return buildConfigWriteFailure(service, host, e.getMessage());
        }
        String output = "配置已写入: " + targetPath;
        ServerCommandSupport.CommandResult commandResult = new ServerCommandSupport.CommandResult(true, 0, output);
        service.setLastOperationTime(LocalDateTime.now());
        service.setLastOperationMessage("配置已更新");
        auditExecutor.run(() -> updateById(service));
        ServerServiceOperationLog log = operationLogService.record(
                service,
                host,
                ServerServiceOperationType.CONFIG_WRITE,
                commandResult,
                service.getRuntimeStatus(),
                "配置已更新",
                null);
        hydrate(service);
        publishService(service);
        return ServerServiceCommandResult.builder()
                .serverServiceId(service.getServerServiceId())
                .serviceName(service.getServiceName())
                .operationType(ServerServiceOperationType.CONFIG_WRITE.name())
                .success(true)
                .exitCode(0)
                .message("配置已更新")
                .output(output)
                .runtimeStatus(service.getRuntimeStatus())
                .operationLogId(log.getServerServiceOperationLogId())
                .knowledgeId(log.getKnowledgeId())
                .build();
    }

    @Override
    public ServerServiceCommandResult operate(Integer id, ServerServiceOperationType operationType) throws Exception {
        ServerService service = getService(id);
        ServerHost host = requireHost(service.getServerId());
        if (!Boolean.TRUE.equals(service.getEnabled()) && operationType != ServerServiceOperationType.STATUS) {
            return buildFailureResult(service, host, operationType, "服务器服务已停用: " + service.getServiceName());
        }
        ServerCommandSupport.CommandResult commandResult;
        try {
            commandResult = executeOperation(service, host, operationType);
        } catch (Exception e) {
            return buildFailureResult(service, host, operationType, e.getMessage());
        }
        String runtimeStatus = updateRuntimeStatus(service, operationType, commandResult);
        boolean diagnoseAsync = shouldDiagnose(operationType, commandResult);
        String message = commandResult.success() ? "执行成功" : diagnoseAsync ? "执行失败，AI 诊断中" : "执行失败";
        service.setLastOperationTime(LocalDateTime.now());
        service.setLastOperationMessage(message);
        auditExecutor.run(() -> updateById(service));
        ServerServiceOperationLog log = operationLogService.record(service, host, operationType, commandResult, runtimeStatus, message, null);
        hydrate(service);
        publishService(service);
        ServerAiTaskTicket aiTask = diagnoseAsync
                ? aiTaskService.scheduleFailureDiagnosis(
                service,
                host,
                operationType,
                commandResult.exitCode(),
                runtimeStatus,
                commandResult.output(),
                log.getServerServiceOperationLogId())
                : null;
        return ServerServiceCommandResult.builder()
                .serverServiceId(service.getServerServiceId())
                .serviceName(service.getServiceName())
                .operationType(operationType.name())
                .success(commandResult.success())
                .exitCode(commandResult.exitCode())
                .message(message)
                .output(commandResult.output())
                .runtimeStatus(runtimeStatus)
                .operationLogId(log.getServerServiceOperationLogId())
                .knowledgeId(log.getKnowledgeId())
                .taskId(aiTask == null ? null : aiTask.getTaskId())
                .aiTaskStatus(aiTask == null ? null : aiTask.getStatus())
                .build();
    }

    @Override
    public ServerServiceCommandResult operateBySoftInstallationId(Integer installationId, ServerServiceOperationType operationType) throws Exception {
        return operate(getBySoftInstallationId(installationId).getServerServiceId(), operationType);
    }

    private ServerServiceCommandResult buildFailureResult(
            ServerService service,
            ServerHost host,
            ServerServiceOperationType operationType,
            String errorMessage
    ) {
        ServerCommandSupport.CommandResult commandResult = new ServerCommandSupport.CommandResult(false, -1, trim(errorMessage));
        String runtimeStatus = updateRuntimeStatus(service, operationType, commandResult);
        boolean diagnoseAsync = shouldDiagnose(operationType, commandResult);
        String message = diagnoseAsync ? "执行失败，AI 诊断中" : "执行失败";
        service.setLastOperationTime(LocalDateTime.now());
        service.setLastOperationMessage(message);
        auditExecutor.run(() -> updateById(service));
        ServerServiceOperationLog log = operationLogService.record(service, host, operationType, commandResult, runtimeStatus, message, null);
        hydrate(service);
        publishService(service);
        ServerAiTaskTicket aiTask = diagnoseAsync
                ? aiTaskService.scheduleFailureDiagnosis(
                service,
                host,
                operationType,
                commandResult.exitCode(),
                runtimeStatus,
                commandResult.output(),
                log.getServerServiceOperationLogId())
                : null;
        return ServerServiceCommandResult.builder()
                .serverServiceId(service.getServerServiceId())
                .serviceName(service.getServiceName())
                .operationType(operationType.name())
                .success(false)
                .exitCode(commandResult.exitCode())
                .message(message)
                .output(commandResult.output())
                .runtimeStatus(runtimeStatus)
                .operationLogId(log.getServerServiceOperationLogId())
                .knowledgeId(log.getKnowledgeId())
                .taskId(aiTask == null ? null : aiTask.getTaskId())
                .aiTaskStatus(aiTask == null ? null : aiTask.getStatus())
                .build();
    }

    private boolean shouldDiagnose(ServerServiceOperationType operationType, ServerCommandSupport.CommandResult commandResult) {
        if (commandResult == null || commandResult.success()) {
            return false;
        }
        String output = trim(commandResult.output());
        if (StringUtils.hasText(output)) {
            String normalized = output.toLowerCase(Locale.ROOT);
            if (normalized.contains("已停用")
                    || normalized.contains("未配置可执行脚本")
                    || normalized.contains("未找到可执行的 ai 修复脚本")) {
                return false;
            }
        }
        return operationType == ServerServiceOperationType.START
                || operationType == ServerServiceOperationType.RESTART
                || operationType == ServerServiceOperationType.AI_FIX;
    }

    private ServerServiceCommandResult buildConfigWriteFailure(
            ServerService service,
            ServerHost host,
            String errorMessage
    ) {
        ServerCommandSupport.CommandResult commandResult = new ServerCommandSupport.CommandResult(false, -1, trim(errorMessage));
        service.setLastOperationTime(LocalDateTime.now());
        service.setLastOperationMessage("配置更新失败");
        auditExecutor.run(() -> updateById(service));
        ServerServiceOperationLog log = operationLogService.record(
                service,
                host,
                ServerServiceOperationType.CONFIG_WRITE,
                commandResult,
                service.getRuntimeStatus(),
                "配置更新失败",
                null);
        hydrate(service);
        publishService(service);
        return ServerServiceCommandResult.builder()
                .serverServiceId(service.getServerServiceId())
                .serviceName(service.getServiceName())
                .operationType(ServerServiceOperationType.CONFIG_WRITE.name())
                .success(false)
                .exitCode(commandResult.exitCode())
                .message("配置更新失败")
                .output(commandResult.output())
                .runtimeStatus(service.getRuntimeStatus())
                .operationLogId(log.getServerServiceOperationLogId())
                .knowledgeId(log.getKnowledgeId())
                .build();
    }

    private String resolveConfigPath(ServerService service, ServerServiceConfigWriteRequest request) {
        if (StringUtils.hasText(request == null ? null : request.getPath())) {
            return request.getPath().trim();
        }
        List<String> paths = parseJsonArray(service == null ? null : service.getConfigPathsJson());
        return paths.isEmpty() ? null : paths.get(0);
    }

    private String resolveConfigContent(ServerService service, ServerServiceConfigWriteRequest request) {
        if (request != null && request.getContent() != null) {
            return request.getContent();
        }
        return service == null ? null : service.getConfigTemplate();
    }

    private List<String> parseJsonArray(String json) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return result;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root != null && root.isArray()) {
                root.forEach(item -> {
                    String value = trim(item == null ? null : item.asText());
                    if (StringUtils.hasText(value)) {
                        result.add(value);
                    }
                });
                return result;
            }
        } catch (Exception ignored) {
        }
        for (String item : json.split("[\\r\\n,;]+")) {
            String value = trim(item);
            if (StringUtils.hasText(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private ServerCommandSupport.CommandResult executeOperation(
            ServerService service,
            ServerHost host,
            ServerServiceOperationType operationType
    ) throws Exception {
        if (operationType == ServerServiceOperationType.AI_FIX) {
            return executeAiFix(service, host);
        }
        String script = switch (operationType) {
            case REGISTER -> resolveRegisterScript(service, host);
            case UNREGISTER -> resolveUnregisterScript(service, host);
            case START -> resolveStartScript(service, host);
            case STOP -> resolveStopScript(service, host);
            case RESTART -> resolveRestartScript(service, host);
            case STATUS -> resolveStatusScript(service, host);
            case AI_FIX -> null;
            case CONFIG_WRITE -> null;
        };
        if (!StringUtils.hasText(script)) {
            throw new IllegalStateException("未配置可执行脚本: " + operationType.name());
        }
        ServerCommandSupport.CommandResult result = executeCommand(host, ServerCommandSupport.renderScript(script, host, service));
        if (operationType != ServerServiceOperationType.START && operationType != ServerServiceOperationType.RESTART) {
            return result;
        }
        return verifyStarted(service, host, result);
    }

    private ServerCommandSupport.CommandResult executeAiFix(ServerService service, ServerHost host) throws Exception {
        ServerServiceOperationLog latestFailure = operationLogService.getLatestFailure(service.getServerServiceId());
        if (latestFailure == null || !StringUtils.hasText(latestFailure.getAiFixScript())) {
            return new ServerCommandSupport.CommandResult(false, -1, "未找到可执行的 AI 修复脚本");
        }
        ServerCommandSupport.CommandResult fixResult = executeCommand(
                host,
                ServerCommandSupport.renderScript(latestFailure.getAiFixScript(), host, service));
        if (!fixResult.success()) {
            return fixResult;
        }
        String startScript = resolveStartScript(service, host);
        if (!StringUtils.hasText(startScript)) {
            return new ServerCommandSupport.CommandResult(false, -1, joinOutputs(fixResult.output(), "AI 修复完成，但未配置启动脚本"));
        }
        ServerCommandSupport.CommandResult startResult = executeCommand(host, ServerCommandSupport.renderScript(startScript, host, service));
        ServerCommandSupport.CommandResult verified = verifyStarted(service, host, startResult);
        return new ServerCommandSupport.CommandResult(
                verified.success(),
                verified.exitCode(),
                joinOutputs(fixResult.output(), verified.output()));
    }

    private ServerCommandSupport.CommandResult verifyStarted(
            ServerService service,
            ServerHost host,
            ServerCommandSupport.CommandResult result
    ) throws Exception {
        if (!result.success()) {
            return result;
        }
        Thread.sleep(1200L);
        String statusScript = resolveStatusScript(service, host);
        if (!StringUtils.hasText(statusScript)) {
            return result;
        }
        ServerCommandSupport.CommandResult statusResult = executeCommand(host, ServerCommandSupport.renderScript(statusScript, host, service));
        String mergedOutput = joinOutputs(result.output(), statusResult.output());
        boolean success = statusResult.success() && "RUNNING".equalsIgnoreCase(resolveRuntimeStatus(statusResult.output(), statusResult.success()));
        return new ServerCommandSupport.CommandResult(success, statusResult.exitCode(), mergedOutput);
    }

    private String updateRuntimeStatus(
            ServerService service,
            ServerServiceOperationType operationType,
            ServerCommandSupport.CommandResult commandResult
    ) {
        String runtimeStatus = service.getRuntimeStatus();
        if (operationType == ServerServiceOperationType.STOP && commandResult.success()) {
            runtimeStatus = "STOPPED";
        } else if (operationType == ServerServiceOperationType.START
                || operationType == ServerServiceOperationType.RESTART
                || operationType == ServerServiceOperationType.AI_FIX) {
            runtimeStatus = commandResult.success() ? "RUNNING" : "ERROR";
        } else if (operationType == ServerServiceOperationType.STATUS) {
            runtimeStatus = resolveRuntimeStatus(commandResult.output(), commandResult.success());
        } else if (operationType == ServerServiceOperationType.UNREGISTER && commandResult.success()) {
            runtimeStatus = "STOPPED";
        }
        service.setRuntimeStatus(runtimeStatus);
        return runtimeStatus;
    }

    private String resolveRuntimeStatus(String output, boolean success) {
        String text = output == null ? "" : output.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return success ? "UNKNOWN" : "ERROR";
        }
        if (text.contains("running") || text.contains("active")) {
            return "RUNNING";
        }
        if (text.contains("stop") || text.contains("inactive") || text.contains("dead") || text.contains("not running")) {
            return "STOPPED";
        }
        if (text.contains("error") || text.contains("failed") || text.contains("exception")) {
            return "ERROR";
        }
        if (text.contains("unknown")) {
            return "UNKNOWN";
        }
        return success ? "RUNNING" : "UNKNOWN";
    }

    private ServerCommandSupport.CommandResult executeCommand(ServerHost host, String command) throws Exception {
        return hostCommandExecutor.execute(host, command);
    }

    private String resolveRegisterScript(ServerService service, ServerHost host) {
        if (StringUtils.hasText(service.getRegisterScript())) {
            return service.getRegisterScript();
        }
        if ("WINDOWS_SERVICE".equalsIgnoreCase(service.getServiceType())) {
            return null;
        }
        if (ServerCommandSupport.isWindows(host.getOsType())) {
            String winSwPath = resolveWinSwPath(service);
            return StringUtils.hasText(winSwPath) ? "& " + ServerCommandSupport.powershellQuote(winSwPath) + " install" : null;
        }
        String serviceName = service.getServiceName();
        String installPath = trim(service.getInstallPath());
        String startScript = resolveStartScript(service, host);
        if (!StringUtils.hasText(serviceName) || !StringUtils.hasText(installPath) || !StringUtils.hasText(startScript)) {
            return null;
        }
        return "cat > /etc/systemd/system/" + serviceName + ".service <<'EOF'\n"
                + "[Unit]\nDescription=" + serviceName + "\nAfter=network.target\n\n"
                + "[Service]\nType=simple\nWorkingDirectory=" + installPath + "\nExecStart=" + startScript + "\nRestart=always\n\n"
                + "[Install]\nWantedBy=multi-user.target\nEOF\n"
                + "systemctl daemon-reload && systemctl enable --now " + serviceName;
    }

    private String resolveUnregisterScript(ServerService service, ServerHost host) {
        if (StringUtils.hasText(service.getUnregisterScript())) {
            return service.getUnregisterScript();
        }
        if ("WINDOWS_SERVICE".equalsIgnoreCase(service.getServiceType())) {
            return null;
        }
        if (ServerCommandSupport.isWindows(host.getOsType())) {
            String winSwPath = resolveWinSwPath(service);
            return StringUtils.hasText(winSwPath) ? "& " + ServerCommandSupport.powershellQuote(winSwPath) + " uninstall" : null;
        }
        return StringUtils.hasText(service.getServiceName())
                ? "systemctl disable --now " + service.getServiceName() + " && rm -f /etc/systemd/system/" + service.getServiceName() + ".service"
                : null;
    }

    private String resolveStartScript(ServerService service, ServerHost host) {
        if (StringUtils.hasText(service.getStartScript())) {
            return service.getStartScript();
        }
        if ("WINDOWS_SERVICE".equalsIgnoreCase(service.getServiceType())) {
            return "Start-Service -Name " + ServerCommandSupport.powershellQuote(service.getServiceName());
        }
        if (ServerCommandSupport.isWindows(host.getOsType())) {
            String winSwPath = resolveWinSwPath(service);
            return StringUtils.hasText(winSwPath) ? "& " + ServerCommandSupport.powershellQuote(winSwPath) + " start" : null;
        }
        return StringUtils.hasText(service.getServiceName()) ? "systemctl start " + service.getServiceName() : null;
    }

    private String resolveStopScript(ServerService service, ServerHost host) {
        if (StringUtils.hasText(service.getStopScript())) {
            return service.getStopScript();
        }
        if ("WINDOWS_SERVICE".equalsIgnoreCase(service.getServiceType())) {
            return "Stop-Service -Name " + ServerCommandSupport.powershellQuote(service.getServiceName()) + " -Force";
        }
        if (ServerCommandSupport.isWindows(host.getOsType())) {
            String winSwPath = resolveWinSwPath(service);
            return StringUtils.hasText(winSwPath) ? "& " + ServerCommandSupport.powershellQuote(winSwPath) + " stop" : null;
        }
        return StringUtils.hasText(service.getServiceName()) ? "systemctl stop " + service.getServiceName() : null;
    }

    private String resolveRestartScript(ServerService service, ServerHost host) {
        if (StringUtils.hasText(service.getRestartScript())) {
            return service.getRestartScript();
        }
        if ("WINDOWS_SERVICE".equalsIgnoreCase(service.getServiceType())) {
            return "Restart-Service -Name " + ServerCommandSupport.powershellQuote(service.getServiceName()) + " -Force";
        }
        if (ServerCommandSupport.isWindows(host.getOsType())) {
            String winSwPath = resolveWinSwPath(service);
            return StringUtils.hasText(winSwPath) ? "& " + ServerCommandSupport.powershellQuote(winSwPath) + " restart" : null;
        }
        return StringUtils.hasText(service.getServiceName()) ? "systemctl restart " + service.getServiceName() : null;
    }

    private String resolveStatusScript(ServerService service, ServerHost host) {
        if (StringUtils.hasText(service.getStatusScript())) {
            return service.getStatusScript();
        }
        if ("WINDOWS_SERVICE".equalsIgnoreCase(service.getServiceType())) {
            return "Get-Service -Name " + ServerCommandSupport.powershellQuote(service.getServiceName()) + " | Select-Object -ExpandProperty Status";
        }
        if (ServerCommandSupport.isWindows(host.getOsType())) {
            String winSwPath = resolveWinSwPath(service);
            return StringUtils.hasText(winSwPath) ? "& " + ServerCommandSupport.powershellQuote(winSwPath) + " status" : null;
        }
        return StringUtils.hasText(service.getServiceName()) ? "systemctl is-active " + service.getServiceName() : null;
    }

    private String resolveWinSwPath(ServerService service) {
        String installPath = trim(service.getInstallPath());
        if (!StringUtils.hasText(installPath)) {
            return null;
        }
        return installPath + "\\winsw.exe";
    }

    private String joinOutputs(String first, String second) {
        String left = trim(first);
        String right = trim(second);
        if (!StringUtils.hasText(left)) {
            return right;
        }
        if (!StringUtils.hasText(right)) {
            return left;
        }
        return left + System.lineSeparator() + right;
    }

    private ServerService getManagedService(Integer installationId) {
        if (installationId == null) {
            return null;
        }
        return getOne(Wrappers.<ServerService>lambdaQuery()
                .eq(ServerService::getSoftInstallationId, installationId)
                .last("limit 1"));
    }

    private void hydrate(ServerService service) {
        if (service == null || service.getServerId() == null) {
            return;
        }
        ServerHost host = serverHostMapper.selectById(service.getServerId());
        if (host == null) {
            return;
        }
        service.setServerName(host.getServerName());
        service.setHost(host.getHost());
        operationLogService.fillLatest(service);
    }

    private void publishService(ServerService service) {
        if (service == null || service.getServerServiceId() == null) {
            return;
        }
        serverRealtimePublisher.publish(
                ServerSocketEvents.MODULE,
                ServerSocketEvents.SERVER_SERVICE,
                service.getServerServiceId(),
                service);
    }

    private void normalize(ServerService service, ServerHost host) {
        if (host == null) {
            throw new IllegalStateException("服务器不存在");
        }
        service.setServerId(host.getServerId());
        service.setServiceName(trim(service.getServiceName()));
        if (!StringUtils.hasText(service.getServiceName())) {
            throw new IllegalStateException("服务名称不能为空");
        }
        service.setServiceType(trim(service.getServiceType()));
        service.setSoftPackageId(normalizeOptionalId(service.getSoftPackageId()));
        service.setSoftPackageVersionId(normalizeOptionalId(service.getSoftPackageVersionId()));
        service.setSoftInstallationId(normalizeOptionalId(service.getSoftInstallationId()));
        service.setInstallPath(trim(service.getInstallPath()));
        service.setConfigPathsJson(trim(service.getConfigPathsJson()));
        service.setLogPathsJson(trim(service.getLogPathsJson()));
        service.setConfigTemplate(trim(service.getConfigTemplate()));
        service.setInitScript(trim(service.getInitScript()));
        service.setInstallScript(trim(service.getInstallScript()));
        service.setUninstallScript(trim(service.getUninstallScript()));
        service.setDetectScript(trim(service.getDetectScript()));
        service.setRegisterScript(trim(service.getRegisterScript()));
        service.setUnregisterScript(trim(service.getUnregisterScript()));
        service.setStartScript(trim(service.getStartScript()));
        service.setStopScript(trim(service.getStopScript()));
        service.setRestartScript(trim(service.getRestartScript()));
        service.setStatusScript(trim(service.getStatusScript()));
        service.setDescription(trim(service.getDescription()));
        service.setMetadataJson(trim(service.getMetadataJson()));
        if (service.getEnabled() == null) {
            service.setEnabled(Boolean.TRUE);
        }
        if (!StringUtils.hasText(service.getRuntimeStatus())) {
            service.setRuntimeStatus("UNKNOWN");
        }
        service.setServiceCode(resolveServiceCode(service, host));
        validateDuplicateService(service);
    }

    private void normalizeDetectedService(ServerService service) {
        ServerHost host = requireHost(service.getServerId());
        normalize(service, host);
        auditExecutor.run(() -> updateById(service));
    }

    private void validateDuplicateService(ServerService service) {
        long count = count(Wrappers.<ServerService>lambdaQuery()
                .eq(ServerService::getServerId, service.getServerId())
                .eq(ServerService::getServiceName, service.getServiceName())
                .ne(service.getServerServiceId() != null, ServerService::getServerServiceId, service.getServerServiceId()));
        if (count > 0) {
            throw new IllegalStateException("当前服务器已存在相同服务名称: " + service.getServiceName());
        }
    }

    private String resolveServiceCode(ServerService service, ServerHost host) {
        String serverCode = StringUtils.hasText(host.getServerCode())
                ? host.getServerCode().trim().toLowerCase(Locale.ROOT)
                : DigestUtils.md5Hex((trim(host.getHost()) + trim(host.getUsername())).toLowerCase(Locale.ROOT));
        return DigestUtils.md5Hex(serverCode + ":" + service.getServiceName().trim().toLowerCase(Locale.ROOT));
    }

    private ServerHost resolveHost(ServerService service) {
        if (service == null || service.getServerId() == null) {
            throw new IllegalStateException("服务未绑定服务器");
        }
        return requireHost(service.getServerId());
    }

    private ServerHost resolveHost(ServerServiceUpsertRequest request) {
        if (request.getServerId() != null) {
            return requireHost(request.getServerId());
        }
        ServerHost host = serverHostMapper.selectOne(Wrappers.<ServerHost>lambdaQuery()
                .eq(StringUtils.hasText(request.getServerType()), ServerHost::getServerType, trim(request.getServerType()))
                .eq(StringUtils.hasText(request.getHost()), ServerHost::getHost, trim(request.getHost()))
                .eq(StringUtils.hasText(request.getUsername()), ServerHost::getUsername, trim(request.getUsername()))
                .last("limit 1"));
        if (host == null) {
            throw new IllegalStateException("未找到可绑定的服务器主机");
        }
        return host;
    }

    private ServerHost requireHost(Integer serverId) {
        ServerHost host = serverHostService.getHost(serverId);
        if (host == null) {
            throw new IllegalStateException("服务器不存在: " + serverId);
        }
        return host;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Integer normalizeOptionalId(Integer value) {
        return value != null && value > 0 ? value : null;
    }
}
