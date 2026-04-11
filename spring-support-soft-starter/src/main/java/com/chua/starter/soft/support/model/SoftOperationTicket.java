package com.chua.starter.soft.support.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SoftOperationTicket {
    private Integer operationId;
    private Integer installationId;
    private String operationType;
    private String operationStatus;
    private LocalDateTime acceptedAt;
}
