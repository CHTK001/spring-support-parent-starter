package com.chua.starter.common.support.configuration;
import com.chua.common.support.core.utils.ClassUtils;
import com.chua.starter.common.support.api.encode.ApiResponseEncodeRegister;
import com.chua.starter.common.support.configuration.resolver.RequestParamsMapMethodArgumentResolver;
import com.chua.starter.common.support.converter.BinaryHttpMessageConverter;
import com.chua.starter.common.support.jackson.configuration.JacksonConfiguration;
import com.chua.starter.common.support.log.MdcHandlerFilter;
import com.chua.starter.common.support.log.RestTemplateTraceIdInterceptor;
import com.chua.starter.common.support.processor.ResponseModelViewMethodProcessor;
import com.chua.starter.common.support.jackson.configuration.JacksonProperties;
import com.chua.starter.common.support.properties.MessageConverterProperties;
import com.chua.starter.common.support.api.encode.ApiContentNegotiationStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.cbor.MappingJackson2CborHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.http.converter.yaml.MappingJackson2YamlHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * 跨域处理
 *
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties({MessageConverterProperties.class})
public class MessageConverterWebMvcConfigurer implements WebMvcConfigurer, ApplicationContextAware, WebMvcRegistrations {
    /**
     * 构造函数
     *
     * @param messageConverterProperties MessageConverterProperties
     */
    public MessageConverterWebMvcConfigurer(MessageConverterProperties messageConverterProperties) {
        this.messageConverterProperties = messageConverterProperties;
    }

        final MessageConverterProperties messageConverterProperties;
    private List<HttpMessageConverter<?>> messageConverters;
    private ApplicationContext applicationContext;
    private ApiResponseEncodeRegister apiResponseEncodeRegister;

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        handlers.add(new ResponseModelViewMethodProcessor(messageConverters));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequestParamsMapMethodArgumentResolver());
    }
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        if (apiResponseEncodeRegister != null) {
            BinaryHttpMessageConverter binaryConverter = new BinaryHttpMessageConverter(apiResponseEncodeRegister, messageConverters);
            converters.addFirst(binaryConverter);
            log.info("[消息转换器]注册二进制转换器到首位: {}", BinaryHttpMessageConverter.class.getName());
        } else {
            log.debug("[消息转换器]ApiResponseEncodeRegister 未配置, 跳过二进制转换器注册");
        }
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        if (apiResponseEncodeRegister != null) {
            configurer.strategies(List.of(new ApiContentNegotiationStrategy(apiResponseEncodeRegister)));
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
        log.info("[消息转换器]开始扩展消息转换器列表, 当前转换器数量: {}", converters.size());
        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> converter = converters.get(i);
            log.debug("[消息转换器]已存在转换器[{}]: {}", i, converter.getClass().getName());
        }
        int index = -1;
        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> converter = converters.get(i);
            if(converter instanceof AllEncompassingFormHttpMessageConverter) {
                index = i;
                break;
            }
        }
        if(index > 0) {
            JacksonProperties jacksonProperties = applicationContext.getBean(JacksonProperties.class);
            log.info("[消息转换器]创建 MappingJackson2HttpMessageConverter, includeNull={}", jacksonProperties.isIncludeNull());
            ObjectMapper objectMapper = JacksonConfiguration.createObjectMapper(false, jacksonProperties.isIncludeNull());
            log.info("[消息转换器]使用的 ObjectMapper 类型: {}", objectMapper.getClass().getName());
            MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter(objectMapper);
            mappingJackson2HttpMessageConverter.setDefaultCharset(StandardCharsets.UTF_8);
            mappingJackson2HttpMessageConverter.setPrettyPrint(true);
            converters.add(index - 1, mappingJackson2HttpMessageConverter);
            log.info("[消息转换器]已注册 MappingJackson2HttpMessageConverter 到位置: {}, 转换器类型: {}", 
                    index - 1, mappingJackson2HttpMessageConverter.getClass().getName());
        } else {
            log.warn("[消息转换器]未找到 AllEncompassingFormHttpMessageConverter, 跳过注册 MappingJackson2HttpMessageConverter");
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
        log.info("[消息转换器]初始化消息转换器配置");
        this.applicationContext = applicationContext;
        this.messageConverters = new LinkedList<>();
        try {
            this.apiResponseEncodeRegister = applicationContext.getBean(ApiResponseEncodeRegister.class);
            log.info("[消息转换器]已注册 ApiResponseEncodeRegister: {}", apiResponseEncodeRegister.getClass().getName());
        } catch (BeansException e) {
            log.debug("[消息转换器]ApiResponseEncodeRegister 未找到, 编码功能已禁用");
        }
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);
        log.info("[消息转换器]获取 ObjectMapper Bean, 类型: {}", objectMapper.getClass().getName());
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        messageConverters.add(jacksonConverter);
        log.info("[消息转换器]注册 JSON 转换器: {}", MappingJackson2HttpMessageConverter.class.getName());
//        messageConverters.add(new FastJsonHttpMessageConverter());
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter();
        messageConverters.add(stringConverter);
        log.info("[消息转换器]注册字符串转换器: {}", StringHttpMessageConverter.class.getName());
        if(ClassUtils.isPresent("com.fasterxml.jackson.dataformat.xml.XmlMapper")) {
            MappingJackson2XmlHttpMessageConverter xmlConverter = new MappingJackson2XmlHttpMessageConverter();
            messageConverters.add(xmlConverter);
            log.info("[消息转换器]注册 XML 转换器: {}", MappingJackson2XmlHttpMessageConverter.class.getName());
        } else {
            log.debug("[消息转换器]XML 转换器不可用 (缺少 XmlMapper 依赖)");
        }

        if(ClassUtils.isPresent("com.fasterxml.jackson.dataformat.yaml.YAMLFactory")) {
            MappingJackson2YamlHttpMessageConverter yamlConverter = new MappingJackson2YamlHttpMessageConverter();
            messageConverters.add(yamlConverter);
            log.info("[消息转换器]注册 YAML 转换器: {}", MappingJackson2YamlHttpMessageConverter.class.getName());
        } else {
            log.debug("[消息转换器]YAML 转换器不可用 (缺少 YAMLFactory 依赖)");
        }

        if(ClassUtils.isPresent("com.fasterxml.jackson.dataformat.cbor.CBORFactory")) {
            MappingJackson2CborHttpMessageConverter cborConverter = new MappingJackson2CborHttpMessageConverter();
            messageConverters.add(cborConverter);
            log.info("[消息转换器]注册 CBOR 转换器: {}", MappingJackson2CborHttpMessageConverter.class.getName());
        } else {
            log.debug("[消息转换器]CBOR 转换器不可用 (缺少 CBORFactory 依赖)");
        }

//        if(ClassUtils.isPresent("com.google.protobuf.CodedOutputStream")) {
//            ProtobufHttpMessageConverter protobufConverter = new ProtobufHttpMessageConverter();
//            messageConverters.add(protobufConverter);
//            log.info("[消息转换器]注册 Protobuf 转换器: {}", ProtobufHttpMessageConverter.class.getName());
//        } else {
//            log.debug("[消息转换器]Protobuf 转换器不可用 (缺少 protobuf 依赖)");
//        }

        log.info("[消息转换器]消息转换器初始化完成, 共注册 {} 个转换器", messageConverters.size());
        try {
            RestTemplate restTemplate = applicationContext.getBean(RestTemplate.class);
            restTemplate.setInterceptors(Collections.singletonList(new RestTemplateTraceIdInterceptor()));
            log.debug("[消息转换器]已为 RestTemplate 注册 TraceId 拦截器");
        } catch (BeansException ignored) {
            log.debug("[消息转换器]RestTemplate Bean 未找到, 跳过 TraceId 拦截器注册");
        }
    }
}

