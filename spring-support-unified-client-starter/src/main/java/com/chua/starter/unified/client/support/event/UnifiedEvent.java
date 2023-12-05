package com.chua.starter.unified.client.support.event;

import lombok.Data;
import org.springframework.context.ApplicationEvent;

/**
 * 统一事件
 *
 * @author CH
 */
@Data
public class UnifiedEvent extends ApplicationEvent {

    private Object source;

    private String type;

    public UnifiedEvent(Object source, String type) {
        super(source);
        this.source = source;
        this.type = type;
    }


}
