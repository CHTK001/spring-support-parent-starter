package com.chua.starter.common.support.configuration;
import com.chua.common.support.utils.ClassUtils;
import com.chua.starter.common.support.api.encode.ApiResponseEncodeRegister;
import com.chua.starter.common.support.configuration.resolver.RequestParamsMapMethodArgumentResolver;
import com.chua.starter.common.support.converter.BinaryHttpMessageConverter;
import com.chua.starter.common.support.jackson.configuration.JacksonConfiguration;
import com.chua.starter.common.support.log.MdcHandlerFilter;
import com.chua.starter.common.support.log.RestTemplateTraceIdInterceptor;
import com.chua.starter.common.support.processor.ResponseModelViewMethodProcessor;
import com.chua.starter.common.support.jackson.configuration.JacksonProperties;
import com.chua.starter.common.support.properties.MessageConverterProperties;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.api.encode.ApiContentNegotiationStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* 跨域处理
*
* @author CH
* @since 4.0.0
 */
@SuppressWarnings({"removal", "deprecation"})
@EnableConfigurationProperties({MessageConverterProperties.class})
public class MessageConverterWebMvcConfigurer implements WebMvcConfigurer, ApplicationContextAware {
    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(MessageConverterWebMvcConfigurer.class);
    /**
    * 构造函数
    *
    * @param messageConverterProperties 消息转换器属性
    */
    public MessageConverterWebMvcConfigurer(MessageConverterProperties messageConverterProperties) {
        this.messageConverterProperties = messageConverterProperties;
    }

        final MessageConverterProperties messageConverterProperties; // 消息转换器属性
    private List<HttpMessageConverter<?>> messageConverters; // 消息转换器
    /** Application上下文 */
    private ApplicationContext applicationContext;

    @Override
    /** 添加申报值处理器 */
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        handlers.add(new ResponseModelViewMethodProcessor(messageConverters));
    }

    @Override
    /** 添加参数解析器 */
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequestParamsMapMethodArgumentResolver());
    }
    @Override
    /** configure消息转换器 */
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        ApiResponseEncodeRegister encodeRegister = getApiResponseEncodeRegister();
        if (encodeRegister != null) {
 // 配置类中 新 合法，Spring 会管理这些消息转换器 Bean
            BinaryHttpMessageConverter binaryConverter = new BinaryHttpMessageConverter(encodeRegister, messageConverters);
            converters.addFirst(binaryConverter);
            log.info("[消息转换器]注册二进制转换器到首位: {}", BinaryHttpMessageConverter.class.getName());
        } else {
            log.debug("[消息转换器]ApiResponseEncodeRegister 未配置, 跳过二进制转换器注册");
        }
    }
    @Override
    /** 添加拦截器 */
    public void addInterceptors(InterceptorRegistry registry) {
    }

    @Override
    /** configure内容negotiation */
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        ApiResponseEncodeRegister encodeRegister = getApiResponseEncodeRegister();
        if (encodeRegister != null) {
            configurer.strategies(List.of(new ApiContentNegotiationStrategy(encodeRegister)));
        }
    }


    @Bean
    /**
    * mdc处理器过滤
    *
    * @return mdc处理器过滤器的结果
    */
    public FilterRegistrationBean<MdcHandlerFilter> mdcHandlerFilter() {
        FilterRegistrationBean<MdcHandlerFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(new MdcHandlerFilter());
        filterRegistrationBean.setOrder(Integer.MIN_VALUE);
        filterRegistrationBean.addUrlPatterns("/*");
        return filterRegistrationBean;
    }

    /**
    * 消息转换器的 服务信息 贡献者。
    *
    * @return ServiceInfoContributor
    */
    @Bean
    public ServiceInfoContributor messageConverterServiceInfoContributor() {
        return registry -> registry.registerFlag("message-converter", "消息转换",
                messageConverterProperties.getEnable(),
                "消息转换器 (JSON/XML/YAML/CBOR)",
                "[脱敏: " + (messageConverterProperties.getOpenDesensitize() ? "开" : "关")
                        + " 加解密: " + (messageConverterProperties.getOpenCrypto() ? "开" : "关") + "]");
    }

    @Override
    /** extend消息转换器 */
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
 // 配置类中 新 合法，Spring 会管理这些消息转换器 Bean
            MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter(objectMapper);
            mappingJackson2HttpMessageConverter.setDefaultCharset(StandardCharsets.UTF_8);
            mappingJackson2HttpMessageConverter.setPrettyPrint(true);
            converters.add(index - 1, mappingJackson2HttpMessageConverter);
            log.info("[消息转换器]已注册 MappingJackson2HttpMessageConverter 到位置: {}, 转换器类型: {}",
                    index - 1, mappingJackson2HttpMessageConverter.getClass().getName());
        } else {
            log.warn("[消息转换器]未找到 AllEncompassingFormHttpMessageConverter, 追加注册 MappingJackson2HttpMessageConverter");
            JacksonProperties jacksonProperties = applicationContext.getBean(JacksonProperties.class);
            ObjectMapper objectMapper = JacksonConfiguration.createObjectMapper(false, jacksonProperties.isIncludeNull());
 // 配置类中 新 合法，Spring 会管理这些消息转换器 Bean
            MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter(objectMapper);
            mappingJackson2HttpMessageConverter.setDefaultCharset(StandardCharsets.UTF_8);
            mappingJackson2HttpMessageConverter.setPrettyPrint(true);
            converters.add(mappingJackson2HttpMessageConverter);
            log.info("[消息转换器]已追加注册 MappingJackson2HttpMessageConverter, 当前转换器数量: {}", converters.size());
        }
 // 注册fastjson(转换器);
    }


 // 私募 Void Linux 注册fastjson(列表<HttpMessageConverter<?>> 转换器) {
 // 日志.信息(">>>>>>> 开启 fastjson2 数据转化");
 // fastjsonhttp消息转换器 fastjsonhttp消息转换器 = 新 fastjsonhttp消息转换器();
 // fastjson配置 fastjson配置 = 新 fastjson配置();
 // fastjson配置.设置日期格式化(消息转换器属性.获取数据格式化());
 // fastjson配置.设置字符集(标准字符集.UTF_8);
 // jsonwriter.特征[] writer特征 = 消息转换器属性.获取writer特征();
 // json读取.特征[] 读取特征 = 消息转换器属性.获取读取特征();
//
 // if(集合工具.是否空(writer特征)) {
 // fastjson配置.设置writer特征(
 // jsonwriter.特征.写入空布尔值asfalse,
 // jsonwriter.特征.写入空列表as空,
 // jsonwriter.特征.写入enums使用名称
//            );
//        } else {
 // fastjson配置.设置writer特征(writer特征);
//        }
//
 // if(集合工具.是否空(读取特征)) {
 // fastjson配置.设置读取特征(
 // json读取.特征.支持array转为Bean,
 // json读取.特征.ignoreauto类型not匹配,
 // json读取.特征.ignore空财产值,
 // json读取.特征.ignore设置空值,
 // json读取.特征.ignore无serializable
    private static final long serialVersionUID = 1L;
//            );
//        } else {
 // fastjson配置.设置读取特征(读取特征);
//        }
//
 // 列表<Filter> writer过滤器 = 新 链接列表<>();
 // 列表<Filter> 读取过滤器 = 新 链接列表<>();
 // writer过滤器.添加(新 enum转换之后过滤器());
//
 // if(消息转换器属性.是否打开desensitize()) {
 // writer过滤器.添加(新 隐私encrypt过滤器());
//        }
//
 // if(消息转换器属性.是否打开加密货币()) {
 // writer过滤器.添加(新 加密货币过滤器());
 // 读取过滤器.添加(新 加密货币过滤器());
//        }
//
 // if(集合工具.是否not空(writer过滤器)) {
 // fastjson配置.设置writer过滤器(writer过滤器.转为array(新 过滤器[0]));
//        }
//
 // if(集合工具.是否not空(读取过滤器)) {
 // fastjson配置.设置读取过滤器(读取过滤器.转为array(新 过滤器[0]));
//        }
//
 // fastjsonhttp消息转换器.设置支持media类型(消息转换器属性.获取media类型());
 // fastjsonhttp消息转换器.设置fastjson配置(fastjson配置);
//
 // 转换器.添加(0, fastjsonhttp消息转换器);
//    }


    @Override
    /** 添加Formatters */
    public void addFormatters(FormatterRegistry registry) {
        ApplicationConversionService.configure(registry);
    }


    @Override
    /** 设置application上下文 */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        log.info("[消息转换器]初始化消息转换器配置");
        this.applicationContext = applicationContext;
        this.messageConverters = new LinkedList<>();
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);
        log.info("[消息转换器]获取 ObjectMapper Bean, 类型: {}", objectMapper.getClass().getName());
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        messageConverters.add(jacksonConverter);
        log.info("[消息转换器]注册 JSON 转换器: {}", MappingJackson2HttpMessageConverter.class.getName());
 // 消息转换器.添加(新 fastjsonhttp消息转换器());
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

 // if(类工具.是否present("com.google.protobuf.编码输出流")) {
 // protobufhttp消息转换器 protobuf转换器 = 新 protobufhttp消息转换器();
 // 消息转换器.添加(protobuf转换器);
 // 日志.信息("[消息转换器]注册 Protobuf 转换器: {}", protobufhttp消息转换器.类.获取名称());
//        } else {
 // 日志.调试("[消息转换器]Protobuf 转换器不可用 (缺少 protobuf 依赖)");
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

    /**
    * 延迟获取 api响应encode注册 Bean
    * 使用 对象提供者 延迟获取，避免在初始化阶段获取导致循环依赖
    *
    * @return ApiResponseEncodeRegister 实例，如果不存在则返回 空
    */
    private ApiResponseEncodeRegister getApiResponseEncodeRegister() {
        if (applicationContext == null) {
            return null;
        }
        try {
 // 使用 对象提供者 延迟获取，避免循环依赖
            var provider = applicationContext.getBeanProvider(ApiResponseEncodeRegister.class);
            return provider.getIfAvailable();
        } catch (Exception e) {
            log.debug("[消息转换器]ApiResponseEncodeRegister 未找到, 编码功能已禁用");
            return null;
        }
    }
}

