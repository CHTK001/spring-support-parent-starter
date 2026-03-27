package com.chua.starter.job.support;

import com.chua.common.support.data.query.ddl.ActionType;

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
    NONE(null),

    /**
     * 仅在表不存在时创建表。
     */
    CREATE(ActionType.CREATE),

    /**
     * 按实体结构更新表。
     */
    UPDATE(ActionType.UPDATE),

    /**
     * 先删后建。
     */
    DROP_CREATE(ActionType.DROP_CREATE);

    private final ActionType actionType;

    JobSchemaInitMode(ActionType actionType) {
        this.actionType = actionType;
    }

    public ActionType toActionType() {
        if (actionType == null) {
            throw new IllegalStateException("NONE 模式没有对应的 DDL 动作");
        }
        return actionType;
    }

    public boolean isEnabled() {
        return actionType != null;
    }
}
