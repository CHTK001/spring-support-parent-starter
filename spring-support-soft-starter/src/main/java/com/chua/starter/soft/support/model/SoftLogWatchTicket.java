package com.chua.starter.soft.support.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SoftLogWatchTicket {
    private Long watchId;
    private Integer installationId;
    private String logPath;
    private LocalDateTime acceptedAt;
}
