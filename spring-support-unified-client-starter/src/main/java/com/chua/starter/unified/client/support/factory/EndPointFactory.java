package com.chua.starter.unified.client.support.factory;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.function.InitializingAware;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.client.support.options.TransPointConfig;
import com.chua.starter.unified.client.support.properties.UnifiedClientProperties;
import org.springframework.core.env.Environment;

/**
 * 端点工厂
 *
 * @author CH
 */
public class EndPointFactory implements InitializingAware {
    private final BootResponse bootResponse;
    private final UnifiedClientProperties unifiedClientProperties;
    private final String content;
    private final String appName;
    private final Environment environment;

    public EndPointFactory(BootResponse bootResponse,
                           UnifiedClientProperties unifiedClientProperties,
                           String appName,
                           Environment environment) {
        this.bootResponse = bootResponse;
        this.unifiedClientProperties = unifiedClientProperties;
        this.content = bootResponse.getContent();
        this.appName = appName;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        UnifiedClientProperties.EndpointOption endpointOption = JSON.parseObject(content, UnifiedClientProperties.EndpointOption.class);
        TransPointConfig transPointConfig = openEndPoint(endpointOption);
        openAttach(transPointConfig);
    }



    /**
     * 打开连接
     *
     * @param transPointConfig 转换点配置
     */
    private void openAttach(TransPointConfig transPointConfig) {
        AttachFactory attachFactory = new AttachFactory(transPointConfig, appName, environment.resolvePlaceholders("${server.port:}"));
        attachFactory.afterPropertiesSet();

    }

    /**
     * 开放式端点
     *
     * @param endpointOption 端点选项
     * @return {@link TransPointConfig}
     */
    private TransPointConfig openEndPoint(UnifiedClientProperties.EndpointOption endpointOption) {
        if(null == endpointOption || StringUtils.isEmpty(endpointOption.getUrl())) {
            return openLocalEndPoint(unifiedClientProperties.getEndpoint());
        }

        TransPointConfig transPointConfig = new TransPointConfig();
        transPointConfig.setUrl(endpointOption.getUrl());

        return transPointConfig;
    }

    /**
     * 开放局部端点
     *
     * @param endpointOption 端点选项
     */
    private TransPointConfig openLocalEndPoint(UnifiedClientProperties.EndpointOption endpointOption) {
        if(null == endpointOption || StringUtils.isEmpty(endpointOption.getUrl())) {
            return null;
        }
        TransPointConfig transPointConfig = new TransPointConfig();
        transPointConfig.setUrl(endpointOption.getUrl());
        transPointConfig.setPath(endpointOption.getAttach());

        return transPointConfig;
    }
}
