package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pay_partition_config")
public class OrderPartitionConfig {
    @TableId(value = "pay_partition_config_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_partition_config_business_type")
    private String businessType;

    @TableField("pay_partition_config_source_table")
    private String sourceTable;

    @TableField("pay_partition_config_partition_prefix")
    private String partitionPrefix;

    @TableField("pay_partition_config_partition_granularity")
    private String partitionGranularity;

    @TableField("pay_partition_config_retention_days")
    private Integer retentionDays;

    @TableField("pay_partition_config_create_ahead_days")
    private Integer createAheadDays;

    @TableField("pay_partition_config_migrate_before_days")
    private Integer migrateBeforeDays;

    @TableField("pay_partition_config_auto_create_enabled")
    private Boolean autoCreateEnabled;

    @TableField("pay_partition_config_auto_migrate_enabled")
    private Boolean autoMigrateEnabled;

    @TableField("pay_partition_config_keep_source_data")
    private Boolean keepSourceData;

    @TableField("pay_partition_config_migration_dry_run")
    private Boolean migrationDryRun;

    @TableField("pay_partition_config_time_column")
    private String tableTimeColumn;

    @TableField("pay_partition_config_id_column")
    private String tableIdColumn;

    @TableField("pay_partition_config_create_task_key")
    private String createTaskKey;

    @TableField("pay_partition_config_migrate_task_key")
    private String migrateTaskKey;

    @TableField("pay_partition_config_last_partition_table")
    private String lastPartitionTable;

    @TableField("pay_partition_config_last_partition_at")
    private LocalDateTime lastPartitionAt;

    @TableField("pay_partition_config_last_migrate_at")
    private LocalDateTime lastMigrateAt;

    @TableField("pay_partition_config_remark")
    private String remark;

    @TableField(value = "pay_partition_config_created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "pay_partition_config_updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
