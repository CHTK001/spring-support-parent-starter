package com.chua.starter.server.support.service.impl;

import com.chua.starter.server.support.config.ServerManagementProperties;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerExposurePortMeta;
import com.chua.starter.server.support.model.ServerExposurePortView;
import com.chua.starter.server.support.model.ServerExposureSummary;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.service.ServerExposureService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerMetricsService;
import com.chua.starter.server.support.util.ServerPortInspectionSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerExposureServiceImpl implements ServerExposureService {

    private static final String METADATA_KEY = "exposureScan";

    private final ServerHostService serverHostService;
    private final ServerMetricsService serverMetricsService;
    private final ServerManagementProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public ServerExposureSummary getSummary(Integer serverId) {
        ServerHost host = serverHostService.getHost(serverId);
        if (host == null) {
            return null;
        }
        ServerExposureSummary summary = readSummary(host);
        if (shouldRefresh(summary)) {
            summary = scanHost(host);
            writeSummary(host, summary);
        }
        return normalizeSummary(summary);
    }

    @Override
    public ServerExposureSummary refresh(Integer serverId) {
        ServerHost host = serverHostService.getHost(serverId);
        if (host == null) {
            return null;
        }
        ServerExposureSummary summary = scanHost(host);
        writeSummary(host, summary);
        return normalizeSummary(summary);
    }

    @Override
    public List<ServerExposurePortView> listPorts(Integer serverId, boolean refresh) {
        return listPorts(serverId, refresh, null, null, null, null);
    }

    @Override
    public List<ServerExposurePortView> listPorts(
            Integer serverId,
            boolean refresh,
            String keyword,
            String protocol,
            String state,
            Integer limit
    ) {
        ServerExposureSummary summary = refresh ? refresh(serverId) : getSummary(serverId);
        if (summary == null || summary.getPorts() == null) {
            return List.of();
        }
        return filterPorts(summary.getPorts(), keyword, protocol, state, limit);
    }

    @Override
    public ServerExposurePortMeta portMeta(Integer serverId, boolean refresh) {
        List<ServerExposurePortView> ports = listPorts(serverId, refresh);
        TreeSet<String> protocols = new TreeSet<>();
        TreeSet<String> states = new TreeSet<>();
        TreeSet<String> processNames = new TreeSet<>();
        for (ServerExposurePortView item : ports) {
            if (item == null) {
                continue;
            }
            if (StringUtils.hasText(item.getProtocol())) {
                protocols.add(item.getProtocol().trim().toLowerCase(Locale.ROOT));
            }
            if (StringUtils.hasText(item.getState())) {
                states.add(item.getState().trim().toUpperCase(Locale.ROOT));
            }
            if (StringUtils.hasText(item.getProcessName())) {
                processNames.add(item.getProcessName().trim());
            }
        }
        return ServerExposurePortMeta.builder()
                .total(ports.size())
                .protocols(new ArrayList<>(protocols))
                .states(new ArrayList<>(states))
                .processNames(new ArrayList<>(processNames))
                .build();
    }

    /**
     * 定时刷新所有启用服务器的监听端口快照。
     */
    @Scheduled(
            fixedDelayString = "${plugin.server.exposure.refresh-interval-ms:900000}",
            initialDelayString = "${plugin.server.exposure.initial-delay-ms:180000}")
    public void scheduledExposureScan() {
        if (!properties.getExposure().isEnable()) {
            return;
        }
        for (ServerHost host : serverHostService.listHosts(null, null, Boolean.TRUE)) {
            try {
                ServerExposureSummary summary = scanHost(host);
                writeSummary(host, summary);
            } catch (Exception e) {
                log.warn("端口暴露分析失败, serverId={}, message={}", host.getServerId(), e.getMessage());
            }
        }
    }

    private ServerExposureSummary scanHost(ServerHost host) {
        long start = System.currentTimeMillis();
        ServerMetricsDetail detail = serverMetricsService.getDetail(host.getServerId());
        List<ServerExposurePortView> ports = detail == null || detail.getListeningPorts() == null
                ? ServerPortInspectionSupport.inspect(host)
                : detail.getListeningPorts();
        ports = applyPolicy(ports);
        return ServerExposureSummary.builder()
                .serverId(host.getServerId())
                .serverCode(host.getServerCode())
                .host(host.getHost())
                .targetHost(resolveTargetHost(host))
                .actualOsName(detail == null ? host.getOsType() : detail.getActualOsName())
                .scannedAt(System.currentTimeMillis())
                .duration(Math.max(System.currentTimeMillis() - start, 0L))
                .totalPorts(ports.size())
                .openPorts(ports.size())
                .ports(ports)
                .build();
    }

    private String resolveTargetHost(ServerHost host) {
        if (host == null || !StringUtils.hasText(host.getHost()) || "LOCAL".equalsIgnoreCase(host.getServerType())) {
            return "127.0.0.1";
        }
        return host.getHost().trim();
    }

    private void writeSummary(ServerHost host, ServerExposureSummary summary) {
        if (host == null || host.getServerId() == null || summary == null) {
            return;
        }
        try {
            ObjectNode metadata = parseMetadata(host.getMetadataJson());
            metadata.set(METADATA_KEY, objectMapper.valueToTree(summary));
            host.setMetadataJson(objectMapper.writeValueAsString(metadata));
            serverHostService.saveHost(host);
        } catch (Exception e) {
            log.warn("写入端口暴露分析结果失败, serverId={}, message={}", host.getServerId(), e.getMessage());
        }
    }

    private ServerExposureSummary readSummary(ServerHost host) {
        if (host == null || !StringUtils.hasText(host.getMetadataJson())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(host.getMetadataJson());
            JsonNode node = root.path(METADATA_KEY);
            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
            ServerExposureSummary summary = objectMapper.convertValue(node, ServerExposureSummary.class);
            if (summary != null && summary.getPorts() == null) {
                summary.setPorts(new ArrayList<>());
            }
            return summary;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ObjectNode parseMetadata(String metadataJson) {
        try {
            JsonNode node = StringUtils.hasText(metadataJson)
                    ? objectMapper.readTree(metadataJson)
                    : objectMapper.createObjectNode();
            if (node instanceof ObjectNode objectNode) {
                return objectNode;
            }
        } catch (Exception ignored) {
        }
        return objectMapper.createObjectNode();
    }

    private boolean shouldRefresh(ServerExposureSummary summary) {
        if (summary == null || summary.getScannedAt() == null || summary.getScannedAt() <= 0L) {
            return true;
        }
        long refreshIntervalMs = Math.max(properties.getExposure().getRefreshIntervalMs(), 60000L);
        return System.currentTimeMillis() - summary.getScannedAt() >= refreshIntervalMs;
    }

    private ServerExposureSummary normalizeSummary(ServerExposureSummary summary) {
        if (summary == null) {
            return null;
        }
        List<ServerExposurePortView> ports = applyPolicy(summary.getPorts());
        summary.setPorts(ports);
        summary.setTotalPorts(ports.size());
        summary.setOpenPorts(ports.size());
        return summary;
    }

    private List<ServerExposurePortView> applyPolicy(List<ServerExposurePortView> ports) {
        return ServerPortInspectionSupport.applyPolicy(
                ports,
                properties.getExposure().isIncludeUdp(),
                properties.getExposure().isIncludeLoopback(),
                properties.getExposure().getMaxPorts());
    }

    private List<ServerExposurePortView> filterPorts(
            List<ServerExposurePortView> source,
            String keyword,
            String protocol,
            String state,
            Integer limit
    ) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        String keywordValue = normalize(keyword);
        String protocolValue = normalize(protocol);
        String stateValue = normalize(state);
        int max = limit == null || limit <= 0
                ? Integer.MAX_VALUE
                : Math.min(Math.max(limit, 1), 2000);
        List<ServerExposurePortView> result = new ArrayList<>();
        for (ServerExposurePortView item : source) {
            if (item == null) {
                continue;
            }
            if (StringUtils.hasText(protocolValue)
                    && !protocolValue.equals(normalize(item.getProtocol()))) {
                continue;
            }
            if (StringUtils.hasText(stateValue)
                    && !stateValue.equals(normalize(item.getState()))) {
                continue;
            }
            if (StringUtils.hasText(keywordValue) && !containsKeyword(item, keywordValue)) {
                continue;
            }
            result.add(item);
            if (result.size() >= max) {
                break;
            }
        }
        return result;
    }

    private boolean containsKeyword(ServerExposurePortView item, String keyword) {
        return contains(item.getServiceName(), keyword)
                || contains(item.getProcessName(), keyword)
                || contains(item.getLocalAddress(), keyword)
                || contains(item.getProtocol(), keyword)
                || contains(item.getState(), keyword)
                || String.valueOf(item.getPort()).contains(keyword)
                || (item.getTags() != null && item.getTags().stream().anyMatch(tag -> contains(tag, keyword)));
    }

    private boolean contains(String value, String keyword) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return normalize(value).contains(keyword);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }
}
