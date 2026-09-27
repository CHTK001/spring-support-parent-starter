package com.chua.payment.support.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单分表配置视图对象
 *
 * @author CH
 * @since 4.0.0
 */
@Data
public class OrderPartitionConfigVO {
    /**
     * 业务类型
     */
    private String businessType;
    /**
     * 来源表
     */
    private String sourceTable;
    /**
     * 分表前缀
     */
    private String partitionPrefix;
    /**
     * 分表粒度
     */
    private String partitionGranularity;
    /**
     * 保留天数
     */
    private Integer retentionDays;
    /**
     * 提前创建天数
     */
    private Integer createAheadDays;
    /**
     * 迁移前保留天数
     */
    private Integer migrateBeforeDays;
    /**
     * 自动创建是否启用
     */
    private Boolean autoCreateEnabled;
    /**
     * 自动迁移是否启用
     */
    private Boolean autoMigrateEnabled;
    /**
     * 保留来源数据
     */
    private Boolean keepSourceData;
    /**
     * 迁移演练模式
     */
    private Boolean migrationDryRun;
    /**
     * 表时间列
     */
    private String tableTimeColumn;
    /**
     * 表标识列
     */
    private String tableIdColumn;
    /**
     * 创建任务密钥
     */
    private String createTaskKey;
    /**
     * 迁移任务密钥
     */
    private String migrateTaskKey;
    /**
     * 最后分区表
     */
    private String lastPartitionTable;
    /**
     * 最后分区时间
     */
    private LocalDateTime lastPartitionAt;
    /**
     * 最后迁移时间
     */
    private LocalDateTime lastMigrateAt;
    /**
     * 备注
     */
    private String remark;
}
