package com.chua.payment.support.vo;

import lombok.Data;

import java.time.LocalDateTime;
/** @author CH */

@Data
public class OrderPartitionConfigVO {
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
    /** Create任务密钥 */
    private String createTaskKey;
    /** Migrate任务密钥 */
    private String migrateTaskKey;
    /** 最后partition表 */
    private String lastPartitionTable;
    /** 最后partitionAT */
    private LocalDateTime lastPartitionAt;
    /** 最后migrateAT */
    private LocalDateTime lastMigrateAt;
    /** Remark */
    private String remark;
}

