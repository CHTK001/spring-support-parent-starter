package com.chua.report.client.starter.sync.handler;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.utils.MapUtils;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 节点控制处理器
 * <p>
 * 监听节点控制 Topic，提供重启和关闭功能
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/01/17
 */
@Slf4j
@Spi("nodeControlHandler")
public class NodeControlHandler implements SyncMessageHandler, ApplicationContextAware {

    private ApplicationContext applicationContext;
    
    /**
     * 延迟执行器
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "node-control-scheduler");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public String getName() {
        return "nodeControlHandler";
    }

    @Override
    public boolean supports(String topic) {
        return MonitorTopics.NODE_RESTART.equals(topic) 
                || MonitorTopics.NODE_SHUTDOWN.equals(topic);
    }

    @Override
    public Object handle(String topic, String sessionId, Map<String, Object> data) {
        String action = MapUtils.getString(data, "action", topic);
        log.info("[NodeControl] 收到控制命令: topic={}, action={}", topic, action);

        return switch (topic) {
            case MonitorTopics.NODE_RESTART -> handleRestart(data);
            case MonitorTopics.NODE_SHUTDOWN -> handleShutdown(data);
            default -> Map.of("code", 400, "message", "未知操作: " + topic);
        };
    }

    /**
     * 处理重启命令
     *
     * @param data 请求数据
     * @return 响应结果
     */
    private Object handleRestart(Map<String, Object> data) {
        try {
            // 获取延迟时间，默认 3 秒
            int delaySeconds = MapUtils.getInteger(data, "delaySeconds", 3);
            String reason = MapUtils.getString(data, "reason", "服务端下发重启命令");
            
            log.warn("[NodeControl] 收到重启命令，将在 {} 秒后重启应用: {}", delaySeconds, reason);

            // 延迟执行重启
            scheduler.schedule(() -> {
                try {
                    log.warn("[NodeControl] 正在重启应用...");
                    restartApplication();
                } catch (Exception e) {
                    log.error("[NodeControl] 重启应用失败: {}", e.getMessage(), e);
                }
            }, delaySeconds, TimeUnit.SECONDS);

            return Map.of(
                    "code", 200,
                    "message", "SUCCESS",
                    "info", "重启命令已接收，将在 " + delaySeconds + " 秒后重启"
            );

        } catch (Exception e) {
            log.error("[NodeControl] 处理重启命令失败: {}", e.getMessage(), e);
            return Map.of("code", 500, "message", "处理重启命令失败: " + e.getMessage());
        }
    }

    /**
     * 处理关闭命令
     *
     * @param data 请求数据
     * @return 响应结果
     */
    private Object handleShutdown(Map<String, Object> data) {
        try {
            // 获取延迟时间，默认 3 秒
            int delaySeconds = MapUtils.getInteger(data, "delaySeconds", 3);
            String reason = MapUtils.getString(data, "reason", "服务端下发关闭命令");
            
            log.warn("[NodeControl] 收到关闭命令，将在 {} 秒后关闭应用: {}", delaySeconds, reason);

            // 延迟执行关闭
            scheduler.schedule(() -> {
                try {
                    log.warn("[NodeControl] 正在关闭应用...");
                    shutdownApplication();
                } catch (Exception e) {
                    log.error("[NodeControl] 关闭应用失败: {}", e.getMessage(), e);
                }
            }, delaySeconds, TimeUnit.SECONDS);

            return Map.of(
                    "code", 200,
                    "message", "SUCCESS",
                    "info", "关闭命令已接收，将在 " + delaySeconds + " 秒后关闭"
            );

        } catch (Exception e) {
            log.error("[NodeControl] 处理关闭命令失败: {}", e.getMessage(), e);
            return Map.of("code", 500, "message", "处理关闭命令失败: " + e.getMessage());
        }
    }

    /**
     * 重启应用
     * <p>
     * 使用 Spring Boot 的优雅关闭机制，然后通过退出码触发重启
     * </p>
     */
    private void restartApplication() {
        if (applicationContext instanceof ConfigurableApplicationContext ctx) {
            // 使用退出码 0 触发重启（需要配合外部进程管理器如 systemd、docker 等）
            // 或者使用 Spring Boot DevTools 的重启机制
            try {
                // 尝试使用 Spring Boot Actuator 的重启功能
                Class<?> restartEndpointClass = Class.forName(
                        "org.springframework.cloud.context.restart.RestartEndpoint");
                Object restartEndpoint = applicationContext.getBean(restartEndpointClass);
                restartEndpointClass.getMethod("restart").invoke(restartEndpoint);
                log.info("[NodeControl] 通过 RestartEndpoint 重启成功");
            } catch (Exception e) {
                log.warn("[NodeControl] RestartEndpoint 不可用，使用退出方式重启: {}", e.getMessage());
                // 退出应用，依赖外部进程管理器重启
                int exitCode = SpringApplication.exit(ctx, () -> 0);
                System.exit(exitCode);
            }
        } else {
            log.error("[NodeControl] ApplicationContext 类型不支持重启操作");
        }
    }

    /**
     * 关闭应用
     * <p>
     * 使用 Spring Boot 的优雅关闭机制
     * </p>
     */
    private void shutdownApplication() {
        if (applicationContext instanceof ConfigurableApplicationContext ctx) {
            int exitCode = SpringApplication.exit(ctx, () -> 0);
            log.info("[NodeControl] 应用关闭，退出码: {}", exitCode);
            System.exit(exitCode);
        } else {
            log.error("[NodeControl] ApplicationContext 类型不支持关闭操作");
            System.exit(0);
        }
    }
}
