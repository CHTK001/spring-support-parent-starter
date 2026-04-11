package com.chua.starter.soft.support.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SoftRealtimeEnvelope {
    private String module;
    private String event;
    private Object dataId;
    private SoftRealtimePayload data;
    private long timestamp;
}
