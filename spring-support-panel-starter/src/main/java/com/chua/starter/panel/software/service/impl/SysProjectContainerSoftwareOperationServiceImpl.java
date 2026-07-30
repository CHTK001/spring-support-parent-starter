package com.chua.starter.panel.software.service.impl;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.model.PanelConnectionHandle;
import com.chua.starter.panel.service.NginxPanelService;
import com.chua.starter.panel.service.PanelConnectionService;
import com.chua.starter.panel.software.entity.SysProjectContainerSoftwareBinding;
import com.chua.starter.panel.software.model.ProjectContainerSoftwareOperationTicket;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareOperationRequest;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareTopologyOperationRequest;
import com.chua.starter.panel.software.service.ProjectContainerSoftwareRealtimePublisher;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareBindingService;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareLogService;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareOperationService;
import com.chua.starter.panel.software.spi.ContainerOperationExecutor;
import com.chua.starter.panel.software.spi.ContainerOperationExecutors;
import com.chua.starter.panel.software.spi.impl.DockerContainerOperationExecutor;
import com.chua.starter.panel.software.spi.impl.SshContainerOperationExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SysProjectContainerSoftwareOperationServiceImpl implements SysProjectContainerSoftwareOperationService {

    private static final Logger log = LoggerFactory.getLogger(SysProjectContainerSoftwareOperationServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

    private final SysProjectContainerSoftwareBindingService bindingService;
    private final ProjectContainerSoftwareRealtimePublisher realtimePublisher;
    private final SysProjectContainerSoftwareLogService logService;
    private final PanelConnectionService panelConnectionService;
    private final NginxPanelService nginxPanelService;

    /**
     * Docker API 主机地址（TCP 模式）。配置后所有 docker 命令将通过 TCP 连接远程 Docker 守护进程，
     * 而不是通过 SSH。例如：172.16.0.40
     */
    @Value("${docker.api.host:}")
    private String dockerApiHost;

    /**
     * Docker API 端口，默认 2375
     */
    @Value("${docker.api.port:2375}")
    private int dockerApiPort;

    private final ServiceProvider<ContainerOperationExecutor> executorProvider =
            ServiceProvider.of(ContainerOperationExecutor.class);

    @Override
    public ReturnResult<ProjectContainerSoftwareOperationTicket> submit(ProjectContainerSoftwareOperationRequest request) {
        if (request == null || request.getSysProjectContainerSoftwareBindingId() == null) {
            return ReturnResult.illegal("项目容器软件关联ID不能为空");
        }
        String action = normalizeAction(request.getAction());
        if (!org.springframework.util.StringUtils.hasText(action)) {
            return ReturnResult.illegal("操作类型不能为空");
        }
        SysProjectContainerSoftwareBinding binding = bindingService.getById(request.getSysProjectContainerSoftwareBindingId());
        if (binding == null) {
            return ReturnResult.illegal("项目容器软件关联不存在");
        }
        String operationId = UUID.randomUUID().toString();
        ProjectContainerSoftwareOperationTicket ticket = ProjectContainerSoftwareOperationTicket.builder()
                .operationId(operationId)
                .sysProjectId(binding.getSysProjectId())
                .sysProjectContainerSoftwareBindingId(binding.getSysProjectContainerSoftwareBindingId())
                .action(action)
                .operationStatus("RUNNING")
                .progressPercent(5)
                .message("任务已提交")
                .build();
        String connectionId = request.getConnectionId();
        Thread.startVirtualThread(() -> execute(operationId, action, binding, connectionId));
        return ReturnResult.ok(ticket);
    }

    @Override
    public ReturnResult<ProjectContainerSoftwareOperationTicket> submitTopology(ProjectContainerSoftwareTopologyOperationRequest request) {
        return ReturnResult.illegal("拓扑操作暂不支持");
    }

    private void execute(String operationId, String action, SysProjectContainerSoftwareBinding binding, String connectionId) {
        try {
            publishProgress(operationId, binding, action, 10, "RUNNING", "准备执行: " + action);
            appendLog(binding, action, operationId, "[信息] 开始执行操作: " + action);

            String installMode = resolveInstallMode(binding);
            appendLog(binding, action, operationId, "[配置] 安装模式: " + installMode);

            if ("INSTALL".equals(action)) {
                executeInstall(operationId, binding, connectionId);
            } else if ("UNINSTALL".equals(action) || "PURGE".equals(action)) {
                if ("DOCKER".equals(installMode)) {
                    executeUninstall(operationId, binding, action, connectionId);
                } else {
                    executeSystemUninstall(operationId, binding, action, connectionId);
                }
            } else if ("START".equals(action)) {
                if ("DOCKER".equals(installMode)) {
                    executeContainerAction(operationId, binding, action, "docker start", connectionId);
                } else {
                    executeSystemScriptAction(operationId, binding, action, "start", connectionId);
                }
            } else if ("STOP".equals(action)) {
                if ("DOCKER".equals(installMode)) {
                    executeContainerAction(operationId, binding, action, "docker stop", connectionId);
                } else {
                    executeSystemScriptAction(operationId, binding, action, "stop", connectionId);
                }
            } else if ("RESTART".equals(action)) {
                if ("DOCKER".equals(installMode)) {
                    executeContainerAction(operationId, binding, action, "docker restart", connectionId);
                } else {
                    executeSystemScriptAction(operationId, binding, action, "restart", connectionId);
                }
            } else if ("STATUS".equals(action)) {
                if ("DOCKER".equals(installMode)) {
                    executeStatus(operationId, binding, connectionId);
                } else {
                    executeSystemStatus(operationId, binding, connectionId);
                }
            } else if ("ENABLE_AUTOSTART".equals(action)) {
                if (!"DOCKER".equals(installMode)) {
                    executeSystemAutostart(operationId, binding, true, connectionId);
                } else {
                    throw new RuntimeException("Docker 模式不支持自启动设置");
                }
            } else if ("DISABLE_AUTOSTART".equals(action)) {
                if (!"DOCKER".equals(installMode)) {
                    executeSystemAutostart(operationId, binding, false, connectionId);
                } else {
                    throw new RuntimeException("Docker 模式不支持自启动设置");
                }
            }

            bindingService.updateById(binding);
            bindingService.evictProjectBindingCache(binding.getSysProjectId());
            publishProgress(operationId, binding, action, 100, "SUCCESS", action + " 执行成功");
            appendLog(binding, action, operationId, "[成功] 操作完成: " + action);
        } catch (Exception e) {
            log.error("操作执行失败: operationId={}, action={}", operationId, action, e);
            publishProgress(operationId, binding, action, 100, "FAILED", "操作失败: " + e.getMessage());
            appendLog(binding, action, operationId, "[错误] " + e.getMessage());
        }
    }

    private void executeInstall(String operationId, SysProjectContainerSoftwareBinding binding, String connectionId) throws Exception {
        String remark = binding.getBindingRemark();
        if (!org.springframework.util.StringUtils.hasText(remark)) {
            throw new RuntimeException("缺少安装配置(bindingRemark)");
        }

        Map<String, Object> config = parseJson(remark);
        String installMode = String.valueOf(config.getOrDefault("installMode", "DOCKER"));
        appendLog(binding, "INSTALL", operationId, "[配置] 安装模式: " + installMode);

        if (!"DOCKER".equals(installMode)) {
            executeSystemInstall(operationId, binding, config, connectionId);
            return;
        }

        executeDockerInstall(operationId, binding, config, connectionId);
    }

    /**
     * Docker 模式安装：docker pull + docker run
     */
    private void executeDockerInstall(String operationId, SysProjectContainerSoftwareBinding binding,
                                       Map<String, Object> config, String connectionId) throws Exception {
        String image = getString(config, "dockerImage");
        String containerName = getString(config, "serviceName");
        String portStr = getString(config, "port");
        String dockerMode = getString(config, "dockerMode");
        String dockerfileContent = getString(config, "dockerfileContent");

        if (!org.springframework.util.StringUtils.hasText(containerName)) {
            containerName = binding.getSoftwareCode() != null ? binding.getSoftwareCode() : "app";
        }

        appendLog(binding, "INSTALL", operationId, "[配置] 容器名称: " + containerName);
        appendLog(binding, "INSTALL", operationId, "[配置] Docker 镜像: " + (image != null ? image : "无"));

        // 日志输出 privileged 配置
        if (Boolean.TRUE.equals(config.get("privileged"))) {
            appendLog(binding, "INSTALL", operationId, "[配置] 特权模式: --privileged 已启用");
        }

        // Dockerfile 模式
        if ("dockerfile".equalsIgnoreCase(dockerMode) && org.springframework.util.StringUtils.hasText(dockerfileContent)) {
            appendLog(binding, "INSTALL", operationId, "[配置] 构建方式: Dockerfile");
            String buildCmd = buildDockerfileCommand(containerName, dockerfileContent, config);
            appendLog(binding, "INSTALL", operationId, "[配置] 构建命令: " + buildCmd);
            publishProgress(operationId, binding, "INSTALL", 30, "RUNNING", "正在构建 Docker 镜像...");
            execCommand(operationId, binding, "INSTALL", buildCmd, connectionId);

            publishProgress(operationId, binding, "INSTALL", 60, "RUNNING", "正在启动容器...");
            String runCmd = buildDockerRunCommand(containerName, image, config);
            execCommand(operationId, binding, "INSTALL", runCmd, connectionId);
        } else {
            // 先拉取镜像
            if (org.springframework.util.StringUtils.hasText(image)) {
                publishProgress(operationId, binding, "INSTALL", 30, "RUNNING", "正在拉取镜像: " + image);
                appendLog(binding, "INSTALL", operationId, "[信息] 开始拉取镜像: " + image);
                execCommand(operationId, binding, "INSTALL", "docker pull " + ContainerOperationExecutors.shellEscape(image), connectionId);
                appendLog(binding, "INSTALL", operationId, "[成功] 镜像拉取完成");
            }

            // 停止并删除已存在的同名容器
            execCommand(operationId, binding, "INSTALL",
                    "docker rm -f " + ContainerOperationExecutors.shellEscape(containerName) + " 2>nul || docker rm -f " + ContainerOperationExecutors.shellEscape(containerName) + " 2>/dev/null || true",
                    connectionId);

            // 运行容器
            publishProgress(operationId, binding, "INSTALL", 60, "RUNNING", "正在启动容器...");
            appendLog(binding, "INSTALL", operationId, "[信息] 创建并启动容器: " + containerName);
            String runCmd = buildDockerRunCommand(containerName, image, config);
            appendLog(binding, "INSTALL", operationId, "[配置] Docker 运行命令: " + ContainerOperationExecutors.maskPasswords(runCmd));
            execCommand(operationId, binding, "INSTALL", runCmd, connectionId);
        }

        // 验证容器运行状态
        publishProgress(operationId, binding, "INSTALL", 90, "RUNNING", "正在验证容器状态...");
        String psOut = execCommand(operationId, binding, "INSTALL",
                "docker ps --filter name=" + ContainerOperationExecutors.shellEscape(containerName) + " --format '{{.Status}}'",
                connectionId);
        if (org.springframework.util.StringUtils.hasText(psOut)) {
            appendLog(binding, "INSTALL", operationId, "[成功] 容器状态: " + psOut.trim());
            binding.setRuntimeStatus("RUNNING");
        } else {
            appendLog(binding, "INSTALL", operationId, "[警告] 容器未运行，检查日志");
            binding.setRuntimeStatus("ERROR");
        }

        binding.setInstallStatus("SUCCESS");
        publishProgress(operationId, binding, "INSTALL", 95, "RUNNING", "安装完成");
    }

    private String buildDockerRunCommand(String containerName, String image, Map<String, Object> config) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("docker run -d --name ").append(ContainerOperationExecutors.shellEscape(containerName));

        // 安全选项 (--security-opt)
        @SuppressWarnings("unchecked")
        List<String> securityOpts = config.get("dockerSecurityOpts") instanceof List<?> sl
                ? sl.stream().filter(s -> s instanceof String).map(s -> (String) s).toList()
                : Collections.emptyList();
        for (String opt : securityOpts) {
            cmd.append(" --security-opt ").append(ContainerOperationExecutors.shellEscape(opt));
        }

        // 特权模式 (--privileged)
        if (Boolean.TRUE.equals(config.get("privileged"))) {
            cmd.append(" --privileged");
        }

        // 端口映射
        String portStr = getString(config, "port");
        if (org.springframework.util.StringUtils.hasText(portStr)) {
            for (String mapping : portStr.split(",")) {
                mapping = mapping.trim();
                if (!mapping.isEmpty()) {
                    cmd.append(" -p ").append(mapping);
                }
            }
        } else if (org.springframework.util.StringUtils.hasText(image)) {
            // 从镜像暴露的端口推断
        }

        // 环境变量
        @SuppressWarnings("unchecked")
        Map<String, Object> dockerEnv = config.get("dockerEnv") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Collections.emptyMap();
        for (Map.Entry<String, Object> e : dockerEnv.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                cmd.append(" -e ").append(ContainerOperationExecutors.shellEscape(e.getKey() + "=" + e.getValue()));
            }
        }

        // 卷映射
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> volumes = config.get("dockerVolumeMappings") instanceof List<?> l
                ? (List<Map<String, Object>>) l : Collections.emptyList();
        for (Map<String, Object> vol : volumes) {
            String hostPath = vol.get("hostPath") instanceof String s ? s : null;
            String containerPath = vol.get("containerPath") instanceof String s ? s : null;
            if (hostPath != null && containerPath != null) {
                String binding = hostPath + ":" + containerPath;
                if (Boolean.TRUE.equals(vol.get("readOnly"))) {
                    binding += ":ro";
                }
                cmd.append(" -v ").append(ContainerOperationExecutors.shellEscape(binding));
            }
        }

        // 镜像
        if (org.springframework.util.StringUtils.hasText(image)) {
            cmd.append(" ").append(ContainerOperationExecutors.shellEscape(image));
        }

        // 容器命令/参数（如 MinIO 的 server /data --console-address=:9001）
        String containerCmd = getString(config, "cmd");
        if (org.springframework.util.StringUtils.hasText(containerCmd)) {
            cmd.append(" ").append(containerCmd);
        }

        return cmd.toString();
    }

    private String buildDockerfileCommand(String containerName, String dockerfileContent, Map<String, Object> config) {
        // 在临时目录中创建 Dockerfile 并构建
        String imageTag = containerName.toLowerCase(Locale.ROOT) + ":local";
        // 使用临时目录方式
        StringBuilder cmd = new StringBuilder();
        if (ContainerOperationExecutors.isWindows()) {
            cmd.append("mkdir %TEMP%\\docker_build_").append(containerName).append(" && ");
            cmd.append("echo ").append(ContainerOperationExecutors.escapeBatch(dockerfileContent))
                    .append(" > %TEMP%\\docker_build_").append(containerName).append("\\Dockerfile && ");
            cmd.append("docker build -t ").append(imageTag)
                    .append(" %TEMP%\\docker_build_").append(containerName).append(" && ");
            cmd.append("rmdir /s /q %TEMP%\\docker_build_").append(containerName);
        } else {
            cmd.append("TMP_DIR=$(mktemp -d) && ");
            cmd.append("cat > \"$TMP_DIR/Dockerfile\" <<'DOCKERFILE_EOF'\n");
            cmd.append(dockerfileContent).append("\n");
            cmd.append("DOCKERFILE_EOF\n");
            cmd.append("docker build -t ").append(imageTag).append(" \"$TMP_DIR\" && ");
            cmd.append("rm -rf \"$TMP_DIR\"");
        }
        return cmd.toString();
    }

    private void executeContainerAction(String operationId, SysProjectContainerSoftwareBinding binding,
                                         String action, String dockerCmd, String connectionId) throws Exception {
        String containerName = resolveContainerName(binding);
        if (!org.springframework.util.StringUtils.hasText(containerName)) {
            throw new RuntimeException("无法确定容器名称");
        }
        appendLog(binding, action, operationId, "[信息] 执行: " + dockerCmd + " " + containerName);
        String result = execCommand(operationId, binding, action,
                dockerCmd + " " + ContainerOperationExecutors.shellEscape(containerName), connectionId);
        appendLog(binding, action, operationId, "[成功] 操作完成: " + result.trim());
        binding.setRuntimeStatus("START".equals(action) || "RESTART".equals(action) ? "RUNNING" : "STOPPED");
    }

    private void executeUninstall(String operationId, SysProjectContainerSoftwareBinding binding,
                                   String action, String connectionId) throws Exception {
        String containerName = resolveContainerName(binding);
        if (org.springframework.util.StringUtils.hasText(containerName)) {
            publishProgress(operationId, binding, action, 50, "RUNNING", "正在删除容器...");
            execCommand(operationId, binding, action,
                    "docker rm -f " + ContainerOperationExecutors.shellEscape(containerName), connectionId);
            appendLog(binding, action, operationId, "[成功] 容器已删除");
        }
        binding.setInstallStatus("UNINSTALLED");
        binding.setRuntimeStatus("STOPPED");
    }

    private void executeSystemInstall(String operationId, SysProjectContainerSoftwareBinding binding,
                                       Map<String, Object> config, String connectionId) throws Exception {
        String script = binding.getInstallScript();
        if (!org.springframework.util.StringUtils.hasText(script)) {
            throw new RuntimeException("SYSTEM 模式需要提供安装脚本");
        }

        // 端口映射（类似 Docker -p，SYSTEM 模式下记录端口配置，供 UI 展示/软件配置使用）
        String systemPorts = getString(config, "systemPorts");
        if (org.springframework.util.StringUtils.hasText(systemPorts)) {
            appendLog(binding, "INSTALL", operationId, "[配置] 端口映射: " + systemPorts);
        }

        // 环境变量注入（类似 Docker -e，SYSTEM 模式下通过 export 注入到执行环境）
        @SuppressWarnings("unchecked")
        Map<String, Object> systemEnv = config.get("systemEnv") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Collections.emptyMap();
        if (!systemEnv.isEmpty()) {
            appendLog(binding, "INSTALL", operationId, "[配置] 环境变量: " + systemEnv);
            StringBuilder envPrefix = new StringBuilder();
            for (Map.Entry<String, Object> e : systemEnv.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    envPrefix.append("export ")
                            .append(ContainerOperationExecutors.shellEscape(e.getKey()))
                            .append("=")
                            .append(ContainerOperationExecutors.shellEscape(String.valueOf(e.getValue())))
                            .append("\n");
                }
            }
            script = envPrefix + script;
        }

        appendLog(binding, "INSTALL", operationId, "[信息] 执行系统安装脚本");
        publishProgress(operationId, binding, "INSTALL", 50, "RUNNING", "执行安装脚本中...");
        execCommand(operationId, binding, "INSTALL", script, connectionId, "SYSTEM");
        binding.setInstallStatus("SUCCESS");
        binding.setRuntimeStatus("RUNNING");
    }

    // ─── SYSTEM 模式操作 ────────────────────────────────────

    /**
     * 从 bindingRemark JSON 中解析 installMode，默认 DOCKER
     */
    private String resolveInstallMode(SysProjectContainerSoftwareBinding binding) {
        String remark = binding.getBindingRemark();
        if (!org.springframework.util.StringUtils.hasText(remark)) {
            // remark 为空时：有 dockerImage 字段则视为 DOCKER，否则视为 SYSTEM
            return hasDockerImage(binding) ? "DOCKER" : "SYSTEM";
        }
        try {
            Map<String, Object> config = OBJECT_MAPPER.readValue(remark,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Object mode = config.get("installMode");
            if (mode instanceof String s) return s;
            // remark 是 JSON 但没有 installMode：通过是否有 dockerImage 判断
            return config.containsKey("dockerImage") ? "DOCKER" : "SYSTEM";
        } catch (Exception e) {
            // remark 非 JSON（如旧数据纯文本）：通过是否有 dockerImage 字段判断
            return hasDockerImage(binding) ? "DOCKER" : "SYSTEM";
        }
    }

    private boolean hasDockerImage(SysProjectContainerSoftwareBinding binding) {
        // 检查 bindingRemark JSON 中是否包含 dockerImage 字段
        String remark = binding.getBindingRemark();
        if (!org.springframework.util.StringUtils.hasText(remark)) return false;
        try {
            Map<String, Object> config = OBJECT_MAPPER.readValue(remark,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return config.containsKey("dockerImage") && config.get("dockerImage") instanceof String s && !s.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将动作名映射到绑定上的脚本字段
     */
    private String resolveScript(SysProjectContainerSoftwareBinding binding, String field) {
        return switch (field) {
            case "start" -> binding.getStartScript();
            case "stop" -> binding.getStopScript();
            case "restart" -> binding.getRestartScript();
            case "status" -> binding.getStatusScript();
            case "uninstall" -> binding.getUninstallScript();
            default -> null;
        };
    }

    /**
     * SYSTEM 模式：执行启动/停止/重启脚本
     */
    private void executeSystemScriptAction(String operationId, SysProjectContainerSoftwareBinding binding,
                                            String action, String scriptField, String connectionId) throws Exception {
        String script = resolveScript(binding, scriptField);
        if (!org.springframework.util.StringUtils.hasText(script)) {
            throw new RuntimeException("SYSTEM 模式 " + action + " 需要 " + scriptField + "Script");
        }
        appendLog(binding, action, operationId, "[信息] 执行: " + script);
        execCommand(operationId, binding, action, script, connectionId);
        binding.setRuntimeStatus("START".equals(action) || "RESTART".equals(action) ? "RUNNING" : "STOPPED");
    }

    /**
     * SYSTEM 模式：执行状态检查脚本
     */
    private void executeSystemStatus(String operationId, SysProjectContainerSoftwareBinding binding,
                                      String connectionId) throws Exception {
        String script = binding.getStatusScript();
        if (!org.springframework.util.StringUtils.hasText(script)) {
            binding.setRuntimeStatus("UNKNOWN");
            return;
        }
        String result = execCommand(operationId, binding, "STATUS", script, connectionId);
        if (org.springframework.util.StringUtils.hasText(result)) {
            // 逐行检查输出：跳过标题行、"inactive"、"dead"、"unknown" 等否定词
            boolean isRunning = false;
            for (String line : result.split("\\n")) {
                String trimmed = line.trim().toLowerCase(Locale.ROOT);
                if (trimmed.isEmpty() || trimmed.startsWith("●")) continue;
                if (trimmed.contains("inactive") || trimmed.contains("dead") || trimmed.contains("unknown")
                        || trimmed.contains("not running") || trimmed.contains("stopped")) {
                    continue;
                }
                if (trimmed.contains("active") || trimmed.contains("running")) {
                    isRunning = true;
                    break;
                }
            }
            binding.setRuntimeStatus(isRunning ? "RUNNING" : "STOPPED");
        } else {
            binding.setRuntimeStatus("STOPPED");
        }
        publishProgress(operationId, binding, "STATUS", 100, "SUCCESS", "状态检查完成");
    }

    /**
     * SYSTEM 模式：先停服务再卸载
     */
    private void executeSystemUninstall(String operationId, SysProjectContainerSoftwareBinding binding,
                                         String action, String connectionId) throws Exception {
        String stopScript = binding.getStopScript();
        if (org.springframework.util.StringUtils.hasText(stopScript)) {
            try {
                execCommand(operationId, binding, action, stopScript, connectionId);
            } catch (Exception ignored) {
                appendLog(binding, action, operationId, "[警告] 停止服务失败（可忽略）");
            }
        }
        String uninstallScript = binding.getUninstallScript();
        if (org.springframework.util.StringUtils.hasText(uninstallScript)) {
            execCommand(operationId, binding, action, uninstallScript, connectionId);
        }
        binding.setInstallStatus("UNINSTALLED");
        binding.setRuntimeStatus("STOPPED");
    }

    /**
     * SYSTEM 模式：设置开机自启动
     */
    private void executeSystemAutostart(String operationId, SysProjectContainerSoftwareBinding binding,
                                         boolean enable, String connectionId) throws Exception {
        String serviceName = binding.getServiceName();
        if (!org.springframework.util.StringUtils.hasText(serviceName)) {
            throw new RuntimeException("该软件未配置服务名称，不支持自启动");
        }
        String cmd = enable ? "systemctl enable " + serviceName : "systemctl disable " + serviceName;
        String label = enable ? "开启" : "关闭";
        appendLog(binding, enable ? "ENABLE_AUTOSTART" : "DISABLE_AUTOSTART", operationId, "[信息] " + label + "开机自启动: " + serviceName);
        publishProgress(operationId, binding, enable ? "ENABLE_AUTOSTART" : "DISABLE_AUTOSTART", 50, "RUNNING", label + "开机自启动中...");
        execCommand(operationId, binding, enable ? "ENABLE_AUTOSTART" : "DISABLE_AUTOSTART", cmd, connectionId);
        appendLog(binding, enable ? "ENABLE_AUTOSTART" : "DISABLE_AUTOSTART", operationId, "[成功] 已" + label + " " + serviceName + " 开机自启动");
    }

    private void executeStatus(String operationId, SysProjectContainerSoftwareBinding binding, String connectionId) throws Exception {
        String containerName = resolveContainerName(binding);
        if (!org.springframework.util.StringUtils.hasText(containerName)) {
            binding.setRuntimeStatus("UNKNOWN");
            return;
        }
        String psOut = execCommand(operationId, binding, "STATUS",
                "docker ps --filter name=" + ContainerOperationExecutors.shellEscape(containerName) + " --format '{{.Status}}'",
                connectionId);
        if (org.springframework.util.StringUtils.hasText(psOut) && !psOut.trim().isEmpty()) {
            binding.setRuntimeStatus("RUNNING");
            appendLog(binding, "STATUS", operationId, "[信息] 容器运行中: " + psOut.trim());
        } else {
            binding.setRuntimeStatus("STOPPED");
            appendLog(binding, "STATUS", operationId, "[信息] 容器未运行");
        }
        publishProgress(operationId, binding, "STATUS", 100, "SUCCESS", "状态检查完成");
    }

    /**
     * 执行本地或远程命令，返回输出内容
     * <p>优先顺序：</p>
     * <ol>
     *   <li>如果配置了 docker.api.host 且安装模式为 DOCKER，使用 Docker TCP API（docker -H tcp://host:port）</li>
     *   <li>否则如果提供了 connectionId，使用 SSH 远程执行</li>
     *   <li>否则本地执行（localhost docker）</li>
     * </ol>
     */
    private String execCommand(String operationId, SysProjectContainerSoftwareBinding binding,
                                String action, String command, String connectionId) throws Exception {
        return execCommand(operationId, binding, action, command, connectionId, resolveInstallMode(binding));
    }

    private String execCommand(String operationId, SysProjectContainerSoftwareBinding binding,
                                String action, String command, String connectionId, String installMode) throws Exception {
        appendLog(binding, action, operationId, "[信息] $ " + ContainerOperationExecutors.maskPasswords(command));
        var lineConsumer = (java.util.function.Consumer<String>) line ->
                appendLog(binding, action, operationId, "[信息] " + line);
        return resolveExecutor(connectionId, installMode).execute(command, lineConsumer);
    }

    /**
     * 根据连接信息和安装模式解析对应的执行器
     *
     * @param connectionId SSH 连接 ID
     * @param installMode 安装模式（DOCKER / SYSTEM）
     * @return 执行器实例
     */
    private ContainerOperationExecutor resolveExecutor(String connectionId, String installMode) {
        // SYSTEM 模式 + SSH 连接：使用 JSch 会话（NginxPanelService）执行，逐行流式输出
        if ("SYSTEM".equals(installMode) && StringUtils.hasText(connectionId)) {
            return (command, lineConsumer) ->
                    nginxPanelService.executeScript(connectionId, command, lineConsumer);
        }
        String mode = resolveMode(connectionId, installMode);
        ContainerOperationExecutor executor = executorProvider.getExtension(mode);
        if (executor == null) {
            executor = executorProvider.getExtension("local");
        }
        if (executor instanceof DockerContainerOperationExecutor de) {
            de.configure(dockerApiHost, dockerApiPort);
        } else if (executor instanceof SshContainerOperationExecutor sv) {
            // 仅当提供了 connectionId 时才尝试配置 SSH 连接详情
            if (StringUtils.hasText(connectionId)) {
                PanelConnectionHandle handle = panelConnectionService.get(connectionId);
                if (handle != null && handle.getDefinition() != null) {
                    var def = handle.getDefinition();
                    sv.configure(def.getHost(), def.getPort() != null ? def.getPort() : 22, def.getUsername());
                }
            }
        }
        return executor;
    }

    /**
     * 解析执行模式
     * <p>SYSTEM 模式不使用 Docker TCP API，始终走 SSH 或本地执行。</p>
     */
    private String resolveMode(String connectionId, String installMode) {
        if ("SYSTEM".equals(installMode)) {
            // SYSTEM 模式：有 connectionId 时尝试 SSH，否则本地执行
            if (StringUtils.hasText(connectionId)) return "ssh";
            return "local";
        }
        if (StringUtils.hasText(dockerApiHost)) return "docker";
        if (StringUtils.hasText(connectionId)) return "ssh";
        return "local";
    }

    // ─── 辅助方法 ──────────────────────────────────────────────

    private String resolveContainerName(SysProjectContainerSoftwareBinding binding) {
        String remark = binding.getBindingRemark();
        if (org.springframework.util.StringUtils.hasText(remark)) {
            try {
                Map<String, Object> config = OBJECT_MAPPER.readValue(remark, Map.class);
                String name = getString(config, "serviceName");
                if (org.springframework.util.StringUtils.hasText(name)) return name;
            } catch (Exception ignored) {
            }
        }
        return binding.getServiceName();
    }

    private void appendLog(SysProjectContainerSoftwareBinding binding, String action, String operationId, String line) {
        if (binding.getSysProjectContainerSoftwareBindingId() != null) {
            logService.appendLog(binding.getSysProjectContainerSoftwareBindingId(), action, operationId, line);
        }
        // 同时通过 Socket.IO 实时推送日志行
        try {
            Map<String, Object> payload = basePayload(operationId, binding, action);
            payload.put("line", line);
            payload.put("level", line.startsWith("[错误]") ? "error" :
                                 line.startsWith("[警告]") ? "warn" :
                                 line.startsWith("[成功]") ? "success" :
                                 line.startsWith("[配置]") ? "config" : "info");
            realtimePublisher.publish("CONTAINER_SOFTWARE_LOG", operationId, payload);
        } catch (Exception ignored) {
            // 推送失败不影响主流程
        }
    }

    private Map<String, Object> parseJson(String json) {
        if (!org.springframework.util.StringUtils.hasText(json)) return Collections.emptyMap();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }

    @Override
    public ReturnResult<Map<String, Object>> aiAnalyzeFailure(Map<String, Object> request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysis", "AI 分析功能需要配置 AI 服务。请检查以下可能原因：\n1. Docker 服务是否正常运行\n2. 镜像名称是否正确\n3. 端口是否被占用\n4. 磁盘空间是否充足");
        result.put("suggestedFix", "请检查 Docker 服务状态和安装日志。");
        return ReturnResult.ok(result);
    }

    private void publishProgress(String operationId,
                                 SysProjectContainerSoftwareBinding binding,
                                 String action,
                                 int progress,
                                 String status,
                                 String message) {
        Map<String, Object> payload = basePayload(operationId, binding, action);
        payload.put("progressPercent", progress);
        payload.put("status", status);
        payload.put("message", message);
        realtimePublisher.publish("CONTAINER_SOFTWARE_PROGRESS", operationId, payload);
    }

    private Map<String, Object> basePayload(String operationId,
                                            SysProjectContainerSoftwareBinding binding,
                                            String action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId);
        payload.put("action", action);
        payload.put("sysProjectId", binding.getSysProjectId());
        payload.put("sysProjectContainerSoftwareBindingId", binding.getSysProjectContainerSoftwareBindingId());
        payload.put("softwareName", binding.getSoftwareName());
        payload.put("softwareCode", binding.getSoftwareCode());
        payload.put("runtimeStatus", binding.getRuntimeStatus());
        payload.put("installStatus", binding.getInstallStatus());
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    private String normalizeAction(String action) {
        return String.valueOf(action == null ? "" : action).trim().toUpperCase(Locale.ROOT);
    }
}
