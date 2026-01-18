package com.chua.starter.strategy.cache;

import com.chua.common.support.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.interceptor.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * 租户缓存键生成器
 * <p>
 * 支持同时使用 key 和 keyGenerator，自动添加租户前缀。
 * </p>
 *
 * <h3>缓存键格式：</h3>
 * <pre>
 * cache:{applicationName}:{platform}:{tenantId}:{key表达式的值}
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/9/5
 */
@Configuration("tenantedKeyGenerator")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("ALL")
public class TenantedKeyGenerator implements KeyGenerator {

    private static final String PREFIX = "cache:";
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAM_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final ApplicationContext applicationContext;

    @Value("${spring.application.name:app}")
    private String applicationName;

    @Value("${plugin.api.platform.platform-name:default}")
    private String platformName;

    /**
     * 租户ID提供者（可通过配置或自定义实现）
     */
    private Supplier<String> tenantIdSupplier = () -> "0";

    /**
     * 设置租户ID提供者
     */
    public void setTenantIdSupplier(Supplier<String> supplier) {
        this.tenantIdSupplier = supplier;
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        EvaluationContext evaluationContext = createEvaluationContext(target, method, params);
        
        if (evaluationContext == null) {
            return SimpleKeyGenerator.generateKey(params);
        }

        StringBuilder key = getKeyPrefix();
        String keyExpression = getKeyExpression(evaluationContext);
        
        if (StringUtils.isNotBlank(keyExpression)) {
            int count = StringUtils.count(keyExpression, "#");
            Expression exp = PARSER.parseExpression(keyExpression, count > 1 ? new TemplateParserContext() : null);
            String expValue = exp.getValue(evaluationContext, String.class);
            key.append(expValue);
        } else {
            key.append(SimpleKeyGenerator.generateKey(params));
        }

        log.debug("生成缓存键: {}", key);
        return key.toString();
    }

    /**
     * 获取缓存键前缀
     */
    public StringBuilder getKeyPrefix() {
        return getKeyPrefix(tenantIdSupplier.get());
    }

    /**
     * 获取指定租户的缓存键前缀
     */
    public StringBuilder getKeyPrefix(String tenantId) {
        StringBuilder key = new StringBuilder(PREFIX);
        key.append(applicationName).append(":");
        key.append(platformName).append(":");
        key.append(tenantId).append(":");
        return key;
    }

    /**
     * 删除缓存
     */
    public void evict(String cacheManagerName, String cacheName, String key) {
        evict(cacheManagerName, cacheName, tenantIdSupplier.get(), key);
    }

    /**
     * 删除指定租户的缓存
     */
    public void evict(String cacheManagerName, String cacheName, String tenantId, String key) {
        try {
            String fullKey = getKeyPrefix(tenantId).append(key).toString();
            applicationContext.getBean(cacheManagerName, CacheManager.class)
                    .getCache(cacheName)
                    .evict(fullKey);
            log.debug("删除缓存: manager={}, cache={}, key={}", cacheManagerName, cacheName, fullKey);
        } catch (Exception e) {
            log.warn("删除缓存失败: manager={}, cache={}, key={}", cacheManagerName, cacheName, key, e);
        }
    }

    private EvaluationContext createEvaluationContext(Object target, Method method, Object[] params) {
        Cacheable cacheable = method.getDeclaredAnnotation(Cacheable.class);
        if (cacheable != null) {
            return createCacheableContext(cacheable, method, params);
        }

        CacheEvict cacheEvict = method.getDeclaredAnnotation(CacheEvict.class);
        if (cacheEvict != null) {
            return createCacheEvictContext(cacheEvict, method, params);
        }

        CachePut cachePut = method.getDeclaredAnnotation(CachePut.class);
        if (cachePut != null) {
            return createCachePutContext(cachePut, method, params);
        }

        return null;
    }

    private String getKeyExpression(EvaluationContext context) {
        Object value = context.getRootObject().getValue();
        
        if (value instanceof CacheableOperation op) {
            return op.getKey();
        }
        if (value instanceof CacheEvictOperation op) {
            return op.getKey();
        }
        if (value instanceof CachePutOperation op) {
            return op.getKey();
        }
        
        return "";
    }

    private EvaluationContext createCacheableContext(Cacheable cacheable, Method method, Object[] params) {
        CacheableOperation.Builder builder = new CacheableOperation.Builder();
        builder.setKey(cacheable.key());
        builder.setCacheNames(cacheable.cacheNames());
        builder.setCacheManager(cacheable.cacheManager());
        builder.setCondition(cacheable.condition());
        builder.setUnless(StringUtils.defaultString(cacheable.unless(), "false"));
        builder.setSync(cacheable.sync());
        return new MethodBasedEvaluationContext(builder.build(), method, params, PARAM_NAME_DISCOVERER);
    }

    private EvaluationContext createCacheEvictContext(CacheEvict cacheEvict, Method method, Object[] params) {
        CacheEvictOperation.Builder builder = new CacheEvictOperation.Builder();
        builder.setKey(cacheEvict.key());
        builder.setCacheNames(cacheEvict.cacheNames());
        builder.setCacheManager(cacheEvict.cacheManager());
        builder.setCondition(cacheEvict.condition());
        return new MethodBasedEvaluationContext(builder.build(), method, params, PARAM_NAME_DISCOVERER);
    }

    private EvaluationContext createCachePutContext(CachePut cachePut, Method method, Object[] params) {
        CachePutOperation.Builder builder = new CachePutOperation.Builder();
        builder.setKey(cachePut.key());
        builder.setCacheNames(cachePut.cacheNames());
        builder.setCacheManager(cachePut.cacheManager());
        builder.setCondition(cachePut.condition());
        builder.setUnless(StringUtils.defaultString(cachePut.unless(), "false"));
        return new MethodBasedEvaluationContext(builder.build(), method, params, PARAM_NAME_DISCOVERER);
    }
}
