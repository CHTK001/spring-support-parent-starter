//package com.chua.report.server.starter.consumer;
//
//import com.chua.common.support.json.Json;
//import com.chua.common.support.utils.IoUtils;
//import com.chua.mica.support.client.MicaClient;
//import com.chua.mica.support.client.session.MicaSession;
//import com.chua.report.client.starter.report.event.ReportEvent;
//import com.chua.report.server.starter.router.Router;
//import io.zbus.mq.Consumer;
//import io.zbus.mq.ConsumerConfig;
//
//import java.io.IOException;
//import java.util.LinkedList;
//import java.util.List;
//
///**
// * 监控消费者
// *
// * @author CH
// * @version 1.0.0
// * @since 2024/02/01
// */
//public class ReportConsumer implements AutoCloseable {
//    private final List<MicaClient> consumers = new LinkedList<>();
//
//    public ReportConsumer(Router router, MicaClient micaClient, String subscribe) {
//        for (int i = 0; i < 3; i++) {
//            MicaSession session = (MicaSession) micaClient.createSession("report" + i);
//            ConsumerConfig config = new ConsumerConfig();
//            config.setBroker(broker);
//            config.setTopic(subscribe );
//            Consumer consumer1 = new Consumer(config);
//            try {
//                consumer1.start((msg, consumer) -> {
//                    try {
//                        ReportEvent reportEvent = Json.fromJson(msg.getBody(), ReportEvent.class);
//                        router.doRoute(reportEvent);
//                    } catch (Exception ignored) {
//                    }
//                });
//                this.consumers.add(consumer1);
//            } catch (IOException ignored) {
//            }
//        }
//    }
//
//    @Override
//    public void close() throws Exception {
//        for (Consumer consumer : consumers) {
//            IoUtils.closeQuietly(consumer);
//        }
//    }
//}
