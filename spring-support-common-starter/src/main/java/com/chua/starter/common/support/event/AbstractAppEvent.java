package com.chua.starter.common.support.event;

import org.springframework.context.ApplicationEvent;

/**
 * event
 *
 * @author CH
 * @since 2022/7/30 11:40
 */
public abstract class AbstractAppEvent<T> extends ApplicationEvent {


    private final String dataId;

    private final String groupId;

    /**
     * @param entity  实体
     * @param dataId  data ID
     * @param groupId group ID
     */
    public AbstractAppEvent(T entity, String dataId, String groupId) {
        super(entity);
        this.dataId = dataId;
        this.groupId = groupId;
    }


    public final String getDataId() {
        return dataId;
    }

    public final String getGroupId() {
        return groupId;
    }
}
