package com.chua.payment.support.vo;

import lombok.Data;

import java.time.LocalDateTime;
/** @author CH */

@Data
public class OrderPartitionPreviewVO {
    /** Business类型 */
    private String businessType;
    /** 来源表 */
    private String sourceTable;
    /** 下一个partition表 */
    private String nextPartitionTable;
    /** Migrate目标表 */
    private String migrateTargetTable;
    /** Migratebefore时间 */
    private LocalDateTime migrateBeforeTime;
    /** Create任务密钥 */
    private String createTaskKey;
    /** Migrate任务密钥 */
    private String migrateTaskKey;
}

