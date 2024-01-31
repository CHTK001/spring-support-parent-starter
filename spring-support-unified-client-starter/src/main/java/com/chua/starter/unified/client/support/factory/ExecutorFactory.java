package com.chua.starter.unified.client.support.factory;

import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.boot.*;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.client.support.event.UnifiedEvent;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.zbus.broker.Broker;
import org.zbus.broker.BrokerConfig;
import org.zbus.broker.SingleBroker;
import org.zbus.mq.MqConfig;
import org.zbus.mq.Producer;
import org.zbus.net.http.Message;

import java.io.IOException;
import java.util.Map;

import static com.chua.common.support.discovery.Constants.SUBSCRIBE;

/**
 * 执行器工厂
 *
 * @author CH
 */
@Slf4j
public class ExecutorFactory implements InitializingAware, DisposableBean, ApplicationListener<UnifiedEvent> {
    private final Protocol protocol;
    private final UnifiedClientProperties unifiedClientProperties;
    private final String appName;
    private final Environment environment;
    private BootResponse bootResponse;
    private Producer producer;

    public ExecutorFactory(Protocol protocol,
                           UnifiedClientProperties unifiedClientProperties,
                           String appName,
                           Environment environment) {
        this.protocol = protocol;
        this.unifiedClientProperties = unifiedClientProperties;
        this.appName = appName;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        registryEnv(protocol);
    }


    public BootResponse getResponse()  {
        return bootResponse;
    }

    /**
     * 注册表环境
     *
     * @param protocol1 protocol1
     */
    private void registryEnv(Protocol protocol1) {
        BootOption bootOption = protocol1.copyBootOption();
        bootOption.setKeepAlive(false);
        ProtocolClient protocolClient = protocol1.createClient(bootOption);
        BootRequest request = createRequest();
        this.bootResponse = protocolClient.send(request);
        log.info("注册结果: {}", bootResponse);
        connectMq();
    }

    private void connectMq() {
        try {
            JsonObject jsonObject = Json.fromJson(bootResponse.getContent(), JsonObject.class);
            String host = jsonObject.getString("host");
            String port = jsonObject.getString("port");
            if(StringUtils.isBlank(host) || StringUtils.isBlank(port)) {
                return;
            }
            BrokerConfig config = new BrokerConfig();
            config.setBrokerAddress(host + ":" + port);
            Broker broker = new SingleBroker(config);
            MqConfig mqConfig = new MqConfig();
            mqConfig.setBroker(broker);
            mqConfig.setMode(org.zbus.mq.Protocol.MqMode.MQ);
            mqConfig.setMq("unified");
            this.producer = new Producer(mqConfig);
            try {
                producer.createMQ();
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    private BootRequest createRequest() {
        BootRequest request = new BootRequest();
        request.setModuleType(ModuleType.EXECUTOR);
        request.setCommandType(CommandType.REGISTER);
        UnifiedClientProperties.UnifiedExecuter executer = unifiedClientProperties.getExecuter();
        JsonObject jsonObject = new JsonObject();
        jsonObject.putAll(BeanMap.create(executer));
        Map<ModuleType, UnifiedClientProperties.SubscribeOption> subscribe = unifiedClientProperties.getSubscribe();
        UnifiedClientProperties.SubscribeOption subscribeOption = new UnifiedClientProperties.SubscribeOption();
        subscribeOption.setExt(new JsonObject()
                .fluentPut("port", environment.resolvePlaceholders("${server.port:8080}"))
                .fluentPut("endpointsUrl", environment.resolvePlaceholders("${management.endpoints.web.base-path:/actuator}"))
                .fluentPut("contextPath", environment.resolvePlaceholders("${server.servlet.context-path:}")));
        subscribe.put(ModuleType.ACTUATOR, subscribeOption);
        jsonObject.put(SUBSCRIBE, subscribe);
        request.setAppName(appName);
        request.setProfile(environment.getProperty("spring.profiles.active", "default"));
        request.setContent(jsonObject.toJSONString());
        return request;
    }


    @Override
    public void destroy() throws Exception {
    }

    @Override
    public void onApplicationEvent(UnifiedEvent event) {
        if(null != producer) {
            Message msg = new Message();
            msg.setBody(new JsonObject()
                    .fluentPut("applicationName", appName)
                    .fluentPut("mode", event.getMode())
                    .fluentPut("type", event.getType())
                    .fluentPut("message", event.getSource())
                    .toString()
            );
            try {
                producer.sendAsync(msg);
            } catch (IOException ignored) {
            }
        }
    }
}
