package com.chua.payment.support.vo;

import java.time.LocalDateTime;

/**
 * 订单分表预览视图对象
 *
 * @param businessType       业务类型
 * @param sourceTable        来源表
 * @param nextPartitionTable 下一个分区表
 * @param migrateTargetTable 迁移目标表
 * @param migrateBeforeTime  迁移前时间
 * @param createTaskKey      创建任务密钥
 * @param migrateTaskKey     迁移任务密钥
 * @author CH
 * @since 4.0.0
 */
public record OrderPartitionPreviewVO(
        String businessType,
        String sourceTable,
        String nextPartitionTable,
        String migrateTargetTable,
        LocalDateTime migrateBeforeTime,
        String createTaskKey,
        String migrateTaskKey) {
}
