package com.chua.starter.panel.software.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.panel.software.entity.SysProjectContainerSoftwareBinding;
import com.chua.starter.panel.software.entity.SysProjectContainerSoftwarePreset;
import com.chua.starter.panel.software.mapper.SysProjectContainerSoftwareBindingMapper;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareImportPresetRequest;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareBatchOperationRequest;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareBatchTarget;
import com.chua.starter.panel.software.pojo.ProjectContainerSoftwareOperationRequest;
import com.chua.starter.panel.software.model.ProjectContainerSoftwareOperationTicket;
import com.chua.starter.panel.service.DockerPanelService;
import lombok.extern.slf4j.Slf4j;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareBindingService;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareLogService;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwareOperationService;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwarePresetService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class SysProjectContainerSoftwareBindingServiceImpl
        extends ServiceImpl<SysProjectContainerSoftwareBindingMapper, SysProjectContainerSoftwareBinding>
        implements SysProjectContainerSoftwareBindingService {

    private final SysProjectContainerSoftwarePresetService presetService;
    private final SysProjectContainerSoftwareLogService logService;
    private final DockerPanelService dockerPanelService;
    private final org.springframework.context.ApplicationContext applicationContext;

    @org.springframework.beans.factory.annotation.Value("${docker.api.host:}")
    private String dockerApiHost;

    @org.springframework.beans.factory.annotation.Value("${docker.api.port:2375}")
    private int dockerApiPort;
    private static final long PROJECT_BINDING_CACHE_TTL_MILLIS = 10_000L;
    private final ConcurrentMap<Integer, BindingListCacheEntry> projectBindingListCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

    public SysProjectContainerSoftwareBindingServiceImpl(SysProjectContainerSoftwarePresetService presetService,
                                                          SysProjectContainerSoftwareLogService logService,
                                                          DockerPanelService dockerPanelService,
                                                          org.springframework.context.ApplicationContext applicationContext) {
        this.presetService = presetService;
        this.logService = logService;
        this.dockerPanelService = dockerPanelService;
        this.applicationContext = applicationContext;
    }

    @Override
    public IPage<SysProjectContainerSoftwareBinding> pageFor(Query<SysProjectContainerSoftwareBinding> query,
                                                             SysProjectContainerSoftwareBinding request,
                                                             Set<String> roles) {
        return baseMapper.selectPage(query.createPage(), query.mpjLambda()
                .selectAll(SysProjectContainerSoftwareBinding.class)
                .eq(null != request.getSysProjectId(),
                        SysProjectContainerSoftwareBinding::getSysProjectId, request.getSysProjectId())
                .like(StringUtils.isNotBlank(request.getSoftwareName()),
                        SysProjectContainerSoftwareBinding::getSoftwareName, request.getSoftwareName())
                .like(StringUtils.isNotBlank(request.getSoftwareCode()),
                        SysProjectContainerSoftwareBinding::getSoftwareCode, request.getSoftwareCode())
                .eq(StringUtils.isNotBlank(request.getSoftwareCategory()),
                        SysProjectContainerSoftwareBinding::getSoftwareCategory, request.getSoftwareCategory())
                .eq(null != request.getBindingCustom(),
                        SysProjectContainerSoftwareBinding::getBindingCustom, request.getBindingCustom())
                .eq(null != request.getBindingStatus(),
                        SysProjectContainerSoftwareBinding::getBindingStatus, request.getBindingStatus())
                .eq(null != request.getBindingDelete(),
                        SysProjectContainerSoftwareBinding::getBindingDelete, request.getBindingDelete())
                .orderByAsc(SysProjectContainerSoftwareBinding::getBindingSort)
                .orderByAsc(SysProjectContainerSoftwareBinding::getSysProjectContainerSoftwareBindingId));
    }

    @Override
    public List<SysProjectContainerSoftwareBinding> listByProjectId(Integer sysProjectId) {
        long now = System.currentTimeMillis();
        BindingListCacheEntry cached = projectBindingListCache.get(sysProjectId);
        if (cached != null && cached.expiresAt() > now) {
            return new ArrayList<>(cached.bindings());
        }
        List<SysProjectContainerSoftwareBinding> bindings = lambdaQuery()
                .eq(SysProjectContainerSoftwareBinding::getSysProjectId, sysProjectId)
                .eq(SysProjectContainerSoftwareBinding::getBindingDelete, 0)
                .orderByAsc(SysProjectContainerSoftwareBinding::getBindingSort)
                .orderByAsc(SysProjectContainerSoftwareBinding::getSysProjectContainerSoftwareBindingId)
                .list();
        projectBindingListCache.put(
                sysProjectId,
                new BindingListCacheEntry(new ArrayList<>(bindings), now + PROJECT_BINDING_CACHE_TTL_MILLIS)
        );
        return bindings;
    }

    @Override
    public void evictProjectBindingCache(Integer sysProjectId) {
        if (sysProjectId != null) {
            projectBindingListCache.remove(sysProjectId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<SysProjectContainerSoftwareBinding> importFromPreset(ProjectContainerSoftwareImportPresetRequest request) {
        if (null == request.getSysProjectId()) {
            return ReturnResult.illegal("项目ID不能为空");
        }
        if (null == request.getSysProjectContainerSoftwarePresetId()) {
            return ReturnResult.illegal("预设软件ID不能为空");
        }
        SysProjectContainerSoftwarePreset preset = presetService.getById(request.getSysProjectContainerSoftwarePresetId());
        if (null == preset) {
            return ReturnResult.illegal("预设软件不存在");
        }

        SysProjectContainerSoftwareBinding binding = new SysProjectContainerSoftwareBinding();
        binding.setSysProjectId(request.getSysProjectId());
        binding.setSysProjectContainerSoftwarePresetId(preset.getSysProjectContainerSoftwarePresetId());
        binding.setSoftwareCode(preset.getSoftwareCode());
        binding.setSoftwareName(preset.getSoftwareName());
        binding.setSoftwareCategory(preset.getSoftwareCategory());
        binding.setSoftwareVersion(preset.getSoftwareVersion());
        binding.setSoftwareIcon(preset.getSoftwareIcon());
        binding.setSoftwareVendor(preset.getSoftwareVendor());
        binding.setOsType(preset.getOsType());
        binding.setSoftwareArch(preset.getSoftwareArch());
        binding.setInstallPath(preset.getInstallPath());
        binding.setExposePorts(preset.getExposePorts());
        binding.setInstallScript(preset.getInstallScript());
        binding.setStartScript(preset.getStartScript());
        binding.setStopScript(preset.getStopScript());
        binding.setRestartScript(preset.getRestartScript());
        binding.setStatusScript(preset.getStatusScript());
        binding.setUninstallScript(preset.getUninstallScript());
        binding.setServiceName(preset.getServiceName());
        binding.setHasPortMapping(preset.getHasPortMapping());
        binding.setSystemEnvVars(preset.getSystemEnvVars());
        binding.setDockerEnvVars(preset.getDockerEnvVars());
        binding.setBindingCustom(0);
        binding.setBindingDelete(0);
        binding.setBindingStatus(1);
        binding.setBindingSort(0);
        binding.setInstallStatus("PENDING");
        binding.setRuntimeStatus("UNKNOWN");
        String remark = StringUtils.defaultString(request.getBindingRemark(), preset.getPresetRemark());
        if ("DOCKERFILE".equals(preset.getPresetType())) {
            try {
                Map<String, Object> remarkMap = objectMapper.readValue(remark, Map.class);
                remarkMap.putIfAbsent("installMode", "DOCKER");
                remarkMap.putIfAbsent("dockerMode", "dockerfile");
                if (StringUtils.isNotBlank(preset.getInstallScript())) {
                    remarkMap.putIfAbsent("dockerfileContent", preset.getInstallScript());
                }
                remark = objectMapper.writeValueAsString(remarkMap);
            } catch (Exception ignored) {
            }
        } else {
            // 非 DOCKERFILE 预设：将 preset_remark 中的 dockerDefaults 提升到顶层
            try {
                Map<String, Object> remarkMap = objectMapper.readValue(remark, Map.class);
                Object defaults = remarkMap.get("dockerDefaults");
                if (defaults instanceof Map<?, ?> dd) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dockerDefaults = (Map<String, Object>) defaults;
                    remarkMap.putIfAbsent("installMode", "DOCKER");
                    // dockerDefaults.image -> dockerImage
                    if (dockerDefaults.get("image") instanceof String image) {
                        remarkMap.putIfAbsent("dockerImage", image);
                    }
                    // dockerDefaults.env -> dockerEnv
                    if (dockerDefaults.get("env") instanceof Map<?, ?>) {
                        remarkMap.putIfAbsent("dockerEnv", dockerDefaults.get("env"));
                    }
                    // dockerDefaults.volumeMappings -> dockerVolumeMappings
                    if (dockerDefaults.get("volumeMappings") instanceof List<?>) {
                        remarkMap.putIfAbsent("dockerVolumeMappings", dockerDefaults.get("volumeMappings"));
                    }
                    // dockerDefaults.dockerSecurityOpts -> dockerSecurityOpts (top level)
                    if (dockerDefaults.get("dockerSecurityOpts") instanceof List<?>) {
                        remarkMap.putIfAbsent("dockerSecurityOpts", dockerDefaults.get("dockerSecurityOpts"));
                    }
                    // dockerDefaults.command -> cmd (used by buildDockerRunCommand fallback)
                    if (dockerDefaults.get("command") instanceof String cmd) {
                        remarkMap.putIfAbsent("cmd", cmd);
                    }
                } else {
                    // 没有 dockerDefaults 的预设为 SYSTEM 模式
                    remarkMap.putIfAbsent("installMode", "SYSTEM");
                }
                remark = objectMapper.writeValueAsString(remarkMap);
            } catch (Exception e) {
                // presetRemark 是纯文本（如 "Linux Redis 预设"），包装为 JSON
                try {
                    Map<String, Object> remarkMap = new HashMap<>();
                    remarkMap.put("_originalRemark", remark);
                    remarkMap.put("installMode", "SYSTEM");
                    remark = objectMapper.writeValueAsString(remarkMap);
                } catch (Exception ignored) {
                }
            }
        }
        binding.setBindingRemark(remark);
        baseMapper.insert(binding);
        evictProjectBindingCache(request.getSysProjectId());
        return ReturnResult.guess(binding);
    }

    @Override
    public ReturnResult<Boolean> delete(Integer bindingId, String connectionId) {
        SysProjectContainerSoftwareBinding existing = getById(bindingId);
        if (existing == null) {
            return ReturnResult.illegal("项目容器软件关联不存在");
        }

        boolean success = baseMapper.deleteById(bindingId) > 0;
        if (!success) {
            return ReturnResult.fail("删除数据库记录失败");
        }
        evictProjectBindingCache(existing.getSysProjectId());

        // 数据删除成功后，异步清理 Docker 容器（不影响删除结果）
        if (org.springframework.util.StringUtils.hasText(connectionId)) {
            String containerName = resolveContainerName(existing);
            if (org.springframework.util.StringUtils.hasText(containerName)) {
                try {
                    dockerPanelService.removeContainer(connectionId, containerName, true);
                } catch (Exception ignored) {
                    // 容器清理失败不阻塞删除
                }
            }
        }

        return ReturnResult.ok(true);
    }

    @Override
    public ReturnResult<Boolean> update(Set<String> roles, SysProjectContainerSoftwareBinding binding) {
        SysProjectContainerSoftwareBinding existing = binding.getSysProjectId() == null
                ? getById(binding.getSysProjectContainerSoftwareBindingId())
                : null;
        boolean success = baseMapper.updateById(binding) > 0;
        if (success) {
            evictProjectBindingCache(binding.getSysProjectId() != null
                    ? binding.getSysProjectId()
                    : existing == null ? null : existing.getSysProjectId());
        }
        return ReturnResult.guess(success);
    }

    @Override
    public ReturnResult<SysProjectContainerSoftwareBinding> save(SysProjectContainerSoftwareBinding binding, Set<String> roles) {
        if (null == binding.getSysProjectId()) {
            return ReturnResult.illegal("项目ID不能为空");
        }
        binding.setBindingDelete(0);
        if (null == binding.getBindingStatus()) {
            binding.setBindingStatus(1);
        }
        if (null == binding.getBindingCustom()) {
            binding.setBindingCustom(1);
        }
        if (null == binding.getBindingSort()) {
            binding.setBindingSort(0);
        }
        if (StringUtils.isBlank(binding.getInstallStatus())) {
            binding.setInstallStatus("PENDING");
        }
        if (StringUtils.isBlank(binding.getRuntimeStatus())) {
            binding.setRuntimeStatus("UNKNOWN");
        }
        baseMapper.insert(binding);
        evictProjectBindingCache(binding.getSysProjectId());
        return ReturnResult.guess(binding);
    }

    @Override
    public ReturnResult<Map<String, Object>> checkRequirements(Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("portAvailable", true);
        result.put("conflictPorts", new ArrayList<>());
        result.put("alreadyInstalled", false);
        result.put("serverOs", "linux");
        result.put("serviceNameAvailable", true);
        result.put("containerNameAvailable", true);
        result.put("daemonAvailable", true);
        result.put("imageAvailable", true);
        result.put("conflictVolumes", List.of());

        String exposePorts = String.valueOf(request.getOrDefault("exposePorts", ""));
        Integer sysProjectId = request.get("sysProjectId") instanceof Number n ? n.intValue() : null;
        Integer excludeBindingId = request.get("excludeBindingId") instanceof Number n ? n.intValue() : null;
        String connectionId = request.get("connectionId") instanceof String s && !s.isBlank() ? s : null;
        String serviceName = String.valueOf(request.getOrDefault("serviceName", ""));

        // ─── Docker 实时检查（需要 connectionId）──────────────────
        List<Map<String, Object>> dockerContainers = null;
        if (connectionId != null) {
            try {
                dockerPanelService.ping(connectionId);
                result.put("daemonAvailable", true);
            } catch (Exception e) {
                result.put("daemonAvailable", false);
                result.put("imageAvailable", false);
                result.put("daemonMessage", "Docker 守护进程不可达: " + e.getMessage());
            }

            if (Boolean.TRUE.equals(result.get("daemonAvailable"))) {
                try {
                    dockerContainers = dockerPanelService.listContainers(connectionId, true);
                } catch (Exception e) {
                    // 获取容器列表失败，仅做 DB 级别检查
                    dockerContainers = null;
                }
            }
        }

        // ─── 容器名冲突检查（实时 Docker）──────────────────────
        if (dockerContainers != null && org.springframework.util.StringUtils.hasText(serviceName)) {
            boolean realNameConflict = dockerContainers.stream().anyMatch(c -> {
                List<?> names = c.get("names") instanceof List<?> l ? l : List.of();
                return names.stream().anyMatch(n -> {
                    String name = String.valueOf(n);
                    // Docker 容器名带 / 前缀，如 /nginx
                    return serviceName.equals(name) || serviceName.equals(name.startsWith("/") ? name.substring(1) : name);
                });
            });
            if (realNameConflict) {
                result.put("containerNameAvailable", false);
                result.put("serviceNameAvailable", false);
            }

            // 端口冲突检查（实时 Docker）
            if (org.springframework.util.StringUtils.hasText(exposePorts)) {
                List<String> requestedPorts = List.of(exposePorts.split(",")).stream()
                        .map(String::trim).filter(p -> !p.isEmpty()).toList();
                // 只检查 host port（如 80:80 中的 80）
                List<String> requestedHostPorts = requestedPorts.stream()
                        .map(p -> p.contains(":") ? p.split(":")[0].trim() : p.trim())
                        .filter(p -> !p.isEmpty()).toList();

                List<String> dockerConflicts = new ArrayList<>();
                for (Map<String, Object> c : dockerContainers) {
                    String state = String.valueOf(c.getOrDefault("state", ""));
                    if ("exited".equalsIgnoreCase(state) || "dead".equalsIgnoreCase(state)) {
                        continue; // 跳过已停止的容器
                    }
                    List<?> ports = c.get("ports") instanceof List<?> l ? l : List.of();
                    for (Object portObj : ports) {
                        if (portObj instanceof Map<?, ?> pm) {
                            Object publicPort = pm.get("publicPort");
                            if (publicPort instanceof Number pn && pn.intValue() > 0) {
                                String hostPort = String.valueOf(pn.intValue());
                                if (requestedHostPorts.contains(hostPort)) {
                                    dockerConflicts.add(hostPort);
                                }
                            }
                        }
                    }
                }
                if (!dockerConflicts.isEmpty()) {
                    result.put("portAvailable", false);
                    result.put("conflictPorts", dockerConflicts);
                }
            }
        }

        // ─── 服务器端口实际占用探测（TCP 直连检查）────────────────
        // 检查请求的端口在远程服务器上是否真实被占用（不限于 Docker 容器）
        String targetHost = null;
        if (org.springframework.util.StringUtils.hasText(dockerApiHost)) {
            targetHost = dockerApiHost;
        }
        if (targetHost != null && org.springframework.util.StringUtils.hasText(exposePorts)) {
            List<String> requestedPorts = List.of(exposePorts.split(",")).stream()
                    .map(String::trim).filter(p -> !p.isEmpty()).toList();
            List<String> requestedHostPorts = requestedPorts.stream()
                    .map(p -> p.contains(":") ? p.split(":")[0].trim() : p.trim())
                    .filter(p -> !p.isEmpty()).toList();

            @SuppressWarnings("unchecked")
            List<String> existingConflicts = (List<String>) result.getOrDefault("conflictPorts", List.of());
            for (String hostPort : requestedHostPorts) {
                if (existingConflicts.contains(hostPort)) continue;
                int port;
                try {
                    port = Integer.parseInt(hostPort);
                } catch (NumberFormatException e) {
                    continue;
                }
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(targetHost, port), 2000);
                    // 连接成功 → 端口被占用
                    existingConflicts.add(hostPort);
                    result.put("portAvailable", false);
                    result.put("conflictPorts", existingConflicts);
                } catch (Exception ignored) {
                    // 连接失败 → 端口可用（或目标不可达，忽略）
                }
            }
        }

        // ─── DB 级别检查（补充或无 connectionId 时兜底）─────────
        if (sysProjectId != null) {
            List<SysProjectContainerSoftwareBinding> existing = listByProjectId(sysProjectId);

            // 端口冲突检查（DB）
            if (org.springframework.util.StringUtils.hasText(exposePorts)) {
                List<String> requestedPorts = List.of(exposePorts.split(","));
                // 提取宿主机端口（支持 "3306:3306" → "3306" 和 "3306" → "3306"）
                List<String> requestedHostPorts = requestedPorts.stream()
                        .map(String::trim).filter(p -> !p.isEmpty())
                        .map(p -> p.contains(":") ? p.split(":")[0].trim() : p.trim())
                        .filter(p -> !p.isEmpty()).toList();
                List<String> conflicts = new ArrayList<>();
                @SuppressWarnings("unchecked")
                List<String> existingConflicts = (List<String>) result.getOrDefault("conflictPorts", List.of());
                conflicts.addAll(existingConflicts);
                for (SysProjectContainerSoftwareBinding b : existing) {
                    if (excludeBindingId != null && excludeBindingId.equals(b.getSysProjectContainerSoftwareBindingId())) {
                        continue;
                    }
                    if (org.springframework.util.StringUtils.hasText(b.getExposePorts())) {
                        // 提取已绑定记录的宿主机端口
                        List<String> existingHostPorts = List.of(b.getExposePorts().split(",")).stream()
                                .map(String::trim).filter(p -> !p.isEmpty())
                                .map(p -> p.contains(":") ? p.split(":")[0].trim() : p.trim())
                                .filter(p -> !p.isEmpty()).toList();
                        for (String hostPort : requestedHostPorts) {
                            if (existingHostPorts.contains(hostPort) && !conflicts.contains(hostPort)) {
                                conflicts.add(hostPort);
                            }
                        }
                    }
                }
                if (!conflicts.isEmpty()) {
                    result.put("portAvailable", false);
                    result.put("conflictPorts", conflicts);
                }
            }

            // 服务名/容器名冲突检查（DB）
            if (org.springframework.util.StringUtils.hasText(serviceName)) {
                boolean nameConflict = existing.stream()
                        .filter(b -> excludeBindingId == null || !excludeBindingId.equals(b.getSysProjectContainerSoftwareBindingId()))
                        .anyMatch(b ->
                                org.springframework.util.StringUtils.hasText(b.getBindingRemark())
                                        && serviceName.equals(extractServiceName(b.getBindingRemark())));
                if (nameConflict) {
                    result.put("serviceNameAvailable", false);
                    result.put("containerNameAvailable", false);
                }
            }
        }

        return ReturnResult.ok(result);
    }

    @Override
    public ReturnResult<Map<String, Object>> checkDocker(Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        String connectionId = request.get("connectionId") instanceof String s && !s.isBlank() ? s : null;

        if (connectionId != null) {
            return checkDockerWithConnection(connectionId, result);
        }

        // 支持自定义 host/port（前端手动输入）
        String customHost = request.get("host") instanceof String s && !s.isBlank() ? s : null;
        if (customHost != null) {
            int customPort = 2375;
            if (request.get("port") instanceof Number n) {
                customPort = n.intValue();
            } else if (request.get("port") instanceof String ps) {
                try { customPort = Integer.parseInt(ps); } catch (NumberFormatException ignored) {}
            }
            return checkDockerWithHostPort(result, customHost, customPort);
        }

        // 无 connectionId 时，尝试使用全局 docker.api.host/port 配置
        if (org.springframework.util.StringUtils.hasText(dockerApiHost)) {
            return checkDockerWithGlobalConfig(result);
        }

        // 也没有全局配置时返回不可用状态
        result.put("available", false);
        result.put("version", "");
        result.put("message", "请先建立 Docker API 连接");
        return ReturnResult.ok(result);
    }

    private ReturnResult<Map<String, Object>> checkDockerWithConnection(String connectionId, Map<String, Object> result) {
        try {
            String pong = dockerPanelService.ping(connectionId);
            result.put("available", "pong".equals(pong));
            result.put("message", "Docker API 连接正常");

            try {
                Map<String, Object> versionInfo = dockerPanelService.version(connectionId);
                result.put("version", versionInfo.getOrDefault("version", "Docker API"));
                result.put("apiVersion", versionInfo.getOrDefault("apiVersion", ""));
            } catch (Exception ignored) {
                result.put("version", "Docker API");
            }
            return ReturnResult.ok(result);
        } catch (Exception e) {
            result.put("available", false);
            result.put("version", "");
            result.put("message", "Docker 连接失败: " + e.getMessage());
            return ReturnResult.ok(result);
        }
    }

    private ReturnResult<Map<String, Object>> checkDockerWithGlobalConfig(Map<String, Object> result) {
        String tempConnectionId = null;
        try {
            tempConnectionId = dockerPanelService.open(dockerApiHost, dockerApiPort, "tcp");
            String pong = dockerPanelService.ping(tempConnectionId);
            result.put("available", "pong".equals(pong));
            result.put("message", "Docker API 连接正常");
            try {
                Map<String, Object> versionInfo = dockerPanelService.version(tempConnectionId);
                result.put("version", versionInfo.getOrDefault("version", "Docker API"));
                result.put("apiVersion", versionInfo.getOrDefault("apiVersion", ""));
            } catch (Exception ignored) {
                result.put("version", "Docker API");
            }
            return ReturnResult.ok(result);
        } catch (Exception e) {
            result.put("available", false);
            result.put("version", "");
            result.put("message", "Docker 连接失败: " + e.getMessage());
            return ReturnResult.ok(result);
        } finally {
            if (tempConnectionId != null) {
                try { dockerPanelService.close(tempConnectionId); } catch (Exception ignored) {}
            }
        }
    }

    private ReturnResult<Map<String, Object>> checkDockerWithHostPort(Map<String, Object> result, String host, int port) {
        String tempConnectionId = null;
        try {
            tempConnectionId = dockerPanelService.open(host, port, "tcp");
            String pong = dockerPanelService.ping(tempConnectionId);
            result.put("available", "pong".equals(pong));
            result.put("message", "Docker API 连接正常");
            try {
                Map<String, Object> versionInfo = dockerPanelService.version(tempConnectionId);
                result.put("version", versionInfo.getOrDefault("version", "Docker API"));
                result.put("apiVersion", versionInfo.getOrDefault("apiVersion", ""));
            } catch (Exception ignored) {
                result.put("version", "Docker API");
            }
            return ReturnResult.ok(result);
        } catch (Exception e) {
            result.put("available", false);
            result.put("version", "");
            result.put("message", "Docker 连接失败: " + e.getMessage());
            return ReturnResult.ok(result);
        } finally {
            if (tempConnectionId != null) {
                try { dockerPanelService.close(tempConnectionId); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> reconfigure(Integer bindingId, Map<String, Object> configValues) {
        if (bindingId == null) {
            return ReturnResult.illegal("绑定ID不能为空");
        }
        SysProjectContainerSoftwareBinding binding = getById(bindingId);
        if (binding == null) {
            return ReturnResult.illegal("项目容器软件关联不存在");
        }

        try {
            Map<String, Object> remark;
            if (org.springframework.util.StringUtils.hasText(binding.getBindingRemark())) {
                try {
                    remark = objectMapper.readValue(binding.getBindingRemark(), new TypeReference<Map<String, Object>>() {});
                } catch (Exception jsonEx) {
                    // bindingRemark 可能是纯文本（如 "Linux Redis 预设"），非 JSON 格式时重新初始化
                    remark = new HashMap<>();
                    remark.put("_originalRemark", binding.getBindingRemark());
                }
            } else {
                remark = new HashMap<>();
            }
            remark.putAll(configValues);
            binding.setBindingRemark(objectMapper.writeValueAsString(remark));
            baseMapper.updateById(binding);
            evictProjectBindingCache(binding.getSysProjectId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配置已更新");
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.fail("重配置异常: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<String>> fetchRuntimeLogs(Integer bindingId, Integer tailLines, String connectionId) {
        if (bindingId == null) {
            return ReturnResult.ok(Collections.emptyList());
        }
        SysProjectContainerSoftwareBinding binding = getById(bindingId);
        if (binding == null) {
            return ReturnResult.ok(Collections.emptyList());
        }

        // 使用 Docker 容器实际日志
        String containerName = resolveContainerName(binding);
        if (org.springframework.util.StringUtils.hasText(connectionId) && org.springframework.util.StringUtils.hasText(containerName)) {
            try {
                String logs = dockerPanelService.containerLogs(connectionId, containerName, tailLines != null ? tailLines : 100);
                if (logs != null) {
                    return ReturnResult.ok(List.of(logs.split("\n", -1)));
                }
            } catch (Exception e) {
                return ReturnResult.ok(List.of("获取日志失败: " + e.getMessage()));
            }
        }

        return ReturnResult.ok(Collections.emptyList());
    }

    @Override
    public ReturnResult<Map<String, Object>> fetchContainerInfo(Integer bindingId, String connectionId) {
        if (bindingId == null) {
            return ReturnResult.illegal("绑定ID不能为空");
        }
        SysProjectContainerSoftwareBinding binding = getById(bindingId);
        if (binding == null) {
            return ReturnResult.illegal("项目容器软件关联不存在");
        }

        String containerName = resolveContainerName(binding);
        Map<String, Object> result = new HashMap<>();

        // 使用 DockerPanelService 获取真实容器数据
        if (org.springframework.util.StringUtils.hasText(connectionId)) {
            try {
                Map<String, Object> inspect = dockerPanelService.inspectContainer(connectionId, containerName);
                result.put("containerName", containerName);
                result.put("image", inspect.getOrDefault("image", ""));
                result.put("status", inspect.getOrDefault("state", ""));
                result.put("createdAt", inspect.getOrDefault("created", ""));
                result.put("portBindings", inspect.getOrDefault("portBindings", null));
                result.put("mounts", inspect.getOrDefault("mounts", List.of()));
                result.put("env", inspect.getOrDefault("env", List.of()));
                result.put("stateDetail", inspect.getOrDefault("stateDetail", null));
                result.put("networks", inspect.getOrDefault("networks", null));
                result.put("raw", inspect);
                return ReturnResult.ok(result);
            } catch (Exception ignored) {
                // Fall through to fallback
            }
        }

        // Fallback: populate from binding data
        result.put("containerName", containerName);
        result.put("image", binding.getSoftwareCode() != null ? binding.getSoftwareCode() : "");
        result.put("status", binding.getRuntimeStatus() != null ? binding.getRuntimeStatus() : "UNKNOWN");
        result.put("createdAt", binding.getCreateTime() != null ? binding.getCreateTime().toString() : "");
        result.put("portBindings", null);
        result.put("mounts", List.of());
        result.put("env", List.of());
        result.put("raw", Map.of("source", "bindingRemark", "remark", binding.getBindingRemark()));
        return ReturnResult.ok(result);
    }

    @Override
    public ReturnResult<Map<String, Object>> dockerExec(Integer bindingId, String command, String connectionId) {
        if (bindingId == null) {
            return ReturnResult.illegal("绑定ID不能为空");
        }
        SysProjectContainerSoftwareBinding binding = getById(bindingId);
        if (binding == null) {
            return ReturnResult.illegal("项目容器软件关联不存在");
        }
        if (!org.springframework.util.StringUtils.hasText(command)) {
            return ReturnResult.ok(Map.of("output", "", "exitCode", 0, "success", true));
        }
        if (!org.springframework.util.StringUtils.hasText(connectionId)) {
            return ReturnResult.illegal("Docker 连接ID不能为空");
        }

        String containerName = resolveContainerName(binding);

        try {
            String output = dockerPanelService.execInContainer(connectionId, containerName,
                    new String[]{"sh", "-c", command});
            Map<String, Object> result = new HashMap<>();
            result.put("output", output != null ? output.trim() : "");
            result.put("exitCode", 0);
            result.put("success", true);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.ok(Map.of("output", "执行异常: " + e.getMessage(), "exitCode", -1, "success", false));
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> getContainerStats(Integer bindingId, String connectionId) {
        if (bindingId == null) {
            return ReturnResult.illegal("绑定ID不能为空");
        }
        SysProjectContainerSoftwareBinding binding = getById(bindingId);
        if (binding == null) {
            return ReturnResult.illegal("项目容器软件关联不存在");
        }
        if (!org.springframework.util.StringUtils.hasText(connectionId)) {
            return ReturnResult.ok(Map.of("available", false, "message", "Docker 未连接"));
        }

        String containerName = resolveContainerName(binding);

        try {
            Map<String, Object> stats = dockerPanelService.getContainerStats(connectionId, containerName);
            stats.putIfAbsent("containerName", containerName);
            stats.putIfAbsent("available", true);
            return ReturnResult.ok(stats);
        } catch (Exception e) {
            return ReturnResult.ok(Map.of("available", false, "message", "无法获取容器统计信息: " + e.getMessage()));
        }
    }

    /**
     * 从 binding 中解析容器名称
     */
    public String resolveContainerName(SysProjectContainerSoftwareBinding binding) {
        String name = extractServiceName(binding.getBindingRemark());
        if (StringUtils.isBlank(name)) {
            name = binding.getServiceName();
        }
        if (StringUtils.isBlank(name)) {
            name = binding.getSoftwareName();
        }
        return name;
    }

    private String extractServiceName(String bindingRemark) {
        if (!org.springframework.util.StringUtils.hasText(bindingRemark)) return "";
        try {
            Map<String, Object> remark = objectMapper.readValue(bindingRemark, Map.class);
            Object name = remark.get("serviceName");
            return name instanceof String s ? s : "";
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private record BindingListCacheEntry(List<SysProjectContainerSoftwareBinding> bindings, long expiresAt) {
    }

    // ─── 容器状态同步 ────────────────────────────────────────

    @Override
    public ReturnResult<SyncSummary> syncContainerStatus(String connectionId) {
        if (!org.springframework.util.StringUtils.hasText(connectionId)) {
            return ReturnResult.illegal("Docker 连接ID不能为空");
        }

        List<SysProjectContainerSoftwareBinding> bindings = lambdaQuery()
                .eq(SysProjectContainerSoftwareBinding::getBindingDelete, 0)
                .list();

        int updated = 0;
        int missing = 0;
        int discovered = 0;

        // 收集已有绑定的容器名，用于去重
        java.util.Set<String> existingContainerNames = new java.util.HashSet<>();

        // 第1步：同步已有绑定的状态
        for (SysProjectContainerSoftwareBinding binding : bindings) {
            if (!isDockerMode(binding)) continue;
            String containerName = resolveContainerName(binding);
            existingContainerNames.add(containerName);
            try {
                java.util.Map<String, Object> inspect = dockerPanelService.inspectContainer(connectionId, containerName);
                String actualState = inspect.getOrDefault("state", "").toString().toLowerCase();
                updateRuntimeStatus(binding, actualState);
            } catch (Exception e) {
                markContainerMissing(binding);
                missing++;
            }
            updated++;
        }

        // 第2步：发现 Docker 主机上未纳入管理的容器并自动创建绑定
        try {
            // 构建 image→preset 映射
            java.util.List<SysProjectContainerSoftwarePreset> allPresets = presetService.list();
            java.util.Map<String, SysProjectContainerSoftwarePreset> imagePresetMap = new java.util.LinkedHashMap<>();
            for (SysProjectContainerSoftwarePreset preset : allPresets) {
                String remark = preset.getPresetRemark();
                if (!org.springframework.util.StringUtils.hasText(remark)) continue;
                try {
                    java.util.Map<String, Object> remarkMap = objectMapper.readValue(remark,
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                    Object defaults = remarkMap.get("dockerDefaults");
                    if (defaults instanceof java.util.Map<?, ?> dd) {
                        Object image = ((java.util.Map<String, Object>) defaults).get("image");
                        if (image instanceof String img && !img.isBlank()) {
                            imagePresetMap.put(img, preset);
                            // 也支持 latest 标签匹配
                            if (!img.contains(":")) {
                                imagePresetMap.put(img + ":latest", preset);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // 列出 Docker 上所有容器
            java.util.List<java.util.Map<String, Object>> containers = dockerPanelService.listContainers(connectionId, true);
            for (java.util.Map<String, Object> container : containers) {
                String containerImage = container.get("image") instanceof String s ? s : "";
                if (containerImage.isBlank()) continue;

                // 提取容器名（去掉前导 /）
                String rawName = container.get("names") instanceof java.util.List<?> names && !names.isEmpty()
                        ? names.get(0).toString().replaceFirst("^/", "") : "";
                if (rawName.isBlank()) continue;
                if (existingContainerNames.contains(rawName)) continue;

                // 尝试匹配 preset
                SysProjectContainerSoftwarePreset matchedPreset = imagePresetMap.get(containerImage);
                if (matchedPreset == null) {
                    // 尝试匹配镜像名的前半部分（如 mysql:5.7 → mysql）
                    String baseImage = containerImage.contains(":") ? containerImage.substring(0, containerImage.indexOf(':')) : containerImage;
                    matchedPreset = imagePresetMap.values().stream()
                            .filter(p -> {
                                String remark = p.getPresetRemark();
                                if (!org.springframework.util.StringUtils.hasText(remark)) return false;
                                try {
                                    java.util.Map<String, Object> rm = objectMapper.readValue(remark,
                                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                                    Object dd = rm.get("dockerDefaults");
                                    if (dd instanceof java.util.Map<?, ?> ddm) {
                                        String img = ((java.util.Map<String, Object>) ddm).get("image") instanceof String s ? s : "";
                                        return img.startsWith(baseImage);
                                    }
                                } catch (Exception ignored) {}
                                return false;
                            })
                            .findFirst().orElse(null);
                }

                if (matchedPreset != null) {
                    // 自动创建绑定
                    SysProjectContainerSoftwareBinding newBinding = new SysProjectContainerSoftwareBinding();
                    newBinding.setSysProjectId(1);
                    newBinding.setSysProjectContainerSoftwarePresetId(matchedPreset.getSysProjectContainerSoftwarePresetId());
                    newBinding.setSoftwareCode(matchedPreset.getSoftwareCode());
                    newBinding.setSoftwareName(matchedPreset.getSoftwareName());
                    newBinding.setSoftwareCategory(matchedPreset.getSoftwareCategory());
                    newBinding.setSoftwareVersion(matchedPreset.getSoftwareVersion());
                    newBinding.setSoftwareIcon(matchedPreset.getSoftwareIcon());
                    newBinding.setSoftwareVendor(matchedPreset.getSoftwareVendor());
                    newBinding.setOsType(matchedPreset.getOsType());
                    newBinding.setSoftwareArch(matchedPreset.getSoftwareArch());
                    newBinding.setInstallPath(matchedPreset.getInstallPath());
                    newBinding.setExposePorts(matchedPreset.getExposePorts());
                    newBinding.setInstallScript(matchedPreset.getInstallScript());
                    newBinding.setStartScript(matchedPreset.getStartScript());
                    newBinding.setStopScript(matchedPreset.getStopScript());
                    newBinding.setRestartScript(matchedPreset.getRestartScript());
                    newBinding.setStatusScript(matchedPreset.getStatusScript());
                    newBinding.setUninstallScript(matchedPreset.getUninstallScript());
                    newBinding.setServiceName(matchedPreset.getServiceName());
                    newBinding.setHasPortMapping(matchedPreset.getHasPortMapping());
                    newBinding.setSystemEnvVars(matchedPreset.getSystemEnvVars());
                    newBinding.setDockerEnvVars(matchedPreset.getDockerEnvVars());
                    newBinding.setBindingCustom(1);
                    newBinding.setBindingDelete(0);
                    newBinding.setBindingStatus(1);
                    newBinding.setBindingSort(0);
                    newBinding.setInstallStatus("INSTALLED");
                    newBinding.setRuntimeStatus("UNKNOWN");

                    // 设置 bindingRemark（标记为自动发现）
                    java.util.Map<String, Object> remarkMap = new java.util.LinkedHashMap<>();
                    remarkMap.put("installMode", "DOCKER");
                    remarkMap.put("dockerImage", containerImage);
                    remarkMap.put("autoDiscovered", true);
                    remarkMap.put("containerName", rawName);
                    try {
                        newBinding.setBindingRemark(objectMapper.writeValueAsString(remarkMap));
                    } catch (Exception ignored) {
                        newBinding.setBindingRemark("{\"installMode\":\"DOCKER\",\"autoDiscovered\":true}");
                    }

                    baseMapper.insert(newBinding);
                    discovered++;
                } else {
                    // 未匹配到 preset 的容器，以原始镜像名创建通用绑定
                    SysProjectContainerSoftwareBinding fallbackBinding = new SysProjectContainerSoftwareBinding();
                    fallbackBinding.setSysProjectId(1);
                    fallbackBinding.setSysProjectContainerSoftwarePresetId(0);
                    fallbackBinding.setSoftwareCode("docker");
                    fallbackBinding.setSoftwareName(rawName);
                    fallbackBinding.setSoftwareVersion("");
                    fallbackBinding.setSoftwareIcon("mdi:docker");
                    fallbackBinding.setSoftwareVendor("");
                    fallbackBinding.setOsType("linux");
                    fallbackBinding.setSoftwareArch("amd64");
                    fallbackBinding.setInstallPath("");
                    fallbackBinding.setExposePorts("");
                    fallbackBinding.setServiceName(rawName);
                    fallbackBinding.setHasPortMapping(0);
                    fallbackBinding.setBindingCustom(1);
                    fallbackBinding.setBindingDelete(0);
                    fallbackBinding.setBindingStatus(1);
                    fallbackBinding.setBindingSort(0);
                    fallbackBinding.setInstallStatus("INSTALLED");
                    fallbackBinding.setRuntimeStatus("RUNNING");
                    java.util.Map<String, Object> remarkMap = new java.util.LinkedHashMap<>();
                    remarkMap.put("installMode", "DOCKER");
                    remarkMap.put("dockerImage", containerImage);
                    remarkMap.put("autoDiscovered", true);
                    remarkMap.put("containerName", rawName);
                    try {
                        fallbackBinding.setBindingRemark(objectMapper.writeValueAsString(remarkMap));
                    } catch (Exception ignored) {
                        fallbackBinding.setBindingRemark("{\"installMode\":\"DOCKER\",\"autoDiscovered\":true}");
                    }
                    baseMapper.insert(fallbackBinding);
                    discovered++;
                }
            }
        } catch (Exception e) {
            log.warn("自动发现容器失败: {}", e.getMessage());
        }

        return ReturnResult.ok(new SyncSummary(bindings.size() + discovered, updated, missing));
    }

    @Override
    public ReturnResult<Map<String, Object>> syncSingleContainerStatus(Integer bindingId, String connectionId) {
        if (bindingId == null) return ReturnResult.illegal("绑定ID不能为空");
        if (!org.springframework.util.StringUtils.hasText(connectionId)) {
            return ReturnResult.illegal("Docker 连接ID不能为空");
        }

        SysProjectContainerSoftwareBinding binding = getById(bindingId);
        if (binding == null) return ReturnResult.illegal("项目容器软件关联不存在");

        String containerName = resolveContainerName(binding);
        Map<String, Object> result = new HashMap<>();
        result.put("bindingId", bindingId);
        result.put("containerName", containerName);
        result.put("previousInstallStatus", binding.getInstallStatus());
        result.put("previousRuntimeStatus", binding.getRuntimeStatus());

        try {
            Map<String, Object> inspect = dockerPanelService.inspectContainer(connectionId, containerName);
            String actualState = inspect.getOrDefault("state", "").toString().toLowerCase();
            updateRuntimeStatus(binding, actualState);
            result.put("actualState", actualState);
            result.put("resolved", true);
            result.put("message", "容器状态已同步: " + actualState);
        } catch (Exception e) {
            markContainerMissing(binding);
            result.put("actualState", "NOT_FOUND");
            result.put("resolved", true);
            result.put("installStatus", "CONTAINER_MISSING");
            result.put("message", "容器不存在，已标记为 CONTAINER_MISSING");
        }

        result.put("currentInstallStatus", binding.getInstallStatus());
        result.put("currentRuntimeStatus", binding.getRuntimeStatus());
        return ReturnResult.ok(result);
    }

    @Override
    public ReturnResult<List<SyncSummary>> syncContainerStatusMulti(List<String> connectionIds) {
        if (connectionIds == null || connectionIds.isEmpty()) {
            return ReturnResult.illegal("连接ID列表不能为空");
        }
        List<SyncSummary> summaries = new java.util.ArrayList<>();
        for (String connId : connectionIds) {
            try {
                ReturnResult<SyncSummary> result = syncContainerStatus(connId);
                if (result.isSuccess() && result.getData() != null) {
                    summaries.add(result.getData());
                }
            } catch (Exception e) {
                summaries.add(new SyncSummary(0, 0, 0));
            }
        }
        return ReturnResult.ok(summaries);
    }

    @Override
    public ReturnResult<List<Map<String, Object>>> batchOperate(ProjectContainerSoftwareBatchOperationRequest request) {
        if (request == null || request.getTargets() == null || request.getTargets().isEmpty()) {
            return ReturnResult.illegal("目标服务器列表不能为空");
        }
        String action = request.getAction();
        if (!org.springframework.util.StringUtils.hasText(action)) {
            return ReturnResult.illegal("操作类型不能为空");
        }

        List<Map<String, Object>> results = new java.util.ArrayList<>();
        List<Throwable> errors = new java.util.ArrayList<>();

        for (ProjectContainerSoftwareBatchTarget target : request.getTargets()) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("connectionId", target.getConnectionId());
            item.put("serverAddress", target.getServerAddress());
            try {
                Integer bindingId = target.getSysProjectContainerSoftwareBindingId();

                // 如果未指定 bindingId 但有 presetId，先导入预设
                if (bindingId == null && request.getSysProjectContainerSoftwarePresetId() != null) {
                    ProjectContainerSoftwareImportPresetRequest importReq = new ProjectContainerSoftwareImportPresetRequest();
                    importReq.setSysProjectId(request.getSysProjectId());
                    importReq.setSysProjectContainerSoftwarePresetId(request.getSysProjectContainerSoftwarePresetId());
                    importReq.setServiceName(target.getServiceName());
                    importReq.setBindingRemark(target.getBindingRemark());
                    importReq.setExposePorts(target.getExposePorts());
                    ReturnResult<SysProjectContainerSoftwareBinding> importResult = importFromPreset(importReq);
                    if (importResult.isSuccess() && importResult.getData() != null) {
                        bindingId = importResult.getData().getSysProjectContainerSoftwareBindingId();
                        item.put("bindingId", bindingId);
                    } else {
                        item.put("success", false);
                        item.put("error", "导入预设失败: " + importResult.getMsg());
                        results.add(item);
                        continue;
                    }
                }

                if (bindingId == null) {
                    item.put("success", false);
                    item.put("error", "绑定ID为空，且无法导入预设");
                    results.add(item);
                    continue;
                }

                // 提交操作
                ProjectContainerSoftwareOperationRequest opReq = new ProjectContainerSoftwareOperationRequest();
                opReq.setSysProjectContainerSoftwareBindingId(bindingId);
                opReq.setAction(action);
                opReq.setConnectionId(target.getConnectionId());
                SysProjectContainerSoftwareOperationService opService = applicationContext.getBean(SysProjectContainerSoftwareOperationService.class);
                ReturnResult<ProjectContainerSoftwareOperationTicket> opResult = opService.submit(opReq);
                if (opResult.isSuccess()) {
                    item.put("success", true);
                    item.put("operationId", opResult.getData() != null ? opResult.getData().getOperationId() : null);
                } else {
                    item.put("success", false);
                    item.put("error", opResult.getMsg());
                }
            } catch (Exception e) {
                item.put("success", false);
                item.put("error", e.getMessage());
            }
            results.add(item);
        }

        return ReturnResult.ok(results);
    }

    /**
     * 判断是否为 Docker 安装模式
     */
    private boolean isDockerMode(SysProjectContainerSoftwareBinding binding) {
        if (!org.springframework.util.StringUtils.hasText(binding.getBindingRemark())) {
            return false;
        }
        try {
            Map<String, Object> remark = objectMapper.readValue(binding.getBindingRemark(), Map.class);
            Object mode = remark.get("installMode");
            return "DOCKER".equals(mode);
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * 根据实际容器状态更新 runtimeStatus
     */
    private void updateRuntimeStatus(SysProjectContainerSoftwareBinding binding, String actualState) {
        String newRuntime;
        if ("running".equals(actualState)) {
            newRuntime = "RUNNING";
        } else if ("exited".equals(actualState) || "dead".equals(actualState)) {
            newRuntime = "STOPPED";
        } else {
            newRuntime = "UNKNOWN";
        }

        if (!newRuntime.equals(binding.getRuntimeStatus())) {
            binding.setRuntimeStatus(newRuntime);
            baseMapper.updateById(binding);
            evictProjectBindingCache(binding.getSysProjectId());
        }
    }

    /**
     * 容器不存在时的处理：标记 installStatus=CONTAINER_MISSING, runtimeStatus=UNKNOWN
     */
    private void markContainerMissing(SysProjectContainerSoftwareBinding binding) {
        binding.setInstallStatus("CONTAINER_MISSING");
        binding.setRuntimeStatus("UNKNOWN");
        baseMapper.updateById(binding);
        evictProjectBindingCache(binding.getSysProjectId());
    }
}
