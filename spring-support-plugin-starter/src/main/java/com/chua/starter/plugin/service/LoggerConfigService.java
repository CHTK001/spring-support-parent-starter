package com.chua.starter.plugin.service;

import com.chua.starter.plugin.entity.PluginNodeLoggerConfig;
import com.chua.starter.plugin.store.PersistenceStore;
import com.chua.starter.plugin.store.QueryCondition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 日志配置服务
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoggerConfigService {

    private final PersistenceStore<PluginNodeLoggerConfig, Long> loggerConfigStore;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 日志配置缓存 (nodeUrl -> loggerName -> config)
     */
    private final Map<String, Map<String, PluginNodeLoggerConfig>> configCache = new ConcurrentHashMap<>();

    /**
     * 获取节点的所有日志器配置
     * 
     * @param nodeUrl 节点地址
     * @return 日志器配置列表
     */
    public List<PluginNodeLoggerConfig> getNodeLoggers(String nodeUrl) {
        try {
            // 先从actuator获取最新的日志器信息
            Map<String, Object> loggersInfo = fetchLoggersFromActuator(nodeUrl);

            // 更新本地配置
            updateLocalConfigs(nodeUrl, loggersInfo);

            // 返回配置列表
            QueryCondition condition = QueryCondition.empty().eq("pluginLoggerConfigNodeUrl", nodeUrl)
                    .eq("pluginLoggerConfigEnabled", true).orderByAsc("pluginLoggerConfigLoggerName");

            return loggerConfigStore.findByCondition(condition);
        } catch (Exception e) {
            log.error("Failed to get node loggers for: {}", nodeUrl, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取相同应用名称的所有节点
     * 
     * @param applicationName 应用名称
     * @return 节点列表
     */
    public List<String> getNodesByApplicationName(String applicationName) {
        try {
            QueryCondition condition = QueryCondition.empty().eq("pluginLoggerConfigApplicationName", applicationName)
                    .eq("pluginLoggerConfigEnabled", true);

            List<PluginLoggerConfig> configs = loggerConfigStore.findByCondition(condition);

            return configs.stream().map(PluginLoggerConfig::getPluginLoggerConfigNodeUrl).distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get nodes by application name: {}", applicationName, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取指定日志器的配置信息
     * 
     * @param nodeUrl    节点地址
     * @param loggerName 日志器名称
     * @return 日志器配置
     */
    public Optional<PluginLoggerConfig> getLoggerConfig(String nodeUrl, String loggerName) {
        try {
            QueryCondition condition = QueryCondition.empty().eq("pluginLoggerConfigNodeUrl", nodeUrl)
                    .eq("pluginLoggerConfigLoggerName", loggerName);

            List<PluginLoggerConfig> configs = loggerConfigStore.findByCondition(condition);
            return configs.isEmpty() ? Optional.empty() : Optional.of(configs.get(0));
        } catch (Exception e) {
            log.error("Failed to get logger config: {} - {}", nodeUrl, loggerName, e);
            return Optional.empty();
        }
    }

    /**
     * 设置日志器等级
     * 
     * @param nodeUrl    节点地址
     * @param loggerName 日志器名称
     * @param level      日志等级
     * @return 是否设置成功
     */
    @Transactional
    public boolean setLoggerLevel(String nodeUrl, String loggerName, PluginLoggerConfig.LogLevel level) {
        try {
            // 调用actuator设置日志等级
            boolean success = setLoggerLevelViaActuator(nodeUrl, loggerName, level);

            if (success) {
                // 更新本地配置
                updateLocalLoggerConfig(nodeUrl, loggerName, level);
                log.info("Successfully set logger level: {} - {} -> {}", nodeUrl, loggerName, level);
                return true;
            } else {
                log.error("Failed to set logger level via actuator: {} - {} -> {}", nodeUrl, loggerName, level);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to set logger level: {} - {} -> {}", nodeUrl, loggerName, level, e);
            return false;
        }
    }

    /**
     * 批量设置相同应用的所有节点日志等级
     * 
     * @param applicationName 应用名称
     * @param loggerName      日志器名称
     * @param level           日志等级
     * @return 设置结果
     */
    @Transactional
    public Map<String, Boolean> setLoggerLevelForAllNodes(String applicationName, String loggerName,
            PluginLoggerConfig.LogLevel level) {
        Map<String, Boolean> results = new HashMap<>();

        List<String> nodeUrls = getNodesByApplicationName(applicationName);

        for (String nodeUrl : nodeUrls) {
            boolean success = setLoggerLevel(nodeUrl, loggerName, level);
            results.put(nodeUrl, success);
        }

        return results;
    }

    /**
     * 获取日志器的详细信息
     * 
     * @param nodeUrl    节点地址
     * @param loggerName 日志器名称
     * @return 详细信息
     */
    public Map<String, Object> getLoggerDetails(String nodeUrl, String loggerName) {
        try {
            String actuatorUrl = buildActuatorUrl(nodeUrl, "/loggers/" + loggerName);

            ResponseEntity<Map> response = restTemplate.getForEntity(actuatorUrl, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> details = new HashMap<>(response.getBody());

                // 添加本地配置信息
                Optional<PluginLoggerConfig> localConfig = getLoggerConfig(nodeUrl, loggerName);
                if (localConfig.isPresent()) {
                    details.put("localConfig", localConfig.get());
                }

                return details;
            }
        } catch (Exception e) {
            log.error("Failed to get logger details: {} - {}", nodeUrl, loggerName, e);
        }

        return Collections.emptyMap();
    }

    /**
     * 刷新节点的日志器配置
     * 
     * @param nodeUrl 节点地址
     * @return 是否刷新成功
     */
    @Transactional
    public boolean refreshNodeLoggers(String nodeUrl) {
        try {
            Map<String, Object> loggersInfo = fetchLoggersFromActuator(nodeUrl);
            updateLocalConfigs(nodeUrl, loggersInfo);

            log.info("Successfully refreshed loggers for node: {}", nodeUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to refresh loggers for node: {}", nodeUrl, e);
            return false;
        }
    }

    /**
     * 获取所有应用名称
     * 
     * @return 应用名称列表
     */
    public List<String> getAllApplicationNames() {
        try {
            List<PluginLoggerConfig> allConfigs = loggerConfigStore.findAll();

            return allConfigs.stream().map(PluginLoggerConfig::getPluginLoggerConfigApplicationName)
                    .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get all application names", e);
            return Collections.emptyList();
        }
    }

    /**
     * 从actuator获取日志器信息
     * 
     * @param nodeUrl 节点地址
     * @return 日志器信息
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchLoggersFromActuator(String nodeUrl) {
        try {
            String actuatorUrl = buildActuatorUrl(nodeUrl, "/loggers");

            ResponseEntity<Map> response = restTemplate.getForEntity(actuatorUrl, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to fetch loggers from actuator: {}", nodeUrl, e);
        }

        return Collections.emptyMap();
    }

    /**
     * 通过actuator设置日志等级
     * 
     * @param nodeUrl    节点地址
     * @param loggerName 日志器名称
     * @param level      日志等级
     * @return 是否设置成功
     */
    private boolean setLoggerLevelViaActuator(String nodeUrl, String loggerName, PluginLoggerConfig.LogLevel level) {
        try {
            String actuatorUrl = buildActuatorUrl(nodeUrl, "/loggers/" + loggerName);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("configuredLevel", level.name());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Void> response = restTemplate.postForEntity(actuatorUrl, request, Void.class);

            return response.getStatusCode() == HttpStatus.NO_CONTENT || response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("Failed to set logger level via actuator: {} - {} -> {}", nodeUrl, loggerName, level, e);
            return false;
        }
    }

    /**
     * 更新本地配置
     * 
     * @param nodeUrl     节点地址
     * @param loggersInfo 日志器信息
     */
    @SuppressWarnings("unchecked")
    private void updateLocalConfigs(String nodeUrl, Map<String, Object> loggersInfo) {
        try {
            Map<String, Object> loggers = (Map<String, Object>) loggersInfo.get("loggers");
            if (loggers == null) {
                return;
            }

            for (Map.Entry<String, Object> entry : loggers.entrySet()) {
                String loggerName = entry.getKey();
                Map<String, Object> loggerInfo = (Map<String, Object>) entry.getValue();

                updateOrCreateLoggerConfig(nodeUrl, loggerName, loggerInfo);
            }
        } catch (Exception e) {
            log.error("Failed to update local configs for: {}", nodeUrl, e);
        }
    }

    /**
     * 更新或创建日志器配置
     * 
     * @param nodeUrl    节点地址
     * @param loggerName 日志器名称
     * @param loggerInfo 日志器信息
     */
    @SuppressWarnings("unchecked")
    private void updateOrCreateLoggerConfig(String nodeUrl, String loggerName, Map<String, Object> loggerInfo) {
        try {
            Optional<PluginLoggerConfig> existingConfig = getLoggerConfig(nodeUrl, loggerName);

            PluginLoggerConfig config;
            if (existingConfig.isPresent()) {
                config = existingConfig.get();
                config.onUpdate();
            } else {
                config = new PluginLoggerConfig(extractNodeName(nodeUrl), nodeUrl, loggerName);
                config.setPluginLoggerConfigApplicationName(extractApplicationName(nodeUrl));
            }

            // 更新日志等级信息
            String effectiveLevel = (String) loggerInfo.get("effectiveLevel");
            String configuredLevel = (String) loggerInfo.get("configuredLevel");

            if (effectiveLevel != null) {
                config.setPluginLoggerConfigCurrentLevel(PluginLoggerConfig.LogLevel.valueOf(effectiveLevel));
            }

            if (configuredLevel != null) {
                config.setPluginLoggerConfigConfiguredLevel(PluginLoggerConfig.LogLevel.valueOf(configuredLevel));
            }

            // 设置有效等级列表
            List<String> effectiveLevels = PluginLoggerConfig.getAllLogLevels().stream().map(Enum::name)
                    .collect(Collectors.toList());
            config.setPluginLoggerConfigEffectiveLevels(objectMapper.writeValueAsString(effectiveLevels));

            loggerConfigStore.save(config);
        } catch (Exception e) {
            log.error("Failed to update logger config: {} - {}", nodeUrl, loggerName, e);
        }
    }

    /**
     * 更新本地日志器配置
     * 
     * @param nodeUrl    节点地址
     * @param loggerName 日志器名称
     * @param level      日志等级
     */
    private void updateLocalLoggerConfig(String nodeUrl, String loggerName, PluginLoggerConfig.LogLevel level) {
        try {
            Optional<PluginLoggerConfig> configOpt = getLoggerConfig(nodeUrl, loggerName);

            if (configOpt.isPresent()) {
                PluginLoggerConfig config = configOpt.get();
                config.setPluginLoggerConfigConfiguredLevel(level);
                config.setPluginLoggerConfigCurrentLevel(level);
                config.onUpdate();

                loggerConfigStore.save(config);
            }
        } catch (Exception e) {
            log.error("Failed to update local logger config: {} - {}", nodeUrl, loggerName, e);
        }
    }

    /**
     * 构建actuator URL
     * 
     * @param nodeUrl  节点地址
     * @param endpoint 端点
     * @return actuator URL
     */
    private String buildActuatorUrl(String nodeUrl, String endpoint) {
        String baseUrl = nodeUrl.endsWith("/") ? nodeUrl.substring(0, nodeUrl.length() - 1) : nodeUrl;
        return baseUrl + "/actuator" + endpoint;
    }

    /**
     * 从URL提取节点名称
     * 
     * @param nodeUrl 节点地址
     * @return 节点名称
     */
    private String extractNodeName(String nodeUrl) {
        try {
            // 简单提取主机名和端口作为节点名称
            String[] parts = nodeUrl.replace("http://", "").replace("https://", "").split("/");
            return parts[0];
        } catch (Exception e) {
            return nodeUrl;
        }
    }

    /**
     * 从URL提取应用名称
     * 
     * @param nodeUrl 节点地址
     * @return 应用名称
     */
    private String extractApplicationName(String nodeUrl) {
        // 这里可以根据实际情况实现应用名称提取逻辑
        // 比如从URL路径、配置文件或其他方式获取
        return "default-app";
    }
}
