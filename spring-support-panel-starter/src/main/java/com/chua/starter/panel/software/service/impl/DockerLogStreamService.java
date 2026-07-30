package com.chua.starter.panel.software.service.impl;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.panel.service.DockerPanelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Docker 容器日志流服务。
 * 轮询 Docker API 获取容器日志，通过 WebSocket + {@link SocketSessionTemplate} 实时推送到前端。
 *
 * @author CH
 */
@Component
public class DockerLogStreamService {

    private static final Logger log = LoggerFactory.getLogger(DockerLogStreamService.class);
    private static final long POLL_INTERVAL_MS = 2000;
    private static final int TAIL_LINES = 100;
    private static final String MODULE = "DOCKER";

    private final DockerPanelService dockerPanelService;
    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;
    private final ConcurrentHashMap<String, LogStreamTask> activeStreams = new ConcurrentHashMap<>();

    public DockerLogStreamService(DockerPanelService dockerPanelService,
                                  ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider) {
        this.dockerPanelService = dockerPanelService;
        this.socketSessionTemplateProvider = socketSessionTemplateProvider;
    }

    /**
     * 开始推送日志流。
     *
     * @param connectionId  Docker 连接 ID
     * @param containerName 容器名称/ID
     * @param bindingId     绑定 ID（用于 dataId）
     */
    public void startStream(String connectionId, String containerName, Integer bindingId) {
        String key = String.valueOf(bindingId);
        stopStream(bindingId);

        LogStreamTask task = new LogStreamTask(connectionId, containerName, bindingId);
        Thread thread = new Thread(task, "docker-log-stream-" + key);
        thread.setDaemon(true);
        thread.start();
        activeStreams.put(key, task);
    }

    /**
     * 停止日志流。
     *
     * @param bindingId 绑定 ID
     */
    public void stopStream(Integer bindingId) {
        String key = String.valueOf(bindingId);
        LogStreamTask task = activeStreams.remove(key);
        if (task != null) {
            task.stop();
        }
    }

    private class LogStreamTask implements Runnable {
        private final String connectionId;
        private final String containerName;
        private final Integer bindingId;
        private volatile boolean running = true;
        private String lastSnapshot = "";

        LogStreamTask(String connectionId, String containerName, Integer bindingId) {
            this.connectionId = connectionId;
            this.containerName = containerName;
            this.bindingId = bindingId;
        }

        void stop() {
            running = false;
        }

        @Override
        public void run() {
            // Wait for initial logs before starting to poll
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                return;
            }

            while (running) {
                try {
                    String currentLogs = dockerPanelService.containerLogs(connectionId, containerName, TAIL_LINES);
                    if (currentLogs == null) {
                        currentLogs = "";
                    }

                    String newContent = extractNewContent(lastSnapshot, currentLogs);
                    if (!newContent.isEmpty()) {
                        SocketSessionTemplate template = socketSessionTemplateProvider.getIfAvailable();
                        if (template != null) {
                            Map<String, Object> data = new HashMap<>(4);
                            data.put("line", newContent);
                            data.put("bindingId", bindingId);
                            data.put("containerName", containerName);
                            data.put("timestamp", System.currentTimeMillis());

                            Map<String, Object> envelope = new LinkedHashMap<>();
                            envelope.put("module", MODULE);
                            envelope.put("event", "docker.container.log");
                            envelope.put("dataId", String.valueOf(bindingId));
                            envelope.put("data", data);
                            envelope.put("timestamp", System.currentTimeMillis());
                            template.broadcastObject("message", envelope);
                        }
                        lastSnapshot = currentLogs;
                    }

                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.debug("日志流轮询异常 (bindingId={}): {}", bindingId, e.getMessage());
                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        /**
         * Compare old and new log text to extract newly appended content.
         */
        private String extractNewContent(String oldLogs, String newLogs) {
            if (oldLogs.isEmpty()) {
                return newLogs;
            }
            if (newLogs.isEmpty()) {
                return "";
            }
            // If the new logs end with the old logs, the new content is the prefix
            if (newLogs.endsWith(oldLogs) && newLogs.length() > oldLogs.length()) {
                return newLogs.substring(0, newLogs.length() - oldLogs.length());
            }
            // If old logs end with new logs, nothing new
            if (oldLogs.endsWith(newLogs)) {
                return "";
            }
            // Fallback: line-by-line diff
            String[] oldLines = oldLogs.split("\n", -1);
            String[] newLines = newLogs.split("\n", -1);
            if (newLines.length <= oldLines.length) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = oldLines.length; i < newLines.length; i++) {
                sb.append(newLines[i]);
                if (i < newLines.length - 1) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        }
    }
}
