package com.chua.starter.job.support;

/**
 * Job 物理表初始化策略。
 * <p>
 * 该配置只负责表结构初始化，不影响运行时轮询和触发逻辑。
 * </p>
 */
public enum JobSchemaInitMode {

    /**
     * 不执行任何 DDL。
     */
    NONE,

    /**
     * 仅在表不存在时创建表。
     */
    CREATE,

    /**
     * 按实体结构更新表。
     */
    UPDATE,

    /**
     * 先删后建。
     */
    DROP_CREATE;

    public boolean isEnabled() {
        return this != NONE;
    }
}
