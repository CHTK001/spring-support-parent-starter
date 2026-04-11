package com.chua.starter.server.support.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerGuacamoleConfig {
    private Boolean enabled;
    private String provider;
    private String protocol;
    private String gatewayUrl;
    private String websocketUrl;
    private String launchUrl;
    private String connectionId;
    private String message;
    private Map<String, String> parameters;
}
