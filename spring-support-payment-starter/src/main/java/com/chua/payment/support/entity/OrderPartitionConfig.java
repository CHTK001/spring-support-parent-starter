package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
/** @author CH */

@Data
@TableName("pay_partition_config")
public class OrderPartitionConfig {
    @TableId(value = "pay_partition_config_id", type = IdType.AUTO)
    /** ID */
    private Long id;

    @TableField("pay_partition_config_business_type")
    /** Business类型 */
    private String businessType;

    @TableField("pay_partition_config_source_table")
    /** 来源表 */
    private String sourceTable;

    @TableField("pay_partition_config_partition_prefix")
    /** Partitionprefix */
    private String partitionPrefix;

    @TableField("pay_partition_config_partition_granularity")
    /** Partitiongranularity */
    private String partitionGranularity;

    @TableField("pay_partition_config_retention_days")
    /** Retentiondays */
    private Integer retentionDays;

    @TableField("pay_partition_config_create_ahead_days")
    /** Createaheaddays */
    private Integer createAheadDays;

    @TableField("pay_partition_config_migrate_before_days")
    /** Migratebeforedays */
    private Integer migrateBeforeDays;

    @TableField("pay_partition_config_auto_create_enabled")
    /** Autocreate是否启用 */
    private Boolean autoCreateEnabled;

    @TableField("pay_partition_config_auto_migrate_enabled")
    /** Automigrate是否启用 */
    private Boolean autoMigrateEnabled;

    @TableField("pay_partition_config_keep_source_data")
    /** Keep来源数据 */
    private Boolean keepSourceData;

    @TableField("pay_partition_config_migration_dry_run")
    /** MigrationDRYRUN */
    private Boolean migrationDryRun;

    @TableField("pay_partition_config_time_column")
    /** 表时间列 */
    private String tableTimeColumn;

    @TableField("pay_partition_config_id_column")
    /** 表ID列 */
    private String tableIdColumn;

    @TableField("pay_partition_config_create_task_key")
    /** Create任务密钥 */
    private String createTaskKey;

    @TableField("pay_partition_config_migrate_task_key")
    /** Migrate任务密钥 */
    private String migrateTaskKey;

    @TableField("pay_partition_config_last_partition_table")
    /** 最后partition表 */
    private String lastPartitionTable;

    @TableField("pay_partition_config_last_partition_at")
    /** 最后partitionAT */
    private LocalDateTime lastPartitionAt;

    @TableField("pay_partition_config_last_migrate_at")
    /** 最后migrateAT */
    private LocalDateTime lastMigrateAt;

    @TableField("pay_partition_config_remark")
    /** Remark */
    private String remark;

    @TableField(value = "pay_partition_config_created_at", fill = FieldFill.INSERT)
    /** 创建时间AT */
    private LocalDateTime createdAt;

    @TableField(value = "pay_partition_config_updated_at", fill = FieldFill.INSERT_UPDATE)
    /** 更新时间AT */
    private LocalDateTime updatedAt;
}

