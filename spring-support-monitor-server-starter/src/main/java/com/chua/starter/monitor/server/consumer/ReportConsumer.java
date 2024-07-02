package com.chua.starter.monitor.server.consumer;

import com.chua.common.support.json.Json;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.router.Router;
import io.zbus.mq.Broker;
import io.zbus.mq.Consumer;
import io.zbus.mq.ConsumerConfig;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * 监控消费者
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class ReportConsumer implements AutoCloseable {
    private final List<Consumer> consumers = new LinkedList<>();

    public ReportConsumer(Router router, Broker broker, String subscribe) {
        for (int i = 0; i < 3; i++) {
            ConsumerConfig config = new ConsumerConfig();
            config.setBroker(broker);
            config.setTopic(subscribe + "#report");
            Consumer consumer1 = new Consumer(config);
            try {
                consumer1.start((msg, consumer) -> {
                    MonitorRequest monitorRequest = Json.fromJson(msg.getBody(), MonitorRequest.class);
                    router.doRoute(monitorRequest);
                });
                this.consumers.add(consumer1);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void close() throws Exception {
        for (Consumer consumer : consumers) {
            IoUtils.closeQuietly(consumer);
        }
    }
}
