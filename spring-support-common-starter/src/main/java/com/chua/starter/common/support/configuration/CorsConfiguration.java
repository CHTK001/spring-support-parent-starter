package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.configuration.resolver.RequestParamsMapMethodArgumentResolver;
import com.chua.starter.common.support.converter.FastJsonHttpMessageConverter;
import com.chua.starter.common.support.converter.ResultDataHttpMessageConverter;
import com.chua.starter.common.support.filter.ParamLogFilter;
import com.chua.starter.common.support.limit.LimitAspect;
import com.chua.starter.common.support.processor.ResponseModelViewMethodProcessor;
import com.chua.starter.common.support.properties.*;
import com.chua.starter.common.support.provider.OptionsProvider;
import com.chua.starter.common.support.result.ResponseAdvice;
import com.chua.starter.common.support.version.ApiVersionRequestMappingHandlerMapping;
import com.chua.starter.common.support.watch.WatchPointcutAdvisor;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

/**
 * 跨域处理
 *
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties({
        OptionProperties.class,
        CorsProperties.class,
        CoreProperties.class,
        BeanProperties.class,
        LimitProperties.class
})
public class CorsConfiguration implements WebMvcConfigurer, ApplicationContextAware, WebMvcRegistrations {

    @Resource
    private CorsProperties corsProperties;
    @Resource
    private CoreProperties coreProperties;
    private List<HttpMessageConverter<?>> messageConverters;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            DateTimeFormatter localDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            //返回时间数据序列化
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(localDateTimeFormatter));
            // 接收时间数据反序列化
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(localDateTimeFormatter));
        };
    }

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        handlers.add(new ResponseModelViewMethodProcessor(messageConverters));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequestParamsMapMethodArgumentResolver());
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
//        MappingJackson2HttpMessageConverter messageConverter = new MappingJackson2HttpMessageConverter();
//        ObjectMapper objectMapper = new ObjectMapper();
//        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
//        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//        objectMapper.registerModule(new JavaTimeModule());
//        messageConverter.setObjectMapper(objectMapper);
//        converters.add(0, messageConverter);
        if (coreProperties.isUniformParameter()) {
            converters.add(0, new ResultDataHttpMessageConverter());
        }
    }

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        if (coreProperties.isOpenVersion()) {
            return new ApiVersionRequestMappingHandlerMapping();
        }
        return new RequestMappingHandlerMapping();
    }

    @Bean
    @ConditionalOnMissingBean
    public OptionsProvider optionsProvider(OptionProperties optionProperties, Environment environment) {
        return new OptionsProvider(optionProperties, environment);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.limit.open-limit", havingValue = "true", matchIfMissing = true)
    public LimitAspect limitAspect(LimitProperties limitProperties) {
        return new LimitAspect(limitProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.core.open-param-log", havingValue = "true", matchIfMissing = true)
    public ParamLogFilter paramLogFilter() {
        return new ParamLogFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.core.uniform-parameter", havingValue = "true", matchIfMissing = true)
    public ResponseAdvice responseAdvice() {
        return new ResponseAdvice();
    }

//    @Bean
//    @ConditionalOnMissingBean
//    @Lazy
//    public LoggerPointcutAdvisor loggerPointcutAdvisor(@Autowired(required = false) LoggerService loggerService) {
//        return new LoggerPointcutAdvisor(loggerService);
//    }

    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

//    @Bean
//    @ConditionalOnMissingBean
//    @Lazy
//    public CommonService commonService(CoreProperties coreProperties, @Qualifier(Constant.DEFAULT_EXECUTOR2) Executor executorService) {
//        return new CommonService(coreProperties, executorService);
//    }

    @Bean
    @ConditionalOnMissingBean
    @Lazy
    public WatchPointcutAdvisor watchPointcutAdvisor() {
        return new WatchPointcutAdvisor();
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        ApplicationConversionService.configure(registry);
    }

    /**
     * 跨域
     *
     * @return CorsFilter
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "plugin.cors.enable", matchIfMissing = false, havingValue = "true")
    public CorsFilter corsFilter() {
        //1. 添加 CORS配置信息
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        //放行哪些原始域
        config.addAllowedOriginPattern("*");
        //是否发送 Cookie
//        config.setAllowCredentials(true);
        //放行哪些请求方式
        config.addAllowedMethod("*");
        //放行哪些原始请求头部信息
        config.addAllowedHeader("*");
        //暴露哪些头部信息
        config.addExposedHeader("*");
        //2. 添加映射路径
        UrlBasedCorsConfigurationSource corsConfigurationSource = new UrlBasedCorsConfigurationSource();
        if (corsProperties.getPattern().isEmpty()) {
            corsConfigurationSource.registerCorsConfiguration("/**", config);
        } else {
            for (String s : corsProperties.getPattern()) {
                corsConfigurationSource.registerCorsConfiguration(s, config);
            }
        }
        //3. 返回新的CorsFilter
        log.info(">>>>>>> 开启跨域处理");
        return new CorsFilter(corsConfigurationSource);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.messageConverters = new LinkedList<>();
        messageConverters.add(new MappingJackson2HttpMessageConverter());
        messageConverters.add(new FastJsonHttpMessageConverter());
        messageConverters.add(new MappingJackson2XmlHttpMessageConverter());
        messageConverters.add(new StringHttpMessageConverter());
    }
}
