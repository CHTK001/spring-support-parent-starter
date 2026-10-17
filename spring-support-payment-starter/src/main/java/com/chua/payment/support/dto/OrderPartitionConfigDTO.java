package com.chua.payment.support.dto;

/**
 * 订单分表配置DTO
 *
 * @param businessType         业务类型
 * @param sourceTable          来源表
 * @param partitionPrefix      分表前缀
 * @param partitionGranularity 分表粒度
 * @param retentionDays        保留天数
 * @param createAheadDays      提前创建天数
 * @param migrateBeforeDays    迁移前保留天数
 * @param autoCreateEnabled    自动创建是否启用
 * @param autoMigrateEnabled   自动迁移是否启用
 * @param keepSourceData       保留来源数据
 * @param migrationDryRun      迁移演练模式
 * @param tableTimeColumn      表时间列
 * @param tableIdColumn        表标识列
 * @param remark               备注
 * @author CH
 * @since 4.0.0
 */
public record OrderPartitionConfigDTO(
        String businessType,
        String sourceTable,
        String partitionPrefix,
        String partitionGranularity,
        Integer retentionDays,
        Integer createAheadDays,
        Integer migrateBeforeDays,
        Boolean autoCreateEnabled,
        Boolean autoMigrateEnabled,
        Boolean keepSourceData,
        Boolean migrationDryRun,
        String tableTimeColumn,
        String tableIdColumn,
        String remark) {
}
