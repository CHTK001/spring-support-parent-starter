package com.chua.starter.common.support.configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.List;

/**
 * 消息转换器报告配置
 *
 * <p>在应用启动完成后，统一输出最终生效的消息转换器列表和顺序，方便排查三方覆盖问题。</p>
 *
 * @author CH
 * @since 2026/01/14
 */
@Configuration
public class MessageConverterReportConfiguration implements ApplicationListener<ApplicationReadyEvent> {
    /**
     * 构造函数
     *
     * @param requestMappingHandlerAdapter RequestMappingHandlerAdapter
     */
    public MessageConverterReportConfiguration(RequestMappingHandlerAdapter requestMappingHandlerAdapter) {
        this.requestMappingHandlerAdapter = requestMappingHandlerAdapter;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MessageConverterReportConfiguration.class);


    private final RequestMappingHandlerAdapter requestMappingHandlerAdapter;

    /**
     * 应用启动完成后输出最终生效的消息转换器信息
     *
     * @param event 启动完成事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<HttpMessageConverter<?>> converters = requestMappingHandlerAdapter.getMessageConverters();
        if (converters == null || converters.isEmpty()) {
            log.warn("[消息转换器][报告]当前没有注册任何 HttpMessageConverter");
            return;
        }
        log.info("[消息转换器][报告]应用启动完成, 最终生效的 HttpMessageConverter 数量: {}", converters.size());

        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> converter = converters.get(i);
            List<MediaType> supportedMediaTypes = converter.getSupportedMediaTypes();
            log.info("[消息转换器][报告]顺序 [{}], 类型: {}, 支持的媒体类型: {}", i, converter.getClass().getName(), supportedMediaTypes);
        }
    }
}


