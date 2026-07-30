package com.chua.starter.server.support.service;

import com.chua.starter.server.support.model.ServerRemoteGatewaySettings;

public interface ServerRemoteGatewaySettingsService {

    ServerRemoteGatewaySettings getGlobalSettings();

    ServerRemoteGatewaySettings saveGlobalSettings(ServerRemoteGatewaySettings settings);

    ServerRemoteGatewaySettings getHostSettings(Integer serverId);

    ServerRemoteGatewaySettings saveHostSettings(Integer serverId, ServerRemoteGatewaySettings settings);
}
