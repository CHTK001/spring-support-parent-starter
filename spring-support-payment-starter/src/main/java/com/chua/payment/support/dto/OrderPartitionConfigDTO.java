package com.chua.payment.support.dto;

import lombok.Data;

@Data
public class OrderPartitionConfigDTO {
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
    private String remark;
}
