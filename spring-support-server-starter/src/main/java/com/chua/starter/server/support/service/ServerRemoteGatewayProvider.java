package com.chua.starter.server.support.service;

import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerGuacamoleConfig;
import java.util.Map;

public interface ServerRemoteGatewayProvider {

    String getProvider();

    default boolean supports(String provider) {
        return getProvider().equalsIgnoreCase(String.valueOf(provider));
    }

    ServerGuacamoleConfig buildConfig(ServerHost host, Map<String, Object> metadata);
}
