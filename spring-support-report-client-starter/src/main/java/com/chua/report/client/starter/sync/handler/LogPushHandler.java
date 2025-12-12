package com.chua.report.client.starter.sync.handler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.MapUtils;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.SyncClient;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志推送处理器
 * <p>
 * 监听应用日志并推送到 Monitor
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/01/17
 */
@Slf4j
@Spi("logPushHandler")
public class LogPushHandler implements SyncMessageHandler, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Value("${server.port:8080}")
    private Integer serverPort;

    /**
     * 订阅状态（sessionId -> 是否订阅）
     */
    private final ConcurrentHashMap<String, AtomicBoolean> subscriptions = new ConcurrentHashMap<>();

    /**
     * 日志 Appender
     */
    private LogPushAppender logAppender;

    /**
     * 日志级别过滤
     */
    private String logLevelFilter = "INFO";

    /**
     * SyncClient 用于推送日志
     */
    private SyncClient syncClient;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        try {
            syncClient = applicationContext.getBean(SyncClient.class);
        } catch (Exception e) {
            log.warn("[LogPush] SyncClient 未配置，日志推送功能不可用");
        }
    }

    @Override
    public String getName() {
        return "logPushHandler";
    }

    @Override
    public boolean supports(String topic) {
        return MonitorTopics.LOG_SUBSCRIBE.equals(topic) 
                || MonitorTopics.LOG_UNSUBSCRIBE.equals(topic);
    }

    @Override
    public Object handle(String topic, String sessionId, Map<String, Object> data) {
        log.info("[LogPush] 收到消息: topic={}, sessionId={}", topic, sessionId);

        return switch (topic) {
            case MonitorTopics.LOG_SUBSCRIBE -> handleSubscribe(sessionId, data);
            case MonitorTopics.LOG_UNSUBSCRIBE -> handleUnsubscribe(sessionId);
            default -> Map.of("code", 400, "message", "未知操作");
        };
    }

    /**
     * 处理日志订阅
     *
     * @param sessionId 会话ID
     * @param data      请求数据
     * @return 订阅结果
     */
    private Map<String, Object> handleSubscribe(String sessionId, Map<String, Object> data) {
        try {
            // 获取日志级别过滤
            String level = MapUtils.getString(data, "level", "INFO");
            this.logLevelFilter = level.toUpperCase();

            // 记录订阅
            subscriptions.computeIfAbsent(sessionId, k -> new AtomicBoolean(false)).set(true);

            // 启动日志监听
            startLogAppender();

            log.info("[LogPush] 日志订阅成功: sessionId={}, level={}", sessionId, level);

            return Map.of(
                    "code", 200,
                    "message", "订阅成功",
                    "data", Map.of(
                            "applicationName", applicationName,
                            "port", serverPort,
                            "level", logLevelFilter
                    )
            );
        } catch (Exception e) {
            log.error("[LogPush] 订阅失败", e);
            return Map.of("code", 500, "message", "订阅失败: " + e.getMessage());
        }
    }

    /**
     * 处理取消订阅
     *
     * @param sessionId 会话ID
     * @return 取消结果
     */
    private Map<String, Object> handleUnsubscribe(String sessionId) {
        AtomicBoolean subscription = subscriptions.get(sessionId);
        if (subscription != null) {
            subscription.set(false);
            subscriptions.remove(sessionId);
        }

        // 如果没有订阅者，停止 Appender
        if (subscriptions.isEmpty()) {
            stopLogAppender();
        }

        log.info("[LogPush] 取消订阅: sessionId={}", sessionId);

        return Map.of("code", 200, "message", "取消订阅成功");
    }

    /**
     * 启动日志 Appender
     */
    private synchronized void startLogAppender() {
        if (logAppender != null) {
            return;
        }

        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

            logAppender = new LogPushAppender();
            logAppender.setContext(loggerContext);
            logAppender.setName("LOG_PUSH_APPENDER");
            logAppender.start();

            rootLogger.addAppender(logAppender);

            log.info("[LogPush] 日志 Appender 已启动");
        } catch (Exception e) {
            log.error("[LogPush] 启动日志 Appender 失败", e);
        }
    }

    /**
     * 停止日志 Appender
     */
    private synchronized void stopLogAppender() {
        if (logAppender == null) {
            return;
        }

        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

            rootLogger.detachAppender(logAppender);
            logAppender.stop();
            logAppender = null;

            log.info("[LogPush] 日志 Appender 已停止");
        } catch (Exception e) {
            log.error("[LogPush] 停止日志 Appender 失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        stopLogAppender();
        subscriptions.clear();
    }

    /**
     * 推送日志到 Monitor
     *
     * @param logEntry 日志条目
     */
    private void pushLog(Map<String, Object> logEntry) {
        if (syncClient == null || subscriptions.isEmpty()) {
            return;
        }

        try {
            syncClient.send(MonitorTopics.LOG_PUSH, logEntry);
        } catch (Exception e) {
            // 推送失败不记录日志，避免死循环
        }
    }

    /**
     * 检查日志级别是否满足过滤条件
     */
    private boolean shouldPush(String level) {
        int currentLevel = getLevelPriority(level);
        int filterLevel = getLevelPriority(logLevelFilter);
        return currentLevel >= filterLevel;
    }

    /**
     * 获取日志级别优先级
     */
    private int getLevelPriority(String level) {
        return switch (level.toUpperCase()) {
            case "TRACE" -> 1;
            case "DEBUG" -> 2;
            case "INFO" -> 3;
            case "WARN" -> 4;
            case "ERROR" -> 5;
            default -> 3;
        };
    }

    /**
     * 日志推送 Appender
     */
    private class LogPushAppender extends AppenderBase<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent event) {
            // 过滤掉自身的日志，避免死循环
            if (event.getLoggerName().contains("LogPushHandler")) {
                return;
            }

            String level = event.getLevel().toString();
            if (!shouldPush(level)) {
                return;
            }

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("applicationName", applicationName);
            logEntry.put("port", serverPort);
            logEntry.put("timestamp", event.getTimeStamp());
            logEntry.put("time", formatTime(event.getTimeStamp()));
            logEntry.put("level", level);
            logEntry.put("loggerName", event.getLoggerName());
            logEntry.put("threadName", event.getThreadName());
            logEntry.put("message", event.getFormattedMessage());

            // 异常信息
            if (event.getThrowableProxy() != null) {
                logEntry.put("exception", event.getThrowableProxy().getMessage());
                logEntry.put("exceptionClass", event.getThrowableProxy().getClassName());
            }

            pushLog(logEntry);
        }

        private String formatTime(long timestamp) {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneId.systemDefault()
            ).format(FORMATTER);
        }
    }
}
