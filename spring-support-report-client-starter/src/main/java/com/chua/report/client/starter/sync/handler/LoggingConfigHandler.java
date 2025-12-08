package com.chua.report.client.starter.sync.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.chua.common.support.annotations.Spi;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日志配置处理器
 * <p>
 * 监听日志配置 Topic，动态调整日志级别
 * 支持 Logback 日志框架
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/08
 */
@Slf4j
@Spi("loggingConfigHandler")
public class LoggingConfigHandler implements SyncMessageHandler {

    @Override
    public String getName() {
        return "loggingConfigHandler";
    }

    @Override
    public boolean supports(String topic) {
        return MonitorTopics.LOGGING_CONFIG.equals(topic);
    }

    @Override
    public Object handle(String topic, String sessionId, Map<String, Object> data) {
        String action = getString(data, "action");

        return switch (action) {
            case "setLevel" -> handleSetLevel(data);
            case "getLoggers" -> handleGetLoggers(data);
            case "getLevel" -> handleGetLevel(data);
            default -> Map.of("code", 400, "message", "Unknown action: " + action);
        };
    }

    /**
     * 设置日志级别
     * 
     * @param data 请求数据，包含 loggerName 和 level
     * @return 操作结果
     */
    private Object handleSetLevel(Map<String, Object> data) {
        String loggerName = getString(data, "loggerName");
        String levelStr = getString(data, "level");

        if (loggerName == null || loggerName.isEmpty()) {
            return Map.of("code", 400, "message", "loggerName is required");
        }
        if (levelStr == null || levelStr.isEmpty()) {
            return Map.of("code", 400, "message", "level is required");
        }

        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger logger = loggerContext.getLogger(loggerName);
            
            Level level = Level.toLevel(levelStr.toUpperCase(), null);
            if (level == null) {
                return Map.of("code", 400, "message", "Invalid level: " + levelStr);
            }

            Level oldLevel = logger.getLevel();
            logger.setLevel(level);

            log.info("[LoggingConfig] 日志级别已调整: {} {} -> {}", 
                    loggerName, 
                    oldLevel != null ? oldLevel.toString() : "inherit", 
                    level.toString());

            return Map.of(
                    "code", 200, 
                    "message", "SUCCESS",
                    "loggerName", loggerName,
                    "oldLevel", oldLevel != null ? oldLevel.toString() : "inherit",
                    "newLevel", level.toString()
            );
        } catch (Exception e) {
            log.error("[LoggingConfig] 设置日志级别失败: {}", e.getMessage(), e);
            return Map.of("code", 500, "message", "设置失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有日志器列表
     * 
     * @param data 请求数据，可包含 keyword 用于过滤
     * @return 日志器列表
     */
    private Object handleGetLoggers(Map<String, Object> data) {
        String keyword = getString(data, "keyword");

        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            List<Logger> loggerList = loggerContext.getLoggerList();

            List<Map<String, Object>> loggers = loggerList.stream()
                    .filter(logger -> {
                        if (keyword == null || keyword.isEmpty()) {
                            return true;
                        }
                        return logger.getName().toLowerCase().contains(keyword.toLowerCase());
                    })
                    .map(logger -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("loggerName", logger.getName());
                        item.put("level", logger.getLevel() != null ? logger.getLevel().toString() : null);
                        item.put("effectiveLevel", logger.getEffectiveLevel().toString());
                        return item;
                    })
                    .collect(Collectors.toList());

            return Map.of(
                    "code", 200,
                    "message", "SUCCESS",
                    "data", loggers,
                    "total", loggers.size()
            );
        } catch (Exception e) {
            log.error("[LoggingConfig] 获取日志器列表失败: {}", e.getMessage(), e);
            return Map.of("code", 500, "message", "获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定日志器的级别
     * 
     * @param data 请求数据，包含 loggerName
     * @return 日志级别信息
     */
    private Object handleGetLevel(Map<String, Object> data) {
        String loggerName = getString(data, "loggerName");

        if (loggerName == null || loggerName.isEmpty()) {
            return Map.of("code", 400, "message", "loggerName is required");
        }

        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger logger = loggerContext.getLogger(loggerName);

            return Map.of(
                    "code", 200,
                    "message", "SUCCESS",
                    "loggerName", loggerName,
                    "level", logger.getLevel() != null ? logger.getLevel().toString() : null,
                    "effectiveLevel", logger.getEffectiveLevel().toString()
            );
        } catch (Exception e) {
            log.error("[LoggingConfig] 获取日志级别失败: {}", e.getMessage(), e);
            return Map.of("code", 500, "message", "获取失败: " + e.getMessage());
        }
    }

    private String getString(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v != null ? v.toString() : null;
    }
}
