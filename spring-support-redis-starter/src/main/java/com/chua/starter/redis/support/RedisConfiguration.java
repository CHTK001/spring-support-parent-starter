package com.chua.starter.redis.support;

import com.chua.starter.common.support.application.ModuleEnvironmentRegistration;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import com.chua.starter.redis.support.listener.RedisListener;
import com.chua.starter.redis.support.properties.RedisServerProperties;
import com.chua.common.support.lang.datasource.kv.KvEngine;
import com.chua.starter.redis.support.service.TimeSeriesService;
import com.chua.starter.redis.support.service.RedisSearchService;
import com.chua.starter.redis.support.service.SimpleRedisService;
import com.chua.starter.redis.support.service.impl.TimeSeriesServiceImpl;
import com.chua.starter.redis.support.service.impl.RedisSearchServiceImpl;
import com.chua.starter.redis.support.service.impl.SimpleRedisServiceImpl;
import com.chua.starter.redis.support.kv.RedisKvEngine;
import com.chua.common.support.utils.StringUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
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
* Redis 自动配置类，负责初始化 Redisson 客户端、redistemplate、字符串redistemplate
* 及 Redis 消息监听容器等相关组件，支持嵌入式中、单机两种部署模式。
*
* <p>该配置在 {@code plugin.redis.server.enable=true}（默认开启）时生效，
* 通过 {@link RedisServerProperties} 暴露嵌入式服务器配置，
* 同时基于 Spring Boot {@link DataRedisProperties} 创建远程连接客户端。</p>
*
* <p>创建 Redisson 客户端与时间序列、搜索等服务实例。</p>
*
* @since 1.0.0
* @author CH
 */
@Slf4j
@EnableConfigurationProperties(RedisServerProperties.class)
@ConditionalOnProperty(prefix = "plugin.redis.server", name = "enable", havingValue = "true", matchIfMissing = true)
public class RedisConfiguration implements ApplicationContextAware, Ordered {

    /**
    * Redis 连接 URL 协议前缀，用于拼接 Redisson 地址
     */
    private static final String REDIS_PROTOCOL_PREFIX = "redis://";

    /**
    * Redis 地址 URL 后缀分隔符，用于拼接 database 部分
     */
    private static final String REDIS_ADDRESS_SUFFIX = "/";

    /**
    * 默认命令执行超时时间（毫秒），当 Redis属性 未配置 超时 时使用
     */
    private static final int DEFAULT_TIMEOUT_MILLIS = 10000;

    /**
    * 默认连接建立超时时间（毫秒），当 Redis属性 未配置 连接超时 时使用
     */
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10000;

    /**
    * Jackson 日期序列化格式字符串，统一使用 yyyy-MM-dd HH:mm:ss
     */
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
    * Redis 服务器配置属性，在应用上下文初始化阶段通过 Binder 绑定
     */
    private RedisServerProperties redisServerProperties;

    /**
    * 创建 Redisson 客户端，基于 Spring Boot Redis属性 构建单机连接配置。
    *
    * <p>当 RedisProperties.url 为空时，使用 host:port 拼接 redis:// 地址；
    * 超时 与 连接超时 缺省时回退到 10000 毫秒。</p>
    *
    * @param redisProperties Spring Boot 自动装配的 Redis 属性
    * @return 已配置完成的 redisson客户端 实例
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(DataRedisProperties redisProperties) {
        log.info(">>>>> 开始创建Redisson客户端: => {}:{}", redisProperties.getHost(), redisProperties.getPort());
        Config config = new Config();
        config.useSingleServer()
                .setDatabase(redisProperties.getDatabase())
                .setUsername(redisProperties.getUsername())
                .setPassword(redisProperties.getPassword())
                .setAddress(StringUtils.defaultString(redisProperties.getUrl(),
                        REDIS_PROTOCOL_PREFIX + redisProperties.getHost() + ":" + redisProperties.getPort() + REDIS_ADDRESS_SUFFIX))
                .setTimeout(null == redisProperties.getTimeout() ? DEFAULT_TIMEOUT_MILLIS : (int) redisProperties.getTimeout().toMillis())
                .setConnectTimeout(null == redisProperties.getConnectTimeout() ? DEFAULT_CONNECT_TIMEOUT_MILLIS : (int) redisProperties.getConnectTimeout().toMillis())
                .setClientName(redisProperties.getClientName());
        return Redisson.create(config);
    }

    /**
    * 创建时间序列服务实例。
    *
    * @return 时间序列服务实现实例
     */
    @Bean
    @ConditionalOnMissingBean
    public TimeSeriesService timeSeriesService() {
        /**
        * 时间series服务impl。
        * @return 时间series服务impl的结果
         */
        return new TimeSeriesServiceImpl();
    }

    /**
    * 创建 Redis 搜索服务实例，提供基于 Redis 的检索能力。
    *
    * @return Redis 搜索服务实现实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisSearchService redisSearchService() {
        /**
        * Redis搜索服务impl。
        * @return redis搜索服务impl的结果
         */
        return new RedisSearchServiceImpl();
    }

    /**
    * 创建简单 Redis 服务实例，提供指标自增/自减与 QPS 统计能力。
    *
    * @return 简单 Redis 服务实现实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SimpleRedisService simpleRedisService() {
        /**
        * 简单Redis服务impl。
        * @return 简单redis服务impl的结果
         */
        return new SimpleRedisServiceImpl();
    }

    /**
    * 创建统一 KV 引擎实例，基于 {@link StringRedisTemplate} 实现 {@link KvEngine} 接口。
    * <p>提供 get/put/delete/incr/expire/ttl 等基础 KV 操作，以及 findAllByPrefix 前缀查询。</p>
    *
    * @param stringRedisTemplate Spring 数据 Redis 字符串操作模板
    * @return KV 引擎实现实例
     */
    @Bean
    @ConditionalOnMissingBean
    public KvEngine kvEngine(StringRedisTemplate stringRedisTemplate) {
        /**
        * rediskvengine。
        * @param stringRedisTemplate 字符串redistemplate
        * @return RedisKvEngine的结果
         */
        return new RedisKvEngine(stringRedisTemplate);
    }

    /**
    * 应用上下文初始化回调，绑定 Redis 服务器配置属性并注册模块环境信息。
    *
    * @param applicationContext Spring 应用上下文
    * @throws BeansException 上下文初始化异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        redisServerProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate("plugin.redis.server", RedisServerProperties.class);
        new ModuleEnvironmentRegistration("plugin.redis.server", redisServerProperties, redisServerProperties.isEnable());
    }

    /**
    * 创建通用的 Jackson 对象映射器，忽略未知字段、注册 Java 8 时间模块并指定日期格式。
    *
    * <p>用于非 Redis 场景的通用 JSON 序列化/反序列化。</p>
    *
    * @return 配置完成的 对象映射器 实例
     */
    public static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 忽略未知字段，避免反序列化失败
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
 // 注册 Java时间module 以支持 Java 8 日期时间类型
        objectMapper.registerModule(new JavaTimeModule());
        // 统一设置日期格式为 yyyy-MM-dd HH:mm:ss
        objectMapper.setDateFormat(new SimpleDateFormat(DATE_FORMAT_PATTERN));
        return objectMapper;
    }

    /**
    * 创建 Redis 消息监听容器，绑定所有实现了 {@link RedisListener} 的 Bean。
    *
    * <p>特别说明：消息序列化对象必须放在加入消息监听器之后设置，
    * 否则会导致接收器接收不到消息（既影响发布方也影响订阅方）。</p>
    *
    * @param connectionFactory Redis 连接工厂
    * @param listeners         已注册的所有 Redis 监听器列表
    * @return 配置完成的 Redis消息监听器容器 实例
     */
    @Bean
    @ConditionalOnMissingBean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory, List<RedisListener> listeners) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        for (RedisListener listener : listeners) {
            container.addMessageListener(listener::onMessage, listener.getTopics());
        }
        // 设置消息序列化对象：必须放在加入监听器之后，否则订阅端无法收到消息
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        Jackson2JsonRedisSerializer seria = new Jackson2JsonRedisSerializer(objectMapper, Object.class);
        container.setTopicSerializer(seria);
        return container;
    }

    /**
    * 创建 redistemplate，使用 字符串Redis序列化器 序列化 钥匙、
    * genericjackson2jsonredis序列化器 序列化 价值观，支持复杂对象类型。
    *
    * <p>源码 autoConfig 中默认为 {@code <Object, Object>}，开发中一般直接使用
    * {@code <String, Object>}，本方法按此约定进行配置。</p>
    *
    * @param factory Redis 连接工厂
    * @return 配置完成的 redistemplate 实例
     */
    @Bean
    @SuppressWarnings("all")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
 // 由于源码 auto配置 中是<Object, Object>，开发中一般直接使用<String,Object>
        RedisTemplate<String, Object> template = new RedisTemplate();
        template.setConnectionFactory(factory);

 // 使用 genericjackson2jsonredis序列化器，它能更好地处理复杂类型
        GenericJackson2JsonRedisSerializer genericSerializer = new GenericJackson2JsonRedisSerializer(createRedisObjectMapper());

 // 字符串 的序列化
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

 // 钥匙 采用 字符串 的序列化
        template.setKeySerializer(stringRedisSerializer);
 // 哈希 的 钥匙 采用 字符串 的序列化
        template.setHashKeySerializer(stringRedisSerializer);
 // 价值观 序列化采用 genericjackson2jsonredis序列化器
        template.setValueSerializer(genericSerializer);
 // 哈希 的 价值观 序列化方式采用 genericjackson2jsonredis序列化器
        template.setHashValueSerializer(genericSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
    * 创建 Redis 专用的 对象映射器。
    *
    * <p>配置支持 Java 8 时间类型，启用所有字段可见性，
    * 禁用日期时间戳写法，并使用安全的类型处理。</p>
    *
    * @return 配置完成的 对象映射器 实例
    * @since 1.0.0
     */
    private static ObjectMapper createRedisObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
 // 注册 Java时间module 支持 本地日期时间 等 Java 8 时间类型
        om.registerModule(new JavaTimeModule());
        // 忽略未知字段
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 禁用将日期写为时间戳
        om.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // 统一设置日期格式为 yyyy-MM-dd HH:mm:ss
        om.setDateFormat(new SimpleDateFormat(DATE_FORMAT_PATTERN));
        return om;
    }

    /**
    * 创建 字符串redistemplate，钥匙/价值观 均使用 字符串Redis序列化器。
    *
    * <p>StringRedisTemplate 的 key/value 必须使用 StringRedisSerializer，
    * 否则 获取() 返回类型不匹配。</p>
    *
    * @param factory Redis 连接工厂
    * @return 配置完成的 字符串redistemplate 实例
     */
    @Bean("stringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
 // 字符串redistemplate 的 钥匙/价值观 必须用 字符串redis序列化器，否则 获取() 返回类型不匹配
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    /**
    * 返回当前配置类的加载优先级，使用最高优先级确保 Redis 相关 Bean 优先初始化。
    *
    * @return 优先级常量 订单.HIGHEST_PRECEDENCE
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Bean
    /**
    * Redis服务信息contributor
    *
    * @return redis服务信息contributor的结果
     */
    public ServiceInfoContributor redisServiceInfoContributor() {
        return registry -> {
            registry.registerEndpoint("Redis", "Redis服务", "/redis/**", "Redis服务");
            registry.registerFlag("redis", "Redis", true, "Redis缓存模块");
        };
    }
}
