package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.entity.service.SysMessage;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.server.support.config.ServerManagementProperties;
import com.chua.starter.server.support.constants.ServerSocketEvents;
import com.chua.starter.server.support.entity.ServerAlertEvent;
import com.chua.starter.server.support.entity.ServerAlertSetting;
import com.chua.starter.server.support.mapper.ServerAlertEventMapper;
import com.chua.starter.server.support.mapper.ServerAlertSettingMapper;
import com.chua.starter.server.support.model.ServerAlertSettings;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.chua.starter.server.support.service.ServerAlertService;
import com.chua.starter.server.support.service.ServerRealtimePublisher;
import com.chua.starter.service.SysMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerAlertServiceImpl implements ServerAlertService {

    private static final String GLOBAL_ALERT_SETTING_KEY = "alert:global";
    private static final String HOST_ALERT_SETTING_PREFIX = "alert:host:";
    private static final String ALERT_SETTINGS_CACHE_KEY = "plugin:server:alert:settings";
    private static final String ALERT_STATE_CACHE_KEY = "plugin:server:alert:state";
    private static final String EMPTY_MARKER = "__NONE__";
    private static final String SYSTEM_AUDIT_USER_ID = "0";
    private static final String SYSTEM_AUDIT_USERNAME = "server-system";

    private final ServerAlertSettingMapper serverAlertSettingMapper;
    private final ServerAlertEventMapper serverAlertEventMapper;
    private final ServerManagementProperties properties;
    private final ServerRealtimePublisher serverRealtimePublisher;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;
    private final ObjectProvider<SysMessageService> sysMessageServiceProvider;
    private final ObjectMapper objectMapper;

    private final Map<String, String> localAlertStateCache = new ConcurrentHashMap<>();

    @Override
    public ServerAlertSettings getGlobalSettings() {
        return mergeWithDefaults(loadSettings(GLOBAL_ALERT_SETTING_KEY), false, null);
    }

    @Override
    public ServerAlertSettings saveGlobalSettings(ServerAlertSettings settings) {
        ServerAlertSettings normalized = normalize(settings, null, false);
        saveSettings(GLOBAL_ALERT_SETTING_KEY, normalized);
        evictSettingsCache(GLOBAL_ALERT_SETTING_KEY);
        return getGlobalSettings();
    }

    @Override
    public ServerAlertSettings getHostSettings(Integer serverId) {
        ServerAlertSettings global = getGlobalSettings();
        ServerAlertSettings host = loadSettings(hostSettingKey(serverId));
        if (host == null || Boolean.TRUE.equals(host.getInheritGlobal())) {
            ServerAlertSettings inherited = copy(global);
            inherited.setServerId(serverId);
            inherited.setInheritGlobal(true);
            return inherited;
        }
        return merge(global, host, serverId);
    }

    @Override
    public ServerAlertSettings saveHostSettings(Integer serverId, ServerAlertSettings settings) {
        if (settings != null && Boolean.TRUE.equals(settings.getInheritGlobal())) {
            deleteSettings(hostSettingKey(serverId));
            evictSettingsCache(hostSettingKey(serverId));
            return getHostSettings(serverId);
        }
        ServerAlertSettings normalized = normalize(settings, serverId, true);
        saveSettings(hostSettingKey(serverId), normalized);
        evictSettingsCache(hostSettingKey(serverId));
        return getHostSettings(serverId);
    }

    @Override
    public List<ServerAlertEvent> listAlerts(Integer serverId, Integer limit) {
        int size = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        return serverAlertEventMapper.selectList(Wrappers.<ServerAlertEvent>lambdaQuery()
                .eq(serverId != null, ServerAlertEvent::getServerId, serverId)
                .orderByDesc(ServerAlertEvent::getCreateTime, ServerAlertEvent::getServerAlertEventId)
                .last("limit " + size));
    }

    @Override
    public void processSnapshot(ServerMetricsSnapshot snapshot) {
        if (snapshot == null || snapshot.getServerId() == null || !Boolean.TRUE.equals(snapshot.getOnline())) {
            clearAllStates(snapshot == null ? null : snapshot.getServerId());
            return;
        }
        ServerAlertSettings settings = getHostSettings(snapshot.getServerId());
        if (!Boolean.TRUE.equals(settings.getEnabled())) {
            clearAllStates(snapshot.getServerId());
            return;
        }
        evaluate(snapshot, settings, "CPU", snapshot.getCpuUsage(), settings.getCpuWarningPercent(), settings.getCpuDangerPercent(), "%");
        evaluate(snapshot, settings, "MEMORY", snapshot.getMemoryUsage(), settings.getMemoryWarningPercent(), settings.getMemoryDangerPercent(), "%");
        evaluate(snapshot, settings, "DISK", snapshot.getDiskUsage(), settings.getDiskWarningPercent(), settings.getDiskDangerPercent(), "%");
        evaluate(
                snapshot,
                settings,
                "IO",
                toDouble(snapshot.getIoReadBytesPerSecond()) + toDouble(snapshot.getIoWriteBytesPerSecond()),
                settings.getIoWarningBytesPerSecond(),
                settings.getIoDangerBytesPerSecond(),
                "B/s");
        evaluate(snapshot, settings, "LATENCY", toDouble(snapshot.getLatencyMs()), toDouble(settings.getLatencyWarningMs()), toDouble(settings.getLatencyDangerMs()), "ms");
    }

    private void evaluate(
            ServerMetricsSnapshot snapshot,
            ServerAlertSettings settings,
            String metricType,
            Double metricValue,
            Double warningThreshold,
            Double dangerThreshold,
            String unit
    ) {
        String severity = resolveSeverity(metricValue, warningThreshold, dangerThreshold);
        String cacheKey = alertStateKey(snapshot.getServerId(), metricType);
        String previousSeverity = getHotState(cacheKey);
        if (!StringUtils.hasText(severity)) {
            clearHotState(cacheKey);
            return;
        }
        if (severity.equalsIgnoreCase(previousSeverity)) {
            return;
        }
        ServerAlertEvent event = new ServerAlertEvent();
        event.setServerId(snapshot.getServerId());
        event.setServerCode(snapshot.getServerCode());
        event.setMetricType(metricType);
        event.setSeverity(severity);
        event.setMetricValue(metricValue);
        event.setWarningThreshold(warningThreshold);
        event.setDangerThreshold(dangerThreshold);
        event.setAlertMessage(buildAlertMessage(metricType, severity, metricValue, unit));
        event.setSnapshotJson(buildSnapshot(snapshot, metricType, warningThreshold, dangerThreshold));
        runWithSystemAudit(() -> serverAlertEventMapper.insert(event));
        setHotState(cacheKey, severity);
        serverRealtimePublisher.publish(ServerSocketEvents.MODULE, ServerSocketEvents.SERVER_ALERT, event.getServerAlertEventId(), event);
        pushAlertMessageIfNecessary(snapshot, settings, event);
    }

    private String buildSnapshot(
            ServerMetricsSnapshot snapshot,
            String metricType,
            Double warningThreshold,
            Double dangerThreshold
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metricType", metricType);
        payload.put("serverId", snapshot.getServerId());
        payload.put("serverCode", snapshot.getServerCode());
        payload.put("collectTimestamp", snapshot.getCollectTimestamp());
        payload.put("latencyMs", snapshot.getLatencyMs());
        payload.put("cpuUsage", snapshot.getCpuUsage());
        payload.put("memoryUsage", snapshot.getMemoryUsage());
        payload.put("diskUsage", snapshot.getDiskUsage());
        payload.put("ioReadBytesPerSecond", snapshot.getIoReadBytesPerSecond());
        payload.put("ioWriteBytesPerSecond", snapshot.getIoWriteBytesPerSecond());
        payload.put("networkRxPacketsPerSecond", snapshot.getNetworkRxPacketsPerSecond());
        payload.put("networkTxPacketsPerSecond", snapshot.getNetworkTxPacketsPerSecond());
        payload.put("warningThreshold", warningThreshold);
        payload.put("dangerThreshold", dangerThreshold);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String buildAlertMessage(String metricType, String severity, Double metricValue, String unit) {
        return metricType + " 指标达到" + ("DANGER".equalsIgnoreCase(severity) ? "危险" : "预警")
                + "阈值，当前值 " + trimNumeric(metricValue) + unit;
    }

    private void pushAlertMessageIfNecessary(
            ServerMetricsSnapshot snapshot,
            ServerAlertSettings settings,
            ServerAlertEvent event
    ) {
        if (snapshot == null || settings == null || event == null || !Boolean.TRUE.equals(settings.getMessageEnabled())) {
            return;
        }
        SysMessageService sysMessageService = sysMessageServiceProvider.getIfAvailable();
        if (sysMessageService == null) {
            return;
        }
        try {
            SysMessage message = new SysMessage();
            message.setSysMessageTitle(buildMessageTitle(snapshot, event));
            message.setSysMessageContent(buildMessageContent(snapshot, event));
            message.setSysMessageType(SysMessage.Type.WARNING);
            message.setSysMessageLevel(resolveMessageLevel(event.getSeverity()));
            message.setSysMessageSenderId(0);
            message.setSysMessageSenderName("服务器预警");
            message.setSysMessageReceiverId(0);
            message.setSysMessageBizType("SERVER_ALERT");
            message.setSysMessageBizId(String.valueOf(event.getServerAlertEventId()));
            message.setSysMessageSendTime(LocalDateTime.now());
            message.setSysMessageRead(0);
            message.setSysMessageStatus(SysMessage.Status.SENT);
            message.setSysMessageUrl("/server/list");
            runWithSystemAudit(() -> sysMessageService.sendMessage(message));
        } catch (Exception ignored) {
        }
    }

    private void runWithSystemAudit(Runnable action) {
        AuthClientExecute authClientExecute = AuthClientExecute.getInstance();
        UserResult current = authClientExecute.getSafeUserResult();
        if (current != null) {
            action.run();
            return;
        }
        String previousUsername = AuthClientExecute.getUsername();
        UserResume previousUser = AuthClientExecute.getUserInfo(UserResume.class);
        UserResult systemUser = new UserResult();
        systemUser.setUserId(SYSTEM_AUDIT_USER_ID);
        systemUser.setUsername(SYSTEM_AUDIT_USERNAME);
        AuthClientExecute.setUsername(SYSTEM_AUDIT_USERNAME);
        AuthClientExecute.setUserInfo(systemUser);
        try {
            action.run();
        } finally {
            if (previousUsername != null) {
                AuthClientExecute.setUsername(previousUsername);
            } else {
                AuthClientExecute.removeUsername();
            }
            if (previousUser != null) {
                AuthClientExecute.setUserInfo(previousUser);
            } else {
                AuthClientExecute.removeUserInfo();
            }
        }
    }

    private String buildMessageTitle(ServerMetricsSnapshot snapshot, ServerAlertEvent event) {
        String severity = "DANGER".equalsIgnoreCase(event.getSeverity()) ? "危险" : "预警";
        String serverName = StringUtils.hasText(snapshot.getServerCode())
                ? snapshot.getServerCode()
                : "服务器#" + snapshot.getServerId();
        return "服务器" + severity + "通知 · " + serverName + " · " + event.getMetricType();
    }

    private String buildMessageContent(ServerMetricsSnapshot snapshot, ServerAlertEvent event) {
        StringBuilder builder = new StringBuilder();
        builder.append("服务器ID: ").append(snapshot.getServerId()).append('\n');
        if (StringUtils.hasText(snapshot.getServerCode())) {
            builder.append("服务器编码: ").append(snapshot.getServerCode()).append('\n');
        }
        builder.append("指标类型: ").append(event.getMetricType()).append('\n');
        builder.append("告警级别: ").append("DANGER".equalsIgnoreCase(event.getSeverity()) ? "危险" : "预警").append('\n');
        builder.append("当前值: ").append(trimNumeric(event.getMetricValue())).append('\n');
        builder.append("预警阈值: ").append(trimNumeric(event.getWarningThreshold())).append('\n');
        builder.append("危险阈值: ").append(trimNumeric(event.getDangerThreshold())).append('\n');
        builder.append("告警说明: ").append(event.getAlertMessage());
        return builder.toString();
    }

    private String resolveMessageLevel(String severity) {
        if ("DANGER".equalsIgnoreCase(severity)) {
            return SysMessage.Level.URGENT;
        }
        if ("WARNING".equalsIgnoreCase(severity)) {
            return SysMessage.Level.HIGH;
        }
        return SysMessage.Level.NORMAL;
    }

    private String resolveSeverity(Double value, Double warning, Double danger) {
        if (value == null) {
            return null;
        }
        if (danger != null && value >= danger) {
            return "DANGER";
        }
        if (warning != null && value >= warning) {
            return "WARNING";
        }
        return null;
    }

    private ServerAlertSettings loadSettings(String settingKey) {
        String cached = getCachedSettings(settingKey);
        if (EMPTY_MARKER.equals(cached)) {
            return null;
        }
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, ServerAlertSettings.class);
            } catch (Exception ignored) {
                evictSettingsCache(settingKey);
            }
        }
        ServerAlertSetting setting = selectSettingEntity(settingKey);
        if (setting == null) {
            cacheSettings(settingKey, EMPTY_MARKER);
            return null;
        }
        ServerAlertSettings model = toModel(setting);
        if (model == null) {
            cacheSettings(settingKey, EMPTY_MARKER);
            return null;
        }
        try {
            String value = objectMapper.writeValueAsString(model);
            cacheSettings(settingKey, value);
            return model;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveSettings(String settingKey, ServerAlertSettings settings) {
        try {
            String value = objectMapper.writeValueAsString(settings);
            ServerAlertSetting current = selectSettingEntity(settingKey);
            if (current == null) {
                current = new ServerAlertSetting();
            }
            applySettingEntity(current, settings, isGlobalSetting(settingKey));
            if (current.getServerAlertSettingId() == null) {
                serverAlertSettingMapper.insert(current);
            } else {
                serverAlertSettingMapper.updateById(current);
            }
            cacheSettings(settingKey, value);
        } catch (Exception e) {
            throw new IllegalStateException("保存服务器预警配置失败", e);
        }
    }

    private void deleteSettings(String settingKey) {
        if (isGlobalSetting(settingKey)) {
            serverAlertSettingMapper.delete(Wrappers.<ServerAlertSetting>lambdaQuery()
                    .isNull(ServerAlertSetting::getServerId));
            return;
        }
        Integer serverId = parseHostSettingServerId(settingKey);
        if (serverId == null) {
            return;
        }
        serverAlertSettingMapper.delete(Wrappers.<ServerAlertSetting>lambdaQuery()
                .eq(ServerAlertSetting::getServerId, serverId));
    }

    private ServerAlertSettings normalize(ServerAlertSettings settings, Integer serverId, boolean inheritAware) {
        ServerAlertSettings source = settings == null ? ServerAlertSettings.builder().build() : settings;
        ServerAlertSettings normalized = mergeWithDefaults(source, inheritAware, serverId);
        normalized.setServerId(serverId);
        normalized.setInheritGlobal(inheritAware && Boolean.TRUE.equals(source.getInheritGlobal()));
        return normalized;
    }

    private ServerAlertSettings mergeWithDefaults(ServerAlertSettings settings, boolean inheritAware, Integer serverId) {
        ServerManagementProperties.Metrics metrics = properties.getMetrics();
        ServerAlertSettings source = settings == null ? ServerAlertSettings.builder().build() : settings;
        return ServerAlertSettings.builder()
                .serverId(serverId)
                .inheritGlobal(inheritAware && Boolean.TRUE.equals(source.getInheritGlobal()))
                .enabled(source.getEnabled() != null ? source.getEnabled() : Boolean.TRUE)
                .messageEnabled(source.getMessageEnabled() != null
                        ? source.getMessageEnabled()
                        : sysMessageServiceProvider.getIfAvailable() != null)
                .cpuWarningPercent(firstNonNull(source.getCpuWarningPercent(), metrics.getCpuWarningPercent()))
                .cpuDangerPercent(firstNonNull(source.getCpuDangerPercent(), metrics.getCpuDangerPercent()))
                .memoryWarningPercent(firstNonNull(source.getMemoryWarningPercent(), metrics.getMemoryWarningPercent()))
                .memoryDangerPercent(firstNonNull(source.getMemoryDangerPercent(), metrics.getMemoryDangerPercent()))
                .diskWarningPercent(firstNonNull(source.getDiskWarningPercent(), metrics.getDiskWarningPercent()))
                .diskDangerPercent(firstNonNull(source.getDiskDangerPercent(), metrics.getDiskDangerPercent()))
                .ioWarningBytesPerSecond(firstNonNull(source.getIoWarningBytesPerSecond(), metrics.getIoWarningBytesPerSecond()))
                .ioDangerBytesPerSecond(firstNonNull(source.getIoDangerBytesPerSecond(), metrics.getIoDangerBytesPerSecond()))
                .latencyWarningMs(firstNonNull(source.getLatencyWarningMs(), metrics.getLatencyWarningMs()))
                .latencyDangerMs(firstNonNull(source.getLatencyDangerMs(), metrics.getLatencyDangerMs()))
                .build();
    }

    private ServerAlertSettings merge(ServerAlertSettings global, ServerAlertSettings host, Integer serverId) {
        ServerAlertSettings merged = copy(global);
        merged.setServerId(serverId);
        merged.setInheritGlobal(false);
        merged.setEnabled(firstNonNull(host.getEnabled(), global.getEnabled()));
        merged.setMessageEnabled(firstNonNull(host.getMessageEnabled(), global.getMessageEnabled()));
        merged.setCpuWarningPercent(firstNonNull(host.getCpuWarningPercent(), global.getCpuWarningPercent()));
        merged.setCpuDangerPercent(firstNonNull(host.getCpuDangerPercent(), global.getCpuDangerPercent()));
        merged.setMemoryWarningPercent(firstNonNull(host.getMemoryWarningPercent(), global.getMemoryWarningPercent()));
        merged.setMemoryDangerPercent(firstNonNull(host.getMemoryDangerPercent(), global.getMemoryDangerPercent()));
        merged.setDiskWarningPercent(firstNonNull(host.getDiskWarningPercent(), global.getDiskWarningPercent()));
        merged.setDiskDangerPercent(firstNonNull(host.getDiskDangerPercent(), global.getDiskDangerPercent()));
        merged.setIoWarningBytesPerSecond(firstNonNull(host.getIoWarningBytesPerSecond(), global.getIoWarningBytesPerSecond()));
        merged.setIoDangerBytesPerSecond(firstNonNull(host.getIoDangerBytesPerSecond(), global.getIoDangerBytesPerSecond()));
        merged.setLatencyWarningMs(firstNonNull(host.getLatencyWarningMs(), global.getLatencyWarningMs()));
        merged.setLatencyDangerMs(firstNonNull(host.getLatencyDangerMs(), global.getLatencyDangerMs()));
        return merged;
    }

    private ServerAlertSettings copy(ServerAlertSettings source) {
        return ServerAlertSettings.builder()
                .serverId(source == null ? null : source.getServerId())
                .inheritGlobal(source != null && Boolean.TRUE.equals(source.getInheritGlobal()))
                .enabled(source == null ? Boolean.TRUE : source.getEnabled())
                .messageEnabled(source != null && Boolean.TRUE.equals(source.getMessageEnabled()))
                .cpuWarningPercent(source == null ? null : source.getCpuWarningPercent())
                .cpuDangerPercent(source == null ? null : source.getCpuDangerPercent())
                .memoryWarningPercent(source == null ? null : source.getMemoryWarningPercent())
                .memoryDangerPercent(source == null ? null : source.getMemoryDangerPercent())
                .diskWarningPercent(source == null ? null : source.getDiskWarningPercent())
                .diskDangerPercent(source == null ? null : source.getDiskDangerPercent())
                .ioWarningBytesPerSecond(source == null ? null : source.getIoWarningBytesPerSecond())
                .ioDangerBytesPerSecond(source == null ? null : source.getIoDangerBytesPerSecond())
                .latencyWarningMs(source == null ? null : source.getLatencyWarningMs())
                .latencyDangerMs(source == null ? null : source.getLatencyDangerMs())
                .build();
    }

    private void clearAllStates(Integer serverId) {
        if (serverId == null) {
            return;
        }
        clearHotState(alertStateKey(serverId, "CPU"));
        clearHotState(alertStateKey(serverId, "MEMORY"));
        clearHotState(alertStateKey(serverId, "DISK"));
        clearHotState(alertStateKey(serverId, "IO"));
        clearHotState(alertStateKey(serverId, "LATENCY"));
    }

    private String alertStateKey(Integer serverId, String metricType) {
        return serverId + ":" + metricType;
    }

    private String hostSettingKey(Integer serverId) {
        return HOST_ALERT_SETTING_PREFIX + serverId;
    }

    private boolean isGlobalSetting(String settingKey) {
        return GLOBAL_ALERT_SETTING_KEY.equals(settingKey);
    }

    private Integer parseHostSettingServerId(String settingKey) {
        if (!StringUtils.hasText(settingKey) || !settingKey.startsWith(HOST_ALERT_SETTING_PREFIX)) {
            return null;
        }
        try {
            return Integer.parseInt(settingKey.substring(HOST_ALERT_SETTING_PREFIX.length()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private ServerAlertSetting selectSettingEntity(String settingKey) {
        if (isGlobalSetting(settingKey)) {
            return serverAlertSettingMapper.selectOne(Wrappers.<ServerAlertSetting>lambdaQuery()
                    .isNull(ServerAlertSetting::getServerId)
                    .orderByDesc(ServerAlertSetting::getServerAlertSettingId)
                    .last("limit 1"));
        }
        Integer serverId = parseHostSettingServerId(settingKey);
        if (serverId == null) {
            return null;
        }
        return serverAlertSettingMapper.selectOne(Wrappers.<ServerAlertSetting>lambdaQuery()
                .eq(ServerAlertSetting::getServerId, serverId)
                .orderByDesc(ServerAlertSetting::getServerAlertSettingId)
                .last("limit 1"));
    }

    private ServerAlertSettings toModel(ServerAlertSetting entity) {
        if (entity == null) {
            return null;
        }
        return ServerAlertSettings.builder()
                .serverId(entity.getServerId())
                .inheritGlobal(Boolean.TRUE.equals(entity.getInheritGlobal()))
                .enabled(entity.getEnabled())
                .messageEnabled(entity.getMessageEnabled())
                .cpuWarningPercent(entity.getCpuWarningPercent())
                .cpuDangerPercent(entity.getCpuDangerPercent())
                .memoryWarningPercent(entity.getMemoryWarningPercent())
                .memoryDangerPercent(entity.getMemoryDangerPercent())
                .diskWarningPercent(entity.getDiskWarningPercent())
                .diskDangerPercent(entity.getDiskDangerPercent())
                .ioWarningBytesPerSecond(entity.getIoWarningBytesPerSecond())
                .ioDangerBytesPerSecond(entity.getIoDangerBytesPerSecond())
                .latencyWarningMs(entity.getLatencyWarningMs())
                .latencyDangerMs(entity.getLatencyDangerMs())
                .build();
    }

    private void applySettingEntity(ServerAlertSetting entity, ServerAlertSettings settings, boolean global) {
        entity.setServerId(global ? null : settings.getServerId());
        entity.setInheritGlobal(global ? Boolean.FALSE : Boolean.TRUE.equals(settings.getInheritGlobal()));
        entity.setEnabled(settings.getEnabled());
        entity.setMessageEnabled(settings.getMessageEnabled());
        entity.setCpuWarningPercent(settings.getCpuWarningPercent());
        entity.setCpuDangerPercent(settings.getCpuDangerPercent());
        entity.setMemoryWarningPercent(settings.getMemoryWarningPercent());
        entity.setMemoryDangerPercent(settings.getMemoryDangerPercent());
        entity.setDiskWarningPercent(settings.getDiskWarningPercent());
        entity.setDiskDangerPercent(settings.getDiskDangerPercent());
        entity.setIoWarningBytesPerSecond(settings.getIoWarningBytesPerSecond());
        entity.setIoDangerBytesPerSecond(settings.getIoDangerBytesPerSecond());
        entity.setLatencyWarningMs(settings.getLatencyWarningMs());
        entity.setLatencyDangerMs(settings.getLatencyDangerMs());
    }

    private String getHotState(String key) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                Object cached = redisTemplate.opsForHash().get(ALERT_STATE_CACHE_KEY, key);
                if (cached != null) {
                    return String.valueOf(cached);
                }
            } catch (Exception ignored) {
            }
        }
        return localAlertStateCache.get(key);
    }

    private void setHotState(String key, String severity) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForHash().put(ALERT_STATE_CACHE_KEY, key, severity);
            } catch (Exception ignored) {
            }
        }
        localAlertStateCache.put(key, severity);
    }

    private void clearHotState(String key) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForHash().delete(ALERT_STATE_CACHE_KEY, key);
            } catch (Exception ignored) {
            }
        }
        localAlertStateCache.remove(key);
    }

    private String getCachedSettings(String key) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return null;
        }
        try {
            Object value = redisTemplate.opsForHash().get(ALERT_SETTINGS_CACHE_KEY, key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void cacheSettings(String key, String value) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().put(ALERT_SETTINGS_CACHE_KEY, key, value);
        } catch (Exception ignored) {
        }
    }

    private void evictSettingsCache(String key) {
        StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForHash().delete(ALERT_SETTINGS_CACHE_KEY, key);
        } catch (Exception ignored) {
        }
    }

    private Double toDouble(Number value) {
        return value == null ? null : value.doubleValue();
    }

    private String trimNumeric(Double value) {
        if (value == null) {
            return "-";
        }
        if (Math.floor(value) == value) {
            return String.valueOf(value.longValue());
        }
        return String.format("%.2f", value);
    }

    private <T> T firstNonNull(T left, T right) {
        return left != null ? left : right;
    }
}
