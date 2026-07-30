package com.chua.payment.support.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderPartitionPreviewVO {
    private String businessType;
    private String sourceTable;
    private String nextPartitionTable;
    private String migrateTargetTable;
    private LocalDateTime migrateBeforeTime;
    private String createTaskKey;
    private String migrateTaskKey;
}
