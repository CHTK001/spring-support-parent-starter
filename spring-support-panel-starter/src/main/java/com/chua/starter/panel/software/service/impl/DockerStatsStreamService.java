package com.chua.starter.panel.software.service.impl;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.panel.service.DockerPanelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Docker 容器性能统计流服务。
 * 每 5 秒采集容器 CPU/内存/网络统计，通过 WebSocket + {@link SocketSessionTemplate} 实时推送到前端。
 *
 * @author CH
 */
@Component
public class DockerStatsStreamService {

    private static final Logger log = LoggerFactory.getLogger(DockerStatsStreamService.class);
    private static final long POLL_INTERVAL_MS = 5000;
    private static final String MODULE = "DOCKER";

    private final DockerPanelService dockerPanelService;
    private final ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider;
    private final ConcurrentHashMap<String, StatsStreamTask> activeStreams = new ConcurrentHashMap<>();

    public DockerStatsStreamService(DockerPanelService dockerPanelService,
                                    ObjectProvider<SocketSessionTemplate> socketSessionTemplateProvider) {
        this.dockerPanelService = dockerPanelService;
        this.socketSessionTemplateProvider = socketSessionTemplateProvider;
    }

    /**
     * 开始推送性能统计。
     *
     * @param connectionId  Docker 连接 ID
     * @param containerName 容器名称/ID
     * @param bindingId     绑定 ID
     */
    public void startStream(String connectionId, String containerName, Integer bindingId) {
        String key = String.valueOf(bindingId);
        stopStream(bindingId);

        StatsStreamTask task = new StatsStreamTask(connectionId, containerName, bindingId);
        Thread thread = new Thread(task, "docker-stats-stream-" + key);
        thread.setDaemon(true);
        thread.start();
        activeStreams.put(key, task);
    }

    /**
     * 停止性能统计推送。
     *
     * @param bindingId 绑定 ID
     */
    public void stopStream(Integer bindingId) {
        String key = String.valueOf(bindingId);
        StatsStreamTask task = activeStreams.remove(key);
        if (task != null) {
            task.stop();
        }
    }

    private class StatsStreamTask implements Runnable {
        private final String connectionId;
        private final String containerName;
        private final Integer bindingId;
        private volatile boolean running = true;
        private String lastStatsJson = "";

        StatsStreamTask(String connectionId, String containerName, Integer bindingId) {
            this.connectionId = connectionId;
            this.containerName = containerName;
            this.bindingId = bindingId;
        }

        void stop() {
            running = false;
        }

        @Override
        public void run() {
            // Warmup delay before first poll
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                return;
            }

            while (running) {
                try {
                    Map<String, Object> stats = dockerPanelService.getContainerStats(connectionId, containerName);
                    if (stats != null && !stats.isEmpty()) {
                        stats.put("bindingId", bindingId);
                        stats.put("containerName", containerName);

                        // Skip push if nothing changed (avoid no-op updates)
                        String currentJson = stats.toString();
                        if (currentJson.equals(lastStatsJson)) {
                            Thread.sleep(POLL_INTERVAL_MS);
                            continue;
                        }
                        lastStatsJson = currentJson;

                        SocketSessionTemplate template = socketSessionTemplateProvider.getIfAvailable();
                        if (template != null) {
                            Map<String, Object> envelope = new LinkedHashMap<>();
                            envelope.put("module", MODULE);
                            envelope.put("event", "docker.container.statistics");
                            envelope.put("dataId", String.valueOf(bindingId));
                            envelope.put("data", stats);
                            envelope.put("timestamp", System.currentTimeMillis());
                            template.broadcastObject("message", envelope);
                        }
                    }

                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.debug("统计流轮询异常 (bindingId={}): {}", bindingId, e.getMessage());
                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
}
