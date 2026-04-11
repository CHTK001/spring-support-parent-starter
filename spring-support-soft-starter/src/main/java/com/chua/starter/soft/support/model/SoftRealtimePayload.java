package com.chua.starter.soft.support.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SoftRealtimePayload {
    private Integer operationId;
    private Integer installationId;
    private String operationType;
    private String status;
    private String stage;
    private Integer progressPercent;
    private String message;
    private String detail;
    private String line;
    private String targetType;
    private String packageCode;
    private String versionCode;
    private boolean finished;
}
