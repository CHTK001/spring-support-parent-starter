package com.chua.starter.server.support.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServerRealtimeEnvelope {
    private String module;
    private String event;
    private Object dataId;
    private Object data;
    private long timestamp;
}
