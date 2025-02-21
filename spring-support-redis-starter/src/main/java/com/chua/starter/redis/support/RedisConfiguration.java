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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * redisson
 *
 * @author CH
 */
@EnableConfigurationProperties(RedisServerProperties.class)
public class RedisConfiguration implements ApplicationContextAware, Ordered {

    RedisServerProperties redisServerProperties;


    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(RedisProperties redisProperties) {
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
        RedisClient redisClient = new RedisClient(
                ClientSetting.builder()
                        .database(String.valueOf(redisProperties.getDatabase()))
                        .host(redisProperties.getHost())
                        .port(redisProperties.getPort())
                        .password(redisProperties.getPassword())
                        .connectTimeoutMillis(null ==  redisProperties.getTimeout()? 10000: (int) redisProperties.getTimeout().toMillis())
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

//    @Bean
//    @ConditionalOnMissingBean
//    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
//        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
//        redisTemplate.setConnectionFactory(factory);
//        redisTemplate.setKeySerializer(new StringRedisSerializer());
//        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
//        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
//        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
//        return redisTemplate;
//    }

    @Bean
    @ConditionalOnMissingBean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory, List<RedisListener>listeners) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        for (RedisListener listener : listeners) {
            container.addMessageListener(listener::onMessage, listener.getTopics());
        }
        return container;
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
