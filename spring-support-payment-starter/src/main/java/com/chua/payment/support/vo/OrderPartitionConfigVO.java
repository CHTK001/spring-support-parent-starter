package com.chua.payment.support.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderPartitionConfigVO {
    private String businessType;
    private String sourceTable;
    private String partitionPrefix;
    private String partitionGranularity;
    private Integer retentionDays;
    private Integer createAheadDays;
    private Integer migrateBeforeDays;
    private Boolean autoCreateEnabled;
    private Boolean autoMigrateEnabled;
    private Boolean keepSourceData;
    private String createTaskKey;
    private String migrateTaskKey;
    private String lastPartitionTable;
    private LocalDateTime lastPartitionAt;
    private LocalDateTime lastMigrateAt;
    private String remark;
}
