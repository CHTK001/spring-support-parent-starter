package com.chua.report.client.starter.event;


import com.chua.starter.common.support.event.AbstractAppEvent;
import lombok.Getter;

/**
 * event
 *
 * @author CH
 * @since 2022/7/30 11:42
 */
@Getter
public class ConfigValueReceivedEvent<T> extends AbstractAppEvent<T> {

    /**
     * -- GETTER --
     *  Get Content of published Nacos Configuration
     *
     * @return content
     */
    private final String content;
    private final String type;

    public ConfigValueReceivedEvent(T entity, String dataId,
                                    String groupId, String content, String type) {
        super(entity, dataId, groupId);
        this.content = content;
        this.type = type;
    }

}
