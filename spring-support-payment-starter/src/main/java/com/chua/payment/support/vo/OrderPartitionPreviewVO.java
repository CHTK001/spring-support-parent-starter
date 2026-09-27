package com.chua.payment.support.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单分表预览视图对象
 *
 * @author CH
 * @since 4.0.0
 */
@Data
public class OrderPartitionPreviewVO {
    /**
     * 业务类型
     */
    private String businessType;
    /**
     * 来源表
     */
    private String sourceTable;
    /**
     * 下一个分区表
     */
    private String nextPartitionTable;
    /**
     * 迁移目标表
     */
    private String migrateTargetTable;
    /**
     * 迁移前时间
     */
    private LocalDateTime migrateBeforeTime;
    /**
     * 创建任务密钥
     */
    private String createTaskKey;
    /**
     * 迁移任务密钥
     */
    private String migrateTaskKey;
}
