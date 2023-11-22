package com.chua.starter.unified.server.support.service.uniform;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.sse.support.SseMessage;
import com.chua.starter.sse.support.SseTemplate;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.zbus.broker.Broker;
import org.zbus.broker.BrokerConfig;
import org.zbus.broker.SingleBroker;
import org.zbus.mq.Consumer;
import org.zbus.mq.MqConfig;
import org.zbus.mq.Protocol;
import org.zbus.mq.server.MqServer;
import org.zbus.mq.server.MqServerConfig;
import org.zbus.net.http.Message;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.Duration;

/**
 * 统一检测中心
 *
 * @author CH
 */
@Service
public class MqUniform implements Uniform, Consumer.ConsumerHandler, InitializingBean, DisposableBean {

    private static final String UNIFORM_APPLICATION_NAME = "applicationName";
    private static final String UNIFORM_MODE = "mode";
    private static final String UNIFORM_MESSAGE = "message";
    public static final String SUBSCRIBE_SSE = "unified-sse";
    MqServer mqServer;


    @Resource
    private UnifiedServerProperties unifiedServerProperties;
    private Consumer consumer;

    @Resource
    private SseTemplate sseTemplate;

    public MqUniform() {

    }

    public void start() {
        UnifiedServerProperties.EndpointOption endpoint = unifiedServerProperties.getEndpoint();
        if(StringUtils.isBlank(endpoint.getHost()) || null == endpoint.getPort()) {
            return;
        }
        try {
            BrokerConfig config = new BrokerConfig();
            config.setBrokerAddress(endpoint.getHost() + ":" + endpoint.getPort());
            Broker broker = new SingleBroker(config);
            MqConfig mqConfig = new MqConfig();
            mqConfig.setBroker(broker);
            mqConfig.setMode(Protocol.MqMode.MQ);
            mqConfig.setMq("unified");
            this.consumer = new Consumer(mqConfig);
            consumer.onMessage(this);
            consumer.start();
        } catch (Exception ignored) {
        }
    }

    public void stop() {
        try {
            mqServer.close();
        } catch (IOException ignored) {
        }
        try {
            consumer.close();
        } catch (IOException ignored) {
        }

    }


    @Override
    public void handle(Message msg, Consumer consumer) throws IOException {
        JSONObject jsonObject = JSON.parseObject(msg.getBody());
        String applicationName = jsonObject.getString(UNIFORM_APPLICATION_NAME);
        if (StringUtils.isEmpty(applicationName)) {
            return;
        }
        String mode = jsonObject.getString(UNIFORM_MODE);
        String message = jsonObject.getString(UNIFORM_MESSAGE);
        String format = StringUtils.format("[{}] [{}] [{}] {}", DateTime.now().toStandard(), applicationName, mode, message);
        sseTemplate.emit(SseMessage.builder().event(mode).message(format).build(), Duration.ofSeconds(10), applicationName);
    }

    @Override
    public void destroy() throws Exception {
        stop();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        startMqServer();
        start();
    }

    private void startMqServer() {
        UnifiedServerProperties.EndpointOption endpoint = unifiedServerProperties.getEndpoint();
        if(StringUtils.isBlank(endpoint.getHost()) || null == endpoint.getPort()) {
            return;
        }
        MqServerConfig mqServerConfig = new MqServerConfig();
        mqServerConfig.setServerHost(endpoint.getHost());
        mqServerConfig.setServerPort(endpoint.getPort());
        this.mqServer = new MqServer(mqServerConfig);
        try {
            mqServer.start();
        } catch (Exception ignored) {
        }
    }
}
