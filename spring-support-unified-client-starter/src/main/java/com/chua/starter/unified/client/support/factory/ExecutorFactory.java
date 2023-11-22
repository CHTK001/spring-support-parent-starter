package com.chua.starter.unified.client.support.factory;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.chua.common.support.bean.BeanMap;
import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.protocol.boot.*;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import static com.chua.common.support.discovery.Constants.SUBSCRIBE;

/**
 * 执行器工厂
 *
 * @author CH
 */
@Slf4j
public class ExecutorFactory implements InitializingAware {
    private final Protocol protocol;
    private final UnifiedClientProperties unifiedClientProperties;
    private final String appName;
    private final Environment environment;
    private BootResponse bootResponse;

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
        ProtocolClient protocolClient = protocol1.createClient();
        BootRequest request = createRequest();
        this.bootResponse = protocolClient.send(request);
        log.info("注册结果: {}", bootResponse);
    }

    private BootRequest createRequest() {
        BootRequest request = new BootRequest();
        request.setModuleType(ModuleType.EXECUTOR);
        request.setCommandType(CommandType.REGISTER);
        UnifiedClientProperties.UnifiedExecuter executer = unifiedClientProperties.getExecuter();
        JSONObject jsonObject = new JSONObject();
        jsonObject.putAll(BeanMap.create(executer));
        jsonObject.put(SUBSCRIBE, unifiedClientProperties.getSubscribe());
        request.setAppName(appName);
        request.setProfile(environment.getProperty("spring.profiles.active", "default"));
        request.setContent(jsonObject.toJSONString(JSONWriter.Feature.WriteEnumsUsingName));
        return request;
    }



}
