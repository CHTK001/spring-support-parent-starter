package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.configuration.resolver.RequestParamsMapMethodArgumentResolver;
import com.chua.starter.common.support.jackson.configuration.JacksonConfiguration;
import com.chua.starter.common.support.mdc.MdcHandlerFilter;
import com.chua.starter.common.support.mdc.RestTemplateTraceIdInterceptor;
import com.chua.starter.common.support.processor.ResponseModelViewMethodProcessor;
import com.chua.starter.common.support.properties.JacksonProperties;
import com.chua.starter.common.support.properties.MdcProperties;
import com.chua.starter.common.support.properties.MessageConverterProperties;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.micrometer.observation.ObservationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 跨域处理
 *
 * @author CH
 */
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties({MessageConverterProperties.class, MdcProperties.class})
public class MessageConverterWebMvcConfigurer implements WebMvcConfigurer, ApplicationContextAware, WebMvcRegistrations {

    final MessageConverterProperties messageConverterProperties;
    final MdcProperties mdcProperties;
    private List<HttpMessageConverter<?>> messageConverters;
    private ApplicationContext applicationContext;

    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        handlers.add(new ResponseModelViewMethodProcessor(messageConverters));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequestParamsMapMethodArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if(!mdcProperties.isEnable()) {
        }
    }


    @Bean
    public FilterRegistrationBean<MdcHandlerFilter> mdcHandlerFilter() {
        FilterRegistrationBean<MdcHandlerFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new MdcHandlerFilter());
        filterRegistrationBean.setOrder(Integer.MIN_VALUE);
        filterRegistrationBean.addUrlPatterns("/*");
        return filterRegistrationBean;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        int index = -1;
        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> converter = converters.get(i);
            if(converter instanceof AllEncompassingFormHttpMessageConverter) {
                index = i;
                break;
            }
        }
        if(index > 0) {
            MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter(
                    JacksonConfiguration.createObjectMapper(false, applicationContext.getBean(JacksonProperties.class).isIncludeNull())
            );
            mappingJackson2HttpMessageConverter.setDefaultCharset(StandardCharsets.UTF_8);
            mappingJackson2HttpMessageConverter.setPrettyPrint(true);
            converters.add(index - 1, mappingJackson2HttpMessageConverter);
        }
//        registerFastjson(converters);
    }


//    private void registerFastjson(List<HttpMessageConverter<?>> converters) {
//        log.info(">>>>>>> 开启 FastJson2 数据转化");
//        FastJsonHttpMessageConverter fastJsonHttpMessageConverter = new FastJsonHttpMessageConverter();
//        FastJsonConfig fastJsonConfig = new FastJsonConfig();
//        fastJsonConfig.setDateFormat(messageConverterProperties.getDataFormat());
//        fastJsonConfig.setCharset(StandardCharsets.UTF_8);
//        JSONWriter.Feature[] writerFeatures = messageConverterProperties.getWriterFeatures();
//        JSONReader.Feature[] readerFeatures = messageConverterProperties.getReaderFeatures();
//
//        if(CollectionUtils.isEmpty(writerFeatures)) {
//            fastJsonConfig.setWriterFeatures(
//                    JSONWriter.Feature.WriteNullBooleanAsFalse,
//                    JSONWriter.Feature.WriteNullListAsEmpty,
//                    JSONWriter.Feature.WriteEnumsUsingName
//            );
//        } else {
//            fastJsonConfig.setWriterFeatures(writerFeatures);
//        }
//
//        if(CollectionUtils.isEmpty(readerFeatures)) {
//            fastJsonConfig.setReaderFeatures(
//                    JSONReader.Feature.SupportArrayToBean,
//                    JSONReader.Feature.IgnoreAutoTypeNotMatch,
//                    JSONReader.Feature.IgnoreNullPropertyValue,
//                    JSONReader.Feature.IgnoreSetNullValue,
//                    JSONReader.Feature.IgnoreNoneSerializable
//            );
//        } else {
//            fastJsonConfig.setReaderFeatures(readerFeatures);
//        }
//
//        List<Filter> writerFilters = new LinkedList<>();
//        List<Filter> readerFilters = new LinkedList<>();
//        writerFilters.add(new EnumConvertAfterFilter());
//
//        if(messageConverterProperties.isOpenDesensitize()) {
//            writerFilters.add(new PrivacyEncryptFilter());
//        }
//
//        if(messageConverterProperties.isOpenCrypto()) {
//            writerFilters.add(new CryptoFilter());
//            readerFilters.add(new CryptoFilter());
//        }
//
//        if(CollectionUtils.isNotEmpty(writerFilters)) {
//            fastJsonConfig.setWriterFilters(writerFilters.toArray(new Filter[0]));
//        }
//
//        if(CollectionUtils.isNotEmpty(readerFilters)) {
//            fastJsonConfig.setReaderFilters(readerFilters.toArray(new Filter[0]));
//        }
//
//        fastJsonHttpMessageConverter.setSupportedMediaTypes(messageConverterProperties.getMediaTypes());
//        fastJsonHttpMessageConverter.setFastJsonConfig(fastJsonConfig);
//
//        converters.add(0, fastJsonHttpMessageConverter);
//    }


    @Override
    public void addFormatters(FormatterRegistry registry) {
        ApplicationConversionService.configure(registry);
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.messageConverters = new LinkedList<>();
        messageConverters.add(new MappingJackson2HttpMessageConverter(applicationContext.getBean(ObjectMapper.class)));
//        messageConverters.add(new FastJsonHttpMessageConverter());
        messageConverters.add(new StringHttpMessageConverter());

        try {
            RestTemplate restTemplate = applicationContext.getBean(RestTemplate.class);
            restTemplate.setInterceptors(Collections.singletonList(new RestTemplateTraceIdInterceptor()));
        } catch (BeansException ignored) {
        }
    }
}
