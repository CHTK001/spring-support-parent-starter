package com.chua.starter.server.support.model;

import lombok.Data;

@Data
public class ServerMetricsTaskSettingsRequest {

    private Boolean inheritGlobal;

    private Boolean enabled;

    private Long refreshIntervalMs;

    private Integer timeoutMs;

    private Boolean cacheEnabled;

    private Long cacheTtlSeconds;
}
