package com.chua.starter.monitor.event;


import com.chua.starter.common.support.event.AbstractAppEvent;

/**
 * event
 *
 * @author CH
 * @since 2022/7/30 11:42
 */
public class ConfigValueReceivedEvent<T> extends AbstractAppEvent<T> {

    private final String content;
    private final String type;

    public ConfigValueReceivedEvent(T entity, String dataId,
                                    String groupId, String content, String type) {
        super(entity, dataId, groupId);
        this.content = content;
        this.type = type;
    }

    /**
     * Get Content of published Nacos Configuration
     *
     * @return content
     */
    public String getContent() {
        return content;
    }

    public String getType() {
        return type;
    }
}
