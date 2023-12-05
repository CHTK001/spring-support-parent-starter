package com.chua.starter.unified.client.support.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 统一事件
 *
 * @author CH
 */
@Getter
public class UnifiedEvent extends ApplicationEvent {

    private final Object source;

    private final String mode;
    private final String type;

    public UnifiedEvent(Object source, String mode,  String type) {
        super(source);
        this.source = source;
        this.mode = mode;
        this.type = type;
    }


}
