package com.chua.starter.common.support.configuration;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.Filter;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring.http.converter.FastJsonHttpMessageConverter;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.configuration.resolver.RequestParamsMapMethodArgumentResolver;
import com.chua.starter.common.support.filter.CryptoFilter;
import com.chua.starter.common.support.filter.EnumConvertAfterFilter;
import com.chua.starter.common.support.filter.PrivacyEncryptFilter;
import com.chua.starter.common.support.processor.ResponseModelViewMethodProcessor;
import com.chua.starter.common.support.properties.MessageConverterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

/**
 * 跨域处理
 *
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties(MessageConverterProperties.class)
public class MessageConverterWebMvcConfigurer implements WebMvcConfigurer, ApplicationContextAware, WebMvcRegistrations {

    @Resource
    private MessageConverterProperties messageConverterProperties;
    private List<HttpMessageConverter<?>> messageConverters;

    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        handlers.add(new ResponseModelViewMethodProcessor(messageConverters));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequestParamsMapMethodArgumentResolver());
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        registerFastjson(converters);
    }

    private void registerFastjson(List<HttpMessageConverter<?>> converters) {
        log.info(">>>>>>> 开启 FastJson2 数据转化");
        FastJsonHttpMessageConverter fastJsonHttpMessageConverter = new FastJsonHttpMessageConverter();
        FastJsonConfig fastJsonConfig = new FastJsonConfig();
        fastJsonConfig.setDateFormat(messageConverterProperties.getDataFormat());
        fastJsonConfig.setCharset(StandardCharsets.UTF_8);
        JSONWriter.Feature[] writerFeatures = messageConverterProperties.getWriterFeatures();
        JSONReader.Feature[] readerFeatures = messageConverterProperties.getReaderFeatures();

        if(CollectionUtils.isEmpty(writerFeatures)) {
            fastJsonConfig.setWriterFeatures(
                    JSONWriter.Feature.WriteNullBooleanAsFalse,
                    JSONWriter.Feature.WriteNullListAsEmpty,
                    JSONWriter.Feature.WriteEnumsUsingName
            );
        } else {
            fastJsonConfig.setWriterFeatures(writerFeatures);
        }

        if(CollectionUtils.isEmpty(readerFeatures)) {
            fastJsonConfig.setReaderFeatures(
                    JSONReader.Feature.SupportArrayToBean,
                    JSONReader.Feature.IgnoreNullPropertyValue,
                    JSONReader.Feature.ErrorOnUnknownProperties
            );
        } else {
            fastJsonConfig.setReaderFeatures(readerFeatures);
        }

        List<Filter> writerFilters = new LinkedList<>();
        List<Filter> readerFilters = new LinkedList<>();
        writerFilters.add(new EnumConvertAfterFilter());

        if(messageConverterProperties.isOpenDesensitize()) {
            writerFilters.add(new PrivacyEncryptFilter());
        }

        if(messageConverterProperties.isOpenCrypto()) {
            writerFilters.add(new CryptoFilter());
            readerFilters.add(new CryptoFilter());
        }

        if(CollectionUtils.isNotEmpty(writerFilters)) {
            fastJsonConfig.setWriterFilters(writerFilters.toArray(new Filter[0]));
        }

        if(CollectionUtils.isNotEmpty(readerFilters)) {
            fastJsonConfig.setReaderFilters(readerFilters.toArray(new Filter[0]));
        }

        fastJsonHttpMessageConverter.setSupportedMediaTypes(messageConverterProperties.getMediaTypes());
        fastJsonHttpMessageConverter.setFastJsonConfig(fastJsonConfig);

        converters.add(0, fastJsonHttpMessageConverter);
    }


    @Override
    public void addFormatters(FormatterRegistry registry) {
        ApplicationConversionService.configure(registry);
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.messageConverters = new LinkedList<>();
        messageConverters.add(new MappingJackson2HttpMessageConverter());
        messageConverters.add(new FastJsonHttpMessageConverter());
        messageConverters.add(new StringHttpMessageConverter());
    }
}
