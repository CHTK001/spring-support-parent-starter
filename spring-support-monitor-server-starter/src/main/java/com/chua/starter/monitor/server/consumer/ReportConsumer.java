package com.chua.starter.monitor.server.consumer;

import com.chua.common.support.json.Json;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.router.Router;
import org.zbus.broker.Broker;
import org.zbus.mq.Consumer;
import org.zbus.mq.MqConfig;

import java.io.IOException;

/**
 * 监控消费者
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class ReportConsumer implements AutoCloseable{
    private final Consumer consumer;

    public ReportConsumer(Router router, Broker broker, String subscribe) {
        MqConfig config = new MqConfig();
        config.setBroker(broker);
        config.setMq(subscribe + "#report");
        this.consumer = new Consumer(config);
        try {
            consumer.start((msg, consumer) -> {
                MonitorRequest monitorRequest = Json.fromJson(msg.getBody(), MonitorRequest.class);
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
