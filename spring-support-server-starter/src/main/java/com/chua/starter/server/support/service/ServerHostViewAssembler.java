package com.chua.starter.server.support.service;

import com.chua.starter.server.support.entity.ServerHost;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ServerHostViewAssembler {

    private final ServerMetricsService serverMetricsService;
    private final ServerGuacamoleService serverGuacamoleService;

    public ServerHost enrich(ServerHost host) {
        if (host == null) {
            return null;
        }
        host.setTagsList(splitTags(host.getTags()));
        if (host.getServerId() != null) {
            host.setStatusSnapshot(serverMetricsService.getSnapshot(host.getServerId()));
        }
        host.setGuacamoleConfig(serverGuacamoleService.buildConfig(host));
        host.setRemoteGatewayConfig(host.getGuacamoleConfig());
        return host;
    }

    public List<ServerHost> enrich(List<ServerHost> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return Collections.emptyList();
        }
        return hosts.stream().map(this::enrich).collect(Collectors.toList());
    }

    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }
}
