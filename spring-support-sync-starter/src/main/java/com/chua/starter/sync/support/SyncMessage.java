package com.chua.starter.sync.support;

/**
 * 同步消息，封装主题和消息体。
 *
 * @author CH
 * @since 4.0.0.41
 */
public class SyncMessage {

    private final String topic;

    private final Object data;

    public SyncMessage(String topic, Object data) {
        this.topic = topic;
        this.data = data;
    }

    public String getTopic() {
        return topic;
    }

    public Object getData() {
        return data;
    }
}