package com.chua.starter.monitor.starter.service;

import com.chua.starter.monitor.starter.entity.MonitorSysGenServer;
import com.chua.starter.monitor.starter.entity.MonitorSysGenServerSetting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 统一定时任务管理服务
 * 参考PrometheusDataPushService的实现模式，统一管理所有定时任务
 *
 * @author CH
 * @since 2025/01/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedScheduledTaskService {

    private final MonitorSysGenServerSettingService serverSettingService;
    private final MonitorSysGenServerService serverService;
    private final ServerMetricsWebSocketService serverMetricsWebSocketService;
    private final ServerConnectionService serverConnectionService;

    /**
     * 定时任务执行器 - 端口检测
     */
    private final ScheduledExecutorService portCheckScheduler = Executors.newScheduledThreadPool(3);

    /**
     * 定时任务执行器 - 在线状态检测
     */
    private final ScheduledExecutorService onlineCheckScheduler = Executors.newScheduledThreadPool(3);

    /**
     * 定时任务执行器 - 延迟检测
     */
    private final ScheduledExecutorService latencyCheckScheduler = Executors.newScheduledThreadPool(3);

    /**
     * 定时任务执行器 - 清理任务
     */
    private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(2);

    /**
     * 端口检测任务缓存
     */
    private final ConcurrentHashMap<Integer, ScheduledTask> portCheckTasks = new ConcurrentHashMap<>();

    /**
     * 在线状态检测任务缓存
     */
    private final ConcurrentHashMap<Integer, ScheduledTask> onlineCheckTasks = new ConcurrentHashMap<>();

    /**
     * 延迟检测任务缓存
     */
    private final ConcurrentHashMap<Integer, ScheduledTask> latencyCheckTasks = new ConcurrentHashMap<>();

    /**
     * 清理任务缓存
     */
    private final ConcurrentHashMap<String, ScheduledTask> cleanupTasks = new ConcurrentHashMap<>();

    /**
     * 定时任务包装类
     */
    public static class ScheduledTask {
        private final ScheduledFuture<?> future;
        private final int frequency;
        private final String taskType;

        public ScheduledTask(ScheduledFuture<?> future, int frequency, String taskType) {
            this.future = future;
            this.frequency = frequency;
            this.taskType = taskType;
        }

        public void cancel() {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        }

        public int getFrequency() {
            return frequency;
        }

        public String getTaskType() {
            return taskType;
        }

        public boolean isCancelled() {
            return future == null || future.isCancelled();
        }

        public boolean isDone() {
            return future != null && future.isDone();
        }
    }

    /**
     * 任务状态信息
     */
    public static class TaskStatus {
        private Integer serverId;
        private String taskType;
        private boolean running;
        private int frequency;
        private String lastExecuteTime;
        private String errorMessage;

        // Getters and Setters
        public Integer getServerId() { return serverId; }
        public void setServerId(Integer serverId) { this.serverId = serverId; }

        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }

        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }

        public int getFrequency() { return frequency; }
        public void setFrequency(int frequency) { this.frequency = frequency; }

        public String getLastExecuteTime() { return lastExecuteTime; }
        public void setLastExecuteTime(String lastExecuteTime) { this.lastExecuteTime = lastExecuteTime; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    /**
     * 启动服务器的端口检测任务
     *
     * @param serverId 服务器ID
     */
    public void startPortCheckTask(Integer serverId) {
        try {
            MonitorSysGenServerSetting setting = serverSettingService.getByServerId(serverId);
            if (setting == null || !setting.isPortMonitorEnabled()) {
                log.debug("服务器端口监控未启用，跳过启动，服务器ID: {}", serverId);
                return;
            }

            // 停止现有任务
            stopPortCheckTask(serverId);

            int frequency = setting.getMonitorSysGenServerSettingPortCheckInterval();
            if (frequency <= 0) {
                frequency = 60; // 默认60秒
            }

            log.info("启动服务器端口检测任务，服务器ID: {}, 频率: {}秒", serverId, frequency);

            ScheduledFuture<?> future = portCheckScheduler.scheduleAtFixedRate(
                () -> executePortCheck(serverId),
                0, frequency, TimeUnit.SECONDS
            );

            portCheckTasks.put(serverId, new ScheduledTask(future, frequency, "PORT_CHECK"));

        } catch (Exception e) {
            log.error("启动端口检测任务失败，服务器ID: {}", serverId, e);
        }
    }

    /**
     * 停止服务器的端口检测任务
     *
     * @param serverId 服务器ID
     */
    public void stopPortCheckTask(Integer serverId) {
        ScheduledTask task = portCheckTasks.remove(serverId);
        if (task != null) {
            task.cancel();
            log.info("停止服务器端口检测任务，服务器ID: {}", serverId);
        }
    }

    /**
     * 启动服务器的在线状态检测任务
     *
     * @param serverId 服务器ID
     */
    public void startOnlineCheckTask(Integer serverId) {
        try {
            MonitorSysGenServerSetting setting = serverSettingService.getByServerId(serverId);
            if (setting == null || !setting.isOnlineCheckEnabled()) {
                log.debug("服务器在线检测未启用，跳过启动，服务器ID: {}", serverId);
                return;
            }

            // 停止现有任务
            stopOnlineCheckTask(serverId);

            int frequency = setting.getMonitorSysGenServerSettingOnlineCheckInterval();
            if (frequency <= 0) {
                frequency = 30; // 默认30秒
            }

            log.info("启动服务器在线状态检测任务，服务器ID: {}, 频率: {}秒", serverId, frequency);

            ScheduledFuture<?> future = onlineCheckScheduler.scheduleAtFixedRate(
                () -> executeOnlineCheck(serverId),
                0, frequency, TimeUnit.SECONDS
            );

            onlineCheckTasks.put(serverId, new ScheduledTask(future, frequency, "ONLINE_CHECK"));

        } catch (Exception e) {
            log.error("启动在线状态检测任务失败，服务器ID: {}", serverId, e);
        }
    }

    /**
     * 停止服务器的在线状态检测任务
     *
     * @param serverId 服务器ID
     */
    public void stopOnlineCheckTask(Integer serverId) {
        ScheduledTask task = onlineCheckTasks.remove(serverId);
        if (task != null) {
            task.cancel();
            log.info("停止服务器在线状态检测任务，服务器ID: {}", serverId);
        }
    }

    /**
     * 启动服务器的延迟检测任务
     *
     * @param serverId 服务器ID
     */
    public void startLatencyCheckTask(Integer serverId) {
        try {
            MonitorSysGenServerSetting setting = serverSettingService.getByServerId(serverId);
            if (setting == null || !setting.isLatencyCheckEnabled()) {
                log.debug("服务器延迟检测未启用，跳过启动，服务器ID: {}", serverId);
                return;
            }

            // 停止现有任务
            stopLatencyCheckTask(serverId);

            int frequency = setting.getMonitorSysGenServerSettingLatencyCheckInterval();
            if (frequency <= 0) {
                frequency = 60; // 默认60秒
            }

            log.info("启动服务器延迟检测任务，服务器ID: {}, 频率: {}秒", serverId, frequency);

            ScheduledFuture<?> future = latencyCheckScheduler.scheduleAtFixedRate(
                () -> executeLatencyCheck(serverId),
                0, frequency, TimeUnit.SECONDS
            );

            latencyCheckTasks.put(serverId, new ScheduledTask(future, frequency, "LATENCY_CHECK"));

        } catch (Exception e) {
            log.error("启动延迟检测任务失败，服务器ID: {}", serverId, e);
        }
    }

    /**
     * 停止服务器的延迟检测任务
     *
     * @param serverId 服务器ID
     */
    public void stopLatencyCheckTask(Integer serverId) {
        ScheduledTask task = latencyCheckTasks.remove(serverId);
        if (task != null) {
            task.cancel();
            log.info("停止服务器延迟检测任务，服务器ID: {}", serverId);
        }
    }

    /**
     * 启动清理任务
     */
    public void startCleanupTasks() {
        startLogCleanupTask();
        startTempFileCleanupTask();
        startWebSocketCleanupTask();
    }

    /**
     * 停止所有清理任务
     */
    public void stopCleanupTasks() {
        cleanupTasks.values().forEach(ScheduledTask::cancel);
        cleanupTasks.clear();
        log.info("停止所有清理任务");
    }

    /**
     * 执行端口检测
     */
    private void executePortCheck(Integer serverId) {
        try {
            log.debug("执行端口检测，服务器ID: {}", serverId);

            MonitorSysGenServer server = serverService.getById(serverId);
            MonitorSysGenServerSetting setting = serverSettingService.getByServerId(serverId);

            if (server == null || setting == null) {
                log.warn("服务器或配置不存在，跳过端口检测，服务器ID: {}", serverId);
                return;
            }

            // 获取监控端口列表
            java.util.List<Integer> ports = setting.getMonitorPortsList();
            if (ports.isEmpty()) {
                log.debug("服务器未配置监控端口，跳过检测，服务器ID: {}", serverId);
                return;
            }

            // 检测每个端口
            java.util.List<Map<String, Object>> portResults = new java.util.ArrayList<>();
            for (Integer port : ports) {
                Map<String, Object> result = checkSinglePort(server.getMonitorSysGenServerHost(), port);
                result.put("serverId", serverId);
                portResults.add(result);
            }

            // 推送结果到WebSocket
            Map<String, Object> message = new java.util.HashMap<>();
            message.put("type", "port_check_result");
            message.put("serverId", serverId);
            message.put("portResults", portResults);
            message.put("timestamp", System.currentTimeMillis());

            serverMetricsWebSocketService.pushCustomMessage(serverId, message);

        } catch (Exception e) {
            log.error("执行端口检测失败，服务器ID: {}", serverId, e);
        }
    }

    /**
     * 执行在线状态检测
     */
    private void executeOnlineCheck(Integer serverId) {
        try {
            log.debug("执行在线状态检测，服务器ID: {}", serverId);

            MonitorSysGenServer server = serverService.getById(serverId);
            if (server == null) {
                log.warn("服务器不存在，跳过在线检测，服务器ID: {}", serverId);
                return;
            }

            // 执行连接测试
            boolean isOnline = testServerConnection(server);

            // 更新服务器状态
            if (server.getMonitorSysGenServerStatus() != (isOnline ? 1 : 0)) {
                server.setMonitorSysGenServerStatus(isOnline ? 1 : 0);
                serverService.updateById(server);

                // 推送状态变化到WebSocket
                Map<String, Object> message = new java.util.HashMap<>();
                message.put("type", "online_status_change");
                message.put("serverId", serverId);
                message.put("isOnline", isOnline);
                message.put("timestamp", System.currentTimeMillis());

                serverMetricsWebSocketService.pushCustomMessage(serverId, message);

                log.info("服务器在线状态变化，服务器ID: {}, 状态: {}", serverId, isOnline ? "在线" : "离线");
            }

        } catch (Exception e) {
            log.error("执行在线状态检测失败，服务器ID: {}", serverId, e);
        }
    }

    /**
     * 执行延迟检测
     */
    private void executeLatencyCheck(Integer serverId) {
        try {
            log.debug("执行延迟检测，服务器ID: {}", serverId);

            MonitorSysGenServer server = serverService.getById(serverId);
            if (server == null) {
                log.warn("服务器不存在，跳过延迟检测，服务器ID: {}", serverId);
                return;
            }

            // 执行延迟测试
            long latency = measureServerLatency(server);

            // 推送延迟结果到WebSocket
            Map<String, Object> message = new java.util.HashMap<>();
            message.put("type", "latency_check_result");
            message.put("serverId", serverId);
            message.put("latency", latency);
            message.put("timestamp", System.currentTimeMillis());

            serverMetricsWebSocketService.pushCustomMessage(serverId, message);

        } catch (Exception e) {
            log.error("执行延迟检测失败，服务器ID: {}", serverId, e);
        }
    }

    /**
     * 启动日志清理任务
     */
    private void startLogCleanupTask() {
        try {
            // 每天凌晨2点执行日志清理
            long initialDelay = calculateInitialDelayForDailyTask(2, 0); // 2:00 AM

            ScheduledFuture<?> future = cleanupScheduler.scheduleAtFixedRate(
                this::executeLogCleanup,
                initialDelay, 24 * 60 * 60, TimeUnit.SECONDS // 每24小时执行一次
            );

            cleanupTasks.put("LOG_CLEANUP", new ScheduledTask(future, 24 * 60 * 60, "LOG_CLEANUP"));
            log.info("启动日志清理任务，每天凌晨2点执行");

        } catch (Exception e) {
            log.error("启动日志清理任务失败", e);
        }
    }

    /**
     * 启动临时文件清理任务
     */
    private void startTempFileCleanupTask() {
        try {
            // 每天凌晨3点执行临时文件清理
            long initialDelay = calculateInitialDelayForDailyTask(3, 0); // 3:00 AM

            ScheduledFuture<?> future = cleanupScheduler.scheduleAtFixedRate(
                this::executeTempFileCleanup,
                initialDelay, 24 * 60 * 60, TimeUnit.SECONDS // 每24小时执行一次
            );

            cleanupTasks.put("TEMP_FILE_CLEANUP", new ScheduledTask(future, 24 * 60 * 60, "TEMP_FILE_CLEANUP"));
            log.info("启动临时文件清理任务，每天凌晨3点执行");

        } catch (Exception e) {
            log.error("启动临时文件清理任务失败", e);
        }
    }

    /**
     * 启动WebSocket会话清理任务
     */
    private void startWebSocketCleanupTask() {
        try {
            // 每10分钟执行一次WebSocket会话清理
            ScheduledFuture<?> future = cleanupScheduler.scheduleAtFixedRate(
                this::executeWebSocketCleanup,
                600, 600, TimeUnit.SECONDS // 每10分钟执行一次
            );

            cleanupTasks.put("WEBSOCKET_CLEANUP", new ScheduledTask(future, 600, "WEBSOCKET_CLEANUP"));
            log.info("启动WebSocket会话清理任务，每10分钟执行一次");

        } catch (Exception e) {
            log.error("启动WebSocket会话清理任务失败", e);
        }
    }

    /**
     * 执行日志清理
     */
    private void executeLogCleanup() {
        try {
            log.info("开始执行日志清理任务");

            // 获取全局配置中的日志保留天数
            // 这里可以从配置服务获取，暂时使用默认值
            int retentionDays = 7; // 默认保留7天

            java.time.LocalDateTime expireTime = java.time.LocalDateTime.now().minusDays(retentionDays);

            // 清理各种类型的日志
            int cleanedCount = 0;

            // 清理连接日志、上传日志等
            // 具体实现可以调用相应的清理方法

            log.info("日志清理任务完成，清理了 {} 条记录", cleanedCount);

        } catch (Exception e) {
            log.error("执行日志清理任务失败", e);
        }
    }

    /**
     * 执行临时文件清理
     */
    private void executeTempFileCleanup() {
        try {
            log.info("开始执行临时文件清理任务");

            int cleanedFiles = 0;
            long cleanedSize = 0;

            // 清理系统临时目录
            cleanedFiles += cleanupTempDirectory(System.getProperty("java.io.tmpdir"), 24); // 24小时前的文件

            // 清理应用临时目录
            cleanedFiles += cleanupTempDirectory("./temp", 24);

            log.info("临时文件清理任务完成，清理了 {} 个文件，释放空间 {} MB",
                    cleanedFiles, cleanedSize / (1024 * 1024));

        } catch (Exception e) {
            log.error("执行临时文件清理任务失败", e);
        }
    }

    /**
     * 执行WebSocket会话清理
     */
    private void executeWebSocketCleanup() {
        try {
            log.debug("开始执行WebSocket会话清理任务");

            // 检查并清理无效的WebSocket连接
            // 具体实现需要访问WebSocket会话管理器

            log.debug("WebSocket会话清理任务完成");

        } catch (Exception e) {
            log.error("执行WebSocket会话清理任务失败", e);
        }
    }

    /**
     * 检查单个端口状态
     */
    private Map<String, Object> checkSinglePort(String host, int port) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("host", host);
        result.put("port", port);
        result.put("checkTime", java.time.LocalDateTime.now().toString());

        long startTime = System.currentTimeMillis();

        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 5000); // 5秒超时
            long responseTime = System.currentTimeMillis() - startTime;

            result.put("isOpen", true);
            result.put("responseTime", responseTime);
            result.put("errorMessage", null);

        } catch (Exception e) {
            result.put("isOpen", false);
            result.put("responseTime", System.currentTimeMillis() - startTime);
            result.put("errorMessage", e.getMessage());
        }

        return result;
    }

    /**
     * 测试服务器连接
     */
    private boolean testServerConnection(MonitorSysGenServer server) {
        try {
            // 根据服务器协议选择不同的连接测试方式
            String protocol = server.getMonitorSysGenServerProtocol();
            String host = server.getMonitorSysGenServerHost();
            Integer port = server.getMonitorSysGenServerPort();

            if ("SSH".equalsIgnoreCase(protocol)) {
                return testSSHConnection(host, port != null ? port : 22);
            } else if ("RDP".equalsIgnoreCase(protocol)) {
                return testRDPConnection(host, port != null ? port : 3389);
            } else if ("VNC".equalsIgnoreCase(protocol)) {
                return testVNCConnection(host, port != null ? port : 5900);
            } else {
                // 默认使用TCP连接测试
                return testTCPConnection(host, port != null ? port : 22);
            }

        } catch (Exception e) {
            log.error("测试服务器连接失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 测量服务器延迟
     */
    private long measureServerLatency(MonitorSysGenServer server) {
        long startTime = System.currentTimeMillis();

        try {
            String host = server.getMonitorSysGenServerHost();
            Integer port = server.getMonitorSysGenServerPort();

            // 使用TCP连接测试延迟
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port != null ? port : 22), 5000);
                return System.currentTimeMillis() - startTime;
            }

        } catch (Exception e) {
            log.debug("延迟测试失败: {}", e.getMessage());
            return -1; // 返回-1表示测试失败
        }
    }

    /**
     * 计算每日任务的初始延迟
     */
    private long calculateInitialDelayForDailyTask(int hour, int minute) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        if (nextRun.isBefore(now)) {
            nextRun = nextRun.plusDays(1);
        }

        return java.time.Duration.between(now, nextRun).getSeconds();
    }

    /**
     * 清理临时目录
     */
    private int cleanupTempDirectory(String dirPath, int hoursOld) {
        int cleanedCount = 0;

        try {
            java.io.File dir = new java.io.File(dirPath);
            if (!dir.exists() || !dir.isDirectory()) {
                return 0;
            }

            long cutoffTime = System.currentTimeMillis() - (hoursOld * 60 * 60 * 1000L);

            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    if (file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            cleanedCount++;
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("清理临时目录失败: {}", dirPath, e);
        }

        return cleanedCount;
    }

    // 连接测试方法
    private boolean testTCPConnection(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 5000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testSSHConnection(String host, int port) {
        return testTCPConnection(host, port);
    }

    private boolean testRDPConnection(String host, int port) {
        return testTCPConnection(host, port);
    }

    private boolean testVNCConnection(String host, int port) {
        return testTCPConnection(host, port);
    }

    /**
     * 启动服务器的所有任务
     *
     * @param serverId 服务器ID
     */
    public void startAllServerTasks(Integer serverId) {
        startPortCheckTask(serverId);
        startOnlineCheckTask(serverId);
        startLatencyCheckTask(serverId);
    }

    /**
     * 停止服务器的所有任务
     *
     * @param serverId 服务器ID
     */
    public void stopAllServerTasks(Integer serverId) {
        stopPortCheckTask(serverId);
        stopOnlineCheckTask(serverId);
        stopLatencyCheckTask(serverId);
    }

    /**
     * 重启服务器的所有任务
     *
     * @param serverId 服务器ID
     */
    public void restartAllServerTasks(Integer serverId) {
        stopAllServerTasks(serverId);
        startAllServerTasks(serverId);
    }

    /**
     * 获取服务器任务状态
     *
     * @param serverId 服务器ID
     * @return 任务状态列表
     */
    public java.util.List<TaskStatus> getServerTaskStatus(Integer serverId) {
        java.util.List<TaskStatus> statusList = new java.util.ArrayList<>();

        // 端口检测任务状态
        ScheduledTask portTask = portCheckTasks.get(serverId);
        if (portTask != null) {
            TaskStatus status = new TaskStatus();
            status.setServerId(serverId);
            status.setTaskType("PORT_CHECK");
            status.setRunning(!portTask.isCancelled());
            status.setFrequency(portTask.getFrequency());
            statusList.add(status);
        }

        // 在线检测任务状态
        ScheduledTask onlineTask = onlineCheckTasks.get(serverId);
        if (onlineTask != null) {
            TaskStatus status = new TaskStatus();
            status.setServerId(serverId);
            status.setTaskType("ONLINE_CHECK");
            status.setRunning(!onlineTask.isCancelled());
            status.setFrequency(onlineTask.getFrequency());
            statusList.add(status);
        }

        // 延迟检测任务状态
        ScheduledTask latencyTask = latencyCheckTasks.get(serverId);
        if (latencyTask != null) {
            TaskStatus status = new TaskStatus();
            status.setServerId(serverId);
            status.setTaskType("LATENCY_CHECK");
            status.setRunning(!latencyTask.isCancelled());
            status.setFrequency(latencyTask.getFrequency());
            statusList.add(status);
        }

        return statusList;
    }

    /**
     * 获取所有清理任务状态
     *
     * @return 清理任务状态列表
     */
    public java.util.List<TaskStatus> getCleanupTaskStatus() {
        java.util.List<TaskStatus> statusList = new java.util.ArrayList<>();

        for (Map.Entry<String, ScheduledTask> entry : cleanupTasks.entrySet()) {
            TaskStatus status = new TaskStatus();
            status.setTaskType(entry.getKey());
            status.setRunning(!entry.getValue().isCancelled());
            status.setFrequency(entry.getValue().getFrequency());
            statusList.add(status);
        }

        return statusList;
    }

    /**
     * 获取所有任务统计信息
     *
     * @return 任务统计信息
     */
    public Map<String, Object> getTaskStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();

        stats.put("portCheckTaskCount", portCheckTasks.size());
        stats.put("onlineCheckTaskCount", onlineCheckTasks.size());
        stats.put("latencyCheckTaskCount", latencyCheckTasks.size());
        stats.put("cleanupTaskCount", cleanupTasks.size());

        // 运行中的任务数量
        long runningPortTasks = portCheckTasks.values().stream().mapToLong(t -> t.isCancelled() ? 0 : 1).sum();
        long runningOnlineTasks = onlineCheckTasks.values().stream().mapToLong(t -> t.isCancelled() ? 0 : 1).sum();
        long runningLatencyTasks = latencyCheckTasks.values().stream().mapToLong(t -> t.isCancelled() ? 0 : 1).sum();
        long runningCleanupTasks = cleanupTasks.values().stream().mapToLong(t -> t.isCancelled() ? 0 : 1).sum();

        stats.put("runningPortTasks", runningPortTasks);
        stats.put("runningOnlineTasks", runningOnlineTasks);
        stats.put("runningLatencyTasks", runningLatencyTasks);
        stats.put("runningCleanupTasks", runningCleanupTasks);
        stats.put("totalRunningTasks", runningPortTasks + runningOnlineTasks + runningLatencyTasks + runningCleanupTasks);

        return stats;
    }

    /**
     * 清理资源
     */
    public void shutdown() {
        log.info("关闭统一定时任务管理服务");

        // 停止所有服务器任务
        portCheckTasks.keySet().forEach(this::stopPortCheckTask);
        onlineCheckTasks.keySet().forEach(this::stopOnlineCheckTask);
        latencyCheckTasks.keySet().forEach(this::stopLatencyCheckTask);

        // 停止清理任务
        stopCleanupTasks();

        // 关闭线程池
        shutdownExecutor(portCheckScheduler, "端口检测");
        shutdownExecutor(onlineCheckScheduler, "在线检测");
        shutdownExecutor(latencyCheckScheduler, "延迟检测");
        shutdownExecutor(cleanupScheduler, "清理任务");
    }

    /**
     * 关闭线程池
     */
    private void shutdownExecutor(ScheduledExecutorService executor, String name) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("{}线程池强制关闭", name);
            } else {
                log.info("{}线程池正常关闭", name);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("{}线程池关闭被中断", name);
        }
    }
}
