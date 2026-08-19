package com.chua.payment.support.dto;

import lombok.Data;
/** @author CH */

@Data
public class OrderPartitionConfigDTO {
    /** Business类型 */
    private String businessType;
    /** 来源表 */
    private String sourceTable;
    /** Partitionprefix */
    private String partitionPrefix;
    /** Partitiongranularity */
    private String partitionGranularity;
    /** Retentiondays */
    private Integer retentionDays;
    /** Createaheaddays */
    private Integer createAheadDays;
    /** Migratebeforedays */
    private Integer migrateBeforeDays;
    /** Autocreate是否启用 */
    private Boolean autoCreateEnabled;
    /** Automigrate是否启用 */
    private Boolean autoMigrateEnabled;
    /** Keep来源数据 */
    private Boolean keepSourceData;
    /** MigrationDRYRUN */
    private Boolean migrationDryRun;
    /** 表时间列 */
    private String tableTimeColumn;
    /** 表ID列 */
    private String tableIdColumn;
    /** Remark */
    private String remark;
}

