package com.chua.starter.monitor.server.consumer;

import com.chua.common.support.json.Json;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.router.Router;
import io.zbus.mq.Broker;
import io.zbus.mq.Consumer;
import io.zbus.mq.ConsumerConfig;

import java.io.IOException;

/**
 * 监控消费者
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class MonitorConsumer implements AutoCloseable{
    private final Consumer consumer;

    public MonitorConsumer(Router router, Broker broker, String subscribe) {
        ConsumerConfig config = new ConsumerConfig();
        config.setBroker(broker);
        config.setTopic(subscribe);
        this.consumer = new Consumer(config);
        try {
            consumer.start((msg, consumer) -> {
                MonitorRequest monitorRequest = null;
                try {
                    monitorRequest = Json.fromJson(msg.getBody(), MonitorRequest.class);
                } catch (Exception ignored) {
                    return;
                }
                if(null == monitorRequest) {
                    return;
                }
                router.doRoute(monitorRequest);
            });
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() throws Exception {
        IoUtils.closeQuietly(consumer);
    }
}
