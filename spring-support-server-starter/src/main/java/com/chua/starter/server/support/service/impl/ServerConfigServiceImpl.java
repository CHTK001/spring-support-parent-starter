package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.MsServerConfig;
import com.chua.starter.server.support.event.ServerConfigChangedEvent;
import com.chua.starter.server.support.mapper.MsServerConfigMapper;
import com.chua.starter.server.support.service.ServerConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* 服务器系统配置服务实现
* @author CH
* @since 4.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerConfigServiceImpl implements ServerConfigService {

    /** JSON 序列化器 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 默认配置 */
    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        // guacd（远程控制网关——系统配置页可动态修改）
        DEFAULTS.put("guacd.host", "127.0.0.1");
        DEFAULTS.put("guacd.port", "4822");
 // guacd-客户端（浏览器侧 guacamole 客户端回连后端 WS 隧道的 主机:端口，空串=同域 位置.主机）
        DEFAULTS.put("guacd.client.host", "127.0.0.1:8082");
        // 连接缓存
        DEFAULTS.put("connection.sweep.interval", "60");
        DEFAULTS.put("connection.idle.timeout", "300");
    }

    /** 配置 映射器 */
    private final MsServerConfigMapper configMapper;

    /** 应用事件发布器 */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ReturnResult<List<MsServerConfig>> listAll() {
        return ReturnResult.ok(configMapper.selectList(
                new LambdaQueryWrapper<MsServerConfig>().orderByAsc(MsServerConfig::getConfigKey)));
    }

    @Override
    public ReturnResult<Map<String, Object>> getConfigMap() {
        Map<String, Object> merged = new HashMap<>(DEFAULTS);
        for (MsServerConfig cfg : configMapper.selectList(null)) {
            Object parsed = parseValue(cfg.getConfigValue());
            if (parsed != null) {
                merged.put(cfg.getConfigKey(), parsed);
            }
        }
        return ReturnResult.ok(merged);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 保存配置。
    * @param map 映射
    * @return 保存配置的结果
     */
    public ReturnResult<Void> saveConfig(Map<String, Object> map) {
        if (map == null) {
            return ReturnResult.error("配置不能为空");
        }
        Set<String> changedKeys = new HashSet<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = serializeValue(entry.getValue());
            MsServerConfig exists = configMapper.selectOne(
                    new LambdaQueryWrapper<MsServerConfig>().eq(MsServerConfig::getConfigKey, key));
            if (exists == null) {
                MsServerConfig cfg = new MsServerConfig();
                cfg.setConfigKey(key);
                cfg.setConfigValue(value);
                cfg.setConfigDesc(defaultDesc(key));
                configMapper.insert(cfg);
            } else {
                exists.setConfigValue(value);
                configMapper.updateById(exists);
            }
            changedKeys.add(key);
        }
        // 配置变更后发布事件，时间轮调度器据此动态重排周期任务
        if (!changedKeys.isEmpty()) {
            try {
                eventPublisher.publishEvent(new ServerConfigChangedEvent(changedKeys));
            } catch (Exception e) {
                log.warn("[config] 发布配置变更事件失败: {}", e.getMessage());
            }
        }
        return ReturnResult.success();
    }

    @Override
    public String getString(String key, String defValue) {
        MsServerConfig cfg = configMapper.selectOne(
                new LambdaQueryWrapper<MsServerConfig>().eq(MsServerConfig::getConfigKey, key));
        if (cfg == null || cfg.getConfigValue() == null) {
            return defValue;
        }
        Object parsed = parseValue(cfg.getConfigValue());
        return parsed == null ? defValue : String.valueOf(parsed);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        String v = getString(key, String.valueOf(defValue));
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    @Override
    public int getInt(String key, int defValue) {
        String v = getString(key, String.valueOf(defValue));
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    /**
    * 解析存储值（JSON 标量原样，字符串去引号）
    *
    * @param raw 原始存储值
    * @return 解析后的值
     */
    private Object parseValue(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        try {
            if (t.startsWith("\"") || t.startsWith("{") || t.startsWith("[")
                    || t.startsWith("true") || t.startsWith("false") || isNumeric(t)) {
                return MAPPER.readValue(t, new TypeReference<Object>() { });
            }
        } catch (Exception ignored) {
            // 非 JSON 走原文
        }
        return raw;
    }

    /**
    * 序列化值（字符串带引号，其余 JSON）
    *
    * @param value 值
    * @return 存储串
     */
    private String serializeValue(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
    * 判断纯数字
    * @param s s
    * @return 是否numeric的结果
     */
    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
    * 配置键默认描述
    * @param key 钥匙
    * @return 默认desc的结果
     */
    private String defaultDesc(String key) {
        Map<String, String> desc = new HashMap<>();
        desc.put("guacd.host", "guacd 网关地址（远控隧道）");
        desc.put("guacd.port", "guacd 网关端口（远控隧道）");
        desc.put("guacd.client.host", "guacd-client 网关地址（浏览器侧 WS 隧道 host:port，空串=同域）");
        desc.put("connection.sweep.interval", "连接健康检查间隔(秒)");
        desc.put("connection.idle.timeout", "连接空闲超时(秒)");
        return desc.getOrDefault(key, "系统配置");
    }
}
