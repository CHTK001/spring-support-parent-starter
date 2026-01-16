package com.chua.report.client.starter.sync.handler;

import com.chua.common.support.core.annotation.Spi;
import com.chua.common.support.utils.MapUtils;
import com.chua.report.client.starter.sync.MonitorTopics;
import com.chua.sync.support.spi.SyncMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 节点控制处理器
 * <p>
 * 监听节点控制 Topic，提供重启和关闭功能
 * 包含重启保全机制：生成守护脚本检测应用启动状态
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
     * 默认健康检查超时时间（秒）
     */
    private static final int DEFAULT_HEALTH_CHECK_TIMEOUT = 120;

    /**
     * 健康检查间隔（秒）
     */
    private static final int HEALTH_CHECK_INTERVAL = 5;

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
            // 健康检查超时时间
            int healthCheckTimeout = MapUtils.getInteger(data, "healthCheckTimeout", DEFAULT_HEALTH_CHECK_TIMEOUT);

            log.warn("[NodeControl] 收到重启命令，将在 {} 秒后重启应用: {}", delaySeconds, reason);

            // 生成并启动守护脚本（保全机制）
            Path watchdogScript = generateWatchdogScript(healthCheckTimeout);
            if (watchdogScript != null) {
                startWatchdogScript(watchdogScript);
                log.info("[NodeControl] 守护脚本已启动: {}", watchdogScript);
            }

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
                    "info", "重启命令已接收，将在 " + delaySeconds + " 秒后重启",
                    "watchdog", watchdogScript != null ? watchdogScript.toString() : "未生成"
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

    /**
     * 生成守护脚本（保全机制）
     * <p>
     * 脚本功能：
     * 1. 定期检测应用健康状态
     * 2. 启动成功后自动退出
     * 3. 超时未启动记录日志
     * </p>
     *
     * @param timeoutSeconds 超时时间（秒）
     * @return 脚本文件路径
     */
    private Path generateWatchdogScript(int timeoutSeconds) {
        try {
            Environment env = applicationContext.getEnvironment();
            String serverPort = env.getProperty("server.port", "8080");
            String contextPath = env.getProperty("server.servlet.context-path", "");
            String actuatorPath = env.getProperty("management.endpoints.web.base-path", "/actuator");
            String appName = env.getProperty("spring.application.name", "application");

            // 构建健康检查 URL
            String healthUrl = String.format("http://localhost:%s%s%s/health", serverPort, contextPath, actuatorPath);

            // 判断操作系统
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            // 生成脚本内容
            String scriptContent;
            String scriptExtension;
            if (isWindows) {
                scriptContent = generateWindowsScript(healthUrl, timeoutSeconds, appName);
                scriptExtension = ".bat";
            } else {
                scriptContent = generateLinuxScript(healthUrl, timeoutSeconds, appName);
                scriptExtension = ".sh";
            }

            // 创建临时脚本文件
            Path scriptPath = Files.createTempFile("restart-watchdog-" + appName + "-", scriptExtension);
            Files.writeString(scriptPath, scriptContent, StandardCharsets.UTF_8);

            // Linux 下设置执行权限
            if (!isWindows) {
                try {
                    Files.setPosixFilePermissions(scriptPath, Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE
                    ));
                } catch (UnsupportedOperationException e) {
                    log.debug("[NodeControl] 无法设置 POSIX 权限: {}", e.getMessage());
                }
            }

            log.info("[NodeControl] 守护脚本已生成: {}", scriptPath);
            return scriptPath;

        } catch (IOException e) {
            log.error("[NodeControl] 生成守护脚本失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 生成 Windows 守护脚本
     *
     * @param healthUrl      健康检查 URL
     * @param timeoutSeconds 超时时间
     * @param appName        应用名称
     * @return 脚本内容
     */
    private String generateWindowsScript(String healthUrl, int timeoutSeconds, String appName) {
        return """
                @echo off
                chcp 65001 >nul
                setlocal enabledelayedexpansion

                :: 重启守护脚本 - %s
                :: 健康检查 URL: %s
                :: 超时时间: %d 秒

                set HEALTH_URL=%s
                set TIMEOUT=%d
                set INTERVAL=%d
                set ELAPSED=0
                set LOG_FILE=%%TEMP%%\\restart-watchdog-%s.log

                echo [%%date%% %%time%%] 守护脚本启动，监控应用健康状态... >> "%%LOG_FILE%%"
                echo [%%date%% %%time%%] 健康检查 URL: %%HEALTH_URL%% >> "%%LOG_FILE%%"

                :LOOP
                if %%ELAPSED%% geq %%TIMEOUT%% goto TIMEOUT

                :: 使用 curl 检查健康状态
                curl -s -o nul -w "%%%%{http_code}" "%%HEALTH_URL%%" > %%TEMP%%\\health_code.txt 2>nul
                set /p HTTP_CODE=<%%TEMP%%\\health_code.txt

                :: 不是 404 和 000（连接失败）都算成功
                if not "%%HTTP_CODE%%"=="404" (
                    if not "%%HTTP_CODE%%"=="000" (
                        echo [%%date%% %%time%%] 应用启动成功！HTTP 状态码: %%HTTP_CODE%% >> "%%LOG_FILE%%"
                        echo 应用启动成功，守护脚本退出
                        del /q %%TEMP%%\\health_code.txt 2>nul
                        goto END
                    )
                )

                echo [%%date%% %%time%%] 等待应用启动... (%%ELAPSED%%/%%TIMEOUT%%秒) HTTP: %%HTTP_CODE%% >> "%%LOG_FILE%%"
                timeout /t %%INTERVAL%% /nobreak >nul
                set /a ELAPSED+=%%INTERVAL%%
                goto LOOP

                :TIMEOUT
                echo [%%date%% %%time%%] 警告：应用在 %%TIMEOUT%% 秒内未能成功启动！ >> "%%LOG_FILE%%"
                echo 警告：应用启动超时，请检查日志！

                :END
                del /q %%TEMP%%\\health_code.txt 2>nul
                echo [%%date%% %%time%%] 守护脚本结束 >> "%%LOG_FILE%%"
                """.formatted(appName, healthUrl, timeoutSeconds, healthUrl, timeoutSeconds, HEALTH_CHECK_INTERVAL, appName);
    }

    /**
     * 生成 Linux 守护脚本
     *
     * @param healthUrl      健康检查 URL
     * @param timeoutSeconds 超时时间
     * @param appName        应用名称
     * @return 脚本内容
     */
    private String generateLinuxScript(String healthUrl, int timeoutSeconds, String appName) {
        return """
                #!/bin/bash

                # 重启守护脚本 - %s
                # 健康检查 URL: %s
                # 超时时间: %d 秒

                HEALTH_URL="%s"
                TIMEOUT=%d
                INTERVAL=%d
                ELAPSED=0
                LOG_FILE="/tmp/restart-watchdog-%s.log"

                echo "[$(date '+%%Y-%%m-%%d %%H:%%M:%%S')] 守护脚本启动，监控应用健康状态..." >> "$LOG_FILE"
                echo "[$(date '+%%Y-%%m-%%d %%H:%%M:%%S')] 健康检查 URL: $HEALTH_URL" >> "$LOG_FILE"

                while [ $ELAPSED -lt $TIMEOUT ]; do
                    # 检查健康状态
                    HTTP_CODE=$(curl -s -o /dev/null -w "%%{http_code}" "$HEALTH_URL" 2>/dev/null)

                    # 不是 404 和 000（连接失败）都算成功
                    if [ "$HTTP_CODE" != "404" ] && [ "$HTTP_CODE" != "000" ] && [ -n "$HTTP_CODE" ]; then
                        echo "[$(date '+%%Y-%%m-%%d %%H:%%M:%%S')] 应用启动成功！HTTP 状态码: $HTTP_CODE" >> "$LOG_FILE"
                        echo "应用启动成功，守护脚本退出"
                        exit 0
                    fi

                    echo "[$(date '+%%Y-%%m-%%d %%H:%%M:%%S')] 等待应用启动... ($ELAPSED/$TIMEOUT秒) HTTP: $HTTP_CODE" >> "$LOG_FILE"
                    sleep $INTERVAL
                    ELAPSED=$((ELAPSED + INTERVAL))
                done

                echo "[$(date '+%%Y-%%m-%%d %%H:%%M:%%S')] 警告：应用在 $TIMEOUT 秒内未能成功启动！" >> "$LOG_FILE"
                echo "警告：应用启动超时，请检查日志！"

                echo "[$(date '+%%Y-%%m-%%d %%H:%%M:%%S')] 守护脚本结束" >> "$LOG_FILE"
                """.formatted(appName, healthUrl, timeoutSeconds, healthUrl, timeoutSeconds, HEALTH_CHECK_INTERVAL, appName);
    }

    /**
     * 启动守护脚本
     *
     * @param scriptPath 脚本路径
     */
    private void startWatchdogScript(Path scriptPath) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb;

            if (isWindows) {
                // Windows: 使用 start 命令在新窗口中运行
                pb = new ProcessBuilder("cmd", "/c", "start", "/min", "重启守护", "cmd", "/c", scriptPath.toString());
            } else {
                // Linux: 使用 nohup 后台运行
                pb = new ProcessBuilder("nohup", scriptPath.toString(), "&");
                pb.redirectOutput(new File("/dev/null"));
                pb.redirectError(new File("/dev/null"));
            }

            pb.directory(scriptPath.getParent().toFile());
            Process process = pb.start();

            log.info("[NodeControl] 守护脚本进程已启动: PID={}", process.pid());

        } catch (IOException e) {
            log.error("[NodeControl] 启动守护脚本失败: {}", e.getMessage(), e);
        }
    }
}
