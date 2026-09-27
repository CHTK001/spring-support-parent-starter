package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单分表配置实体类
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("pay_partition_config")
public class OrderPartitionConfig {
    @TableId(value = "pay_partition_config_id", type = IdType.AUTO)
    /**
     * 标识
     */
    private Long id;

    @TableField("pay_partition_config_business_type")
    /**
     * 业务类型
     */
    private String businessType;

    @TableField("pay_partition_config_source_table")
    /**
     * 来源表
     */
    private String sourceTable;

    @TableField("pay_partition_config_partition_prefix")
    /**
     * 分表前缀
     */
    private String partitionPrefix;

    @TableField("pay_partition_config_partition_granularity")
    /**
     * 分表粒度
     */
    private String partitionGranularity;

    @TableField("pay_partition_config_retention_days")
    /**
     * 数据保留天数
     */
    private Integer retentionDays;

    @TableField("pay_partition_config_create_ahead_days")
    /**
     * 提前创建天数
     */
    private Integer createAheadDays;

    @TableField("pay_partition_config_migrate_before_days")
    /**
     * 迁移前置天数
     */
    private Integer migrateBeforeDays;

    @TableField("pay_partition_config_auto_create_enabled")
    /**
     * 是否自动创建分表
     */
    private Boolean autoCreateEnabled;

    @TableField("pay_partition_config_auto_migrate_enabled")
    /**
     * 是否自动迁移数据
     */
    private Boolean autoMigrateEnabled;

    @TableField("pay_partition_config_keep_source_data")
    /**
     * 是否保留来源数据
     */
    private Boolean keepSourceData;

    @TableField("pay_partition_config_migration_dry_run")
    /**
     * 迁移是否试运行
     */
    private Boolean migrationDryRun;

    @TableField("pay_partition_config_time_column")
    /**
     * 表时间列
     */
    private String tableTimeColumn;

    @TableField("pay_partition_config_id_column")
    /**
     * 表标识列
     */
    private String tableIdColumn;

    @TableField("pay_partition_config_create_task_key")
    /**
     * 创建任务密钥
     */
    private String createTaskKey;

    @TableField("pay_partition_config_migrate_task_key")
    /**
     * 迁移任务密钥
     */
    private String migrateTaskKey;

    @TableField("pay_partition_config_last_partition_table")
    /**
     * 最后分区表
     */
    private String lastPartitionTable;

    @TableField("pay_partition_config_last_partition_at")
    /**
     * 最后分区时间
     */
    private LocalDateTime lastPartitionAt;

    @TableField("pay_partition_config_last_migrate_at")
    /**
     * 最后迁移时间
     */
    private LocalDateTime lastMigrateAt;

    @TableField("pay_partition_config_remark")
    /**
     * 备注
     */
    private String remark;

    @TableField(value = "pay_partition_config_created_at", fill = FieldFill.INSERT)
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @TableField(value = "pay_partition_config_updated_at", fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
