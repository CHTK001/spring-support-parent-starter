package com.chua.starter.redis.support;

import com.chua.common.support.protocol.ClientSetting;
import com.chua.common.support.utils.StringUtils;
import com.chua.redis.support.client.RedisClient;
import com.chua.starter.redis.support.listener.RedisListener;
import com.chua.starter.redis.support.properties.RedisServerProperties;
import com.chua.starter.redis.support.server.RedisEmbeddedServer;
import com.chua.starter.redis.support.service.RedisSearchService;
import com.chua.starter.redis.support.service.SimpleRedisService;
import com.chua.starter.redis.support.service.TimeSeriesService;
import com.chua.starter.redis.support.service.impl.RedisSearchServiceImpl;
import com.chua.starter.redis.support.service.impl.SimpleRedisServiceImpl;
import com.chua.starter.redis.support.service.impl.TimeSeriesServiceImpl;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * redisson
 *
 * @author CH
 */
@Slf4j
@EnableConfigurationProperties(RedisServerProperties.class)
public class RedisConfiguration implements ApplicationContextAware, Ordered {

    RedisServerProperties redisServerProperties;


    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        log.info(">>>>> 开始创建Redisson客户端: => {}:{}", redisProperties.getHost(), redisProperties.getPort());
        Config config = new Config();
        config.useSingleServer()
                .setDatabase(redisProperties.getDatabase())
                .setUsername(redisProperties.getUsername())
                .setPassword(redisProperties.getPassword())
                .setAddress(StringUtils.defaultString(redisProperties.getUrl(),
                        "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort() + "/"))
                .setTimeout(null == redisProperties.getTimeout() ? 10000 : (int) redisProperties.getTimeout().toMillis())
                .setConnectTimeout(null == redisProperties.getConnectTimeout() ? 10000: (int) redisProperties.getConnectTimeout().toMillis())
                .setClientName(redisProperties.getClientName());
        return Redisson.create(config);
    }

    @Bean(destroyMethod = "close", name = "redisClient")
    @ConditionalOnMissingBean
    public RedisClient redisClient(RedisProperties redisProperties) {
        log.info(">>>>> 开始创建RedisClient客户端: => {}:{}", redisProperties.getHost(), redisProperties.getPort());
        RedisClient redisClient = new RedisClient(
                ClientSetting.builder()
                        .database(String.valueOf(redisProperties.getDatabase()))
                        .host(redisProperties.getHost())
                        .port(redisProperties.getPort())
                        .password(redisProperties.getPassword())
                        .connectTimeoutMillis(null == redisProperties.getTimeout() ? 10_000L : redisProperties.getTimeout().toMillis())
                        .build()
        );

        redisClient.connect();
        return redisClient;
    }


    @Bean
    @DependsOn("redisClient")
    @ConditionalOnMissingBean
    public TimeSeriesService timeSeriesService() {
        return new TimeSeriesServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisSearchService redisSearchService() {
        return new RedisSearchServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public SimpleRedisService simpleRedisService() {
        return new SimpleRedisServiceImpl();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        redisServerProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate("plugin.redis.server", RedisServerProperties.class);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "plugin.redis.server", name = "open-embedded", havingValue = "true", matchIfMissing = false)
    public RedisEmbeddedServer embeddedServer() {
        return new RedisEmbeddedServer(redisServerProperties);
    }


    public static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 忽略未知字段
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 注册 JavaTimeModule 以支持 Java 8 日期时间类型
        objectMapper.registerModule(new JavaTimeModule());
        // 设置日期格式
        objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        return objectMapper;
    }

    @Bean
    @ConditionalOnMissingBean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory, List<RedisListener>listeners) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        for (RedisListener listener : listeners) {
            container.addMessageListener(listener::onMessage, listener.getTopics());
        }
        /**
         * 设置序列化对象
         * 特别注意：1. 发布的时候需要设置序列化；订阅方也需要设置序列化
         *         2. 设置序列化对象必须放在[加入消息监听器]这一步后面，否则会导致接收器接收不到消息
         */
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        Jackson2JsonRedisSerializer seria = new Jackson2JsonRedisSerializer(objectMapper, Object.class);
        container.setTopicSerializer(seria);
        return container;
    }

    @Bean
    @SuppressWarnings("all")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        //由于源码autoConfig中是<Object, Object>，开发中一般直接使用<String,Object>
        RedisTemplate<String, Object> template = new RedisTemplate();
        template.setConnectionFactory(factory);

        //Json序列化配置
        Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        jackson2JsonRedisSerializer.setObjectMapper(om);

        //String的序列化
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        //key采用string的序列化
        template.setKeySerializer(stringRedisSerializer);
        //hash的key采用string的序列化
        template.setHashKeySerializer(stringRedisSerializer);
        //value序列化采用jackson
        template.setValueSerializer(jackson2JsonRedisSerializer);
        //hash的value序列化方式采用jackson
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean("stringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
