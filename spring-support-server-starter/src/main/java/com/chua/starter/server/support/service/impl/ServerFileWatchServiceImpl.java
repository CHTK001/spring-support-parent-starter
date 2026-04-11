package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.config.ServerManagementProperties;
import com.chua.starter.server.support.constants.ServerSocketEvents;
import com.chua.starter.server.support.model.ServerFileContent;
import com.chua.starter.server.support.model.ServerFileWatchTicket;
import com.chua.starter.server.support.model.ServerRealtimePayload;
import com.chua.starter.server.support.service.ServerFileService;
import com.chua.starter.server.support.service.ServerFileWatchService;
import com.chua.starter.server.support.service.ServerRealtimePublisher;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ServerFileWatchServiceImpl implements ServerFileWatchService {

    private final ServerFileService serverFileService;
    private final ServerRealtimePublisher serverRealtimePublisher;
    private final ServerManagementProperties properties;

    private final AtomicLong watchIdGenerator = new AtomicLong(1L);
    private final Map<Long, WatchState> watches = new ConcurrentHashMap<>();

    @Override
    public ServerFileWatchTicket createWatch(Integer serverId, String path) throws Exception {
        ServerFileContent content = serverFileService.readContent(
                serverId,
                path,
                properties.getFileWatch().getMaxReadBytes());
        long watchId = watchIdGenerator.getAndIncrement();
        watches.put(watchId, new WatchState(serverId, content.getPath(), content.getContent()));
        return ServerFileWatchTicket.builder()
                .watchId(watchId)
                .serverId(serverId)
                .path(content.getPath())
                .acceptedAt(System.currentTimeMillis())
                .build();
    }

    @Override
    public boolean stopWatch(Integer serverId, Long watchId) {
        WatchState removed = watches.remove(watchId);
        return removed != null && (serverId == null || serverId.equals(removed.serverId));
    }

    @Scheduled(fixedDelayString = "${plugin.server.file-watch.poll-interval-ms:2000}")
    public void pollWatchers() {
        if (!properties.getFileWatch().isEnable()) {
            return;
        }
        for (Map.Entry<Long, WatchState> entry : watches.entrySet()) {
            Long watchId = entry.getKey();
            WatchState state = entry.getValue();
            try {
                ServerFileContent current = serverFileService.readContent(
                        state.serverId,
                        state.path,
                        properties.getFileWatch().getMaxReadBytes());
                String delta = extractDelta(state.lastContent, current.getContent());
                state.path = current.getPath();
                state.lastContent = current.getContent();
                if (!StringUtils.hasText(delta)) {
                    continue;
                }
                for (String line : delta.split("\\R")) {
                    if (!StringUtils.hasText(line)) {
                        continue;
                    }
                    serverRealtimePublisher.publish(
                            ServerSocketEvents.MODULE,
                            ServerSocketEvents.FILE_LOG,
                            watchId,
                            ServerRealtimePayload.builder()
                                    .serverId(state.serverId)
                                    .watchId(watchId)
                                    .path(state.path)
                                    .line(line)
                                    .message("文件日志已追加")
                                    .finished(false)
                                    .build());
                }
            } catch (Exception e) {
                serverRealtimePublisher.publish(
                        ServerSocketEvents.MODULE,
                        ServerSocketEvents.FILE_LOG,
                        watchId,
                        ServerRealtimePayload.builder()
                                .serverId(state.serverId)
                                .watchId(watchId)
                                .path(state.path)
                                .message(e.getMessage())
                                .finished(false)
                                .build());
            }
        }
    }

    private String extractDelta(String previous, String current) {
        if (!StringUtils.hasText(current)) {
            return "";
        }
        if (!StringUtils.hasText(previous)) {
            return current;
        }
        if (current.startsWith(previous)) {
            return current.substring(previous.length());
        }
        return current;
    }

    private static final class WatchState {
        private final Integer serverId;
        private String path;
        private String lastContent;

        private WatchState(Integer serverId, String path, String lastContent) {
            this.serverId = serverId;
            this.path = path;
            this.lastContent = lastContent;
        }
    }
}
