package com.chua.payment.support.dto;

import lombok.Data;

/**
 * 订单分表配置DTO
 *
 * @author CH
 * @since 4.0.0
 */
@Data
public class OrderPartitionConfigDTO {
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
     * 备注
     */
    private String remark;
}
