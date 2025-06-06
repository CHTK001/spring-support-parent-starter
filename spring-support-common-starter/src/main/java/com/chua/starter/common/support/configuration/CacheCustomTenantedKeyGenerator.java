package com.chua.starter.common.support.configuration;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.annotations.ApiCacheKey;
import com.chua.starter.common.support.properties.ControlProperties;
import com.chua.starter.common.support.utils.RequestUtils;
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

/**
 * 自定义key
 *
 * @author CH
 * @since 2024/9/5
 */
@Configuration("customTenantedKeyGenerator")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("ALL")
public class CacheCustomTenantedKeyGenerator implements KeyGenerator {

    private static final String PREFIX = "cache:";

    final ControlProperties controlProperties;

    final ApplicationContext applicationContext;
    @Value("${spring.application.name:}")
    private String applicationName;

    /**
     * 删除
     *
     * @param cacheManagerName
     * @param cacheName
     * @param sysTenantId
     * @param key
     */
    public void evict(String cacheManagerName, String cacheName, String sysTenantId, String key) {
        try {
            applicationContext.getBean(cacheManagerName, CacheManager.class)
                    .getCache(cacheName)
                    .evict(getKeyPref(String.valueOf(sysTenantId)).toString() + key);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    /**
     * 删除
     *
     * @param cacheManagerName
     * @param cacheName
     * @param key
     */
    public void evict(String cacheManagerName, String cacheName, String key) {
        evict(cacheManagerName, cacheName, RequestUtils.getTenantId(), key);
    }

    /**
     * 删除
     *
     * @param cacheManagerName
     * @param cacheName
     * @param key
     */
    public void evictWithPrefix(String cacheManagerName, String cacheName, String prefix, String key) {
        evict(cacheManagerName, cacheName, RequestUtils.getTenantId(), prefix + key);
    }

    public StringBuilder getKeyPref(String tenantId) {
        StringBuilder key = new StringBuilder(PREFIX);
        key.append(applicationName).append(":");
        key.append(controlProperties.getPlatform().getName()).append(":");
        key.append(tenantId).append(":");
        return key;
    }

    /**
     * 获取key
     *
     * @return
     */
    public StringBuilder getKeyPref() {
        return getKeyPref(RequestUtils.getTenantId());
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        ApiCacheKey apiCacheKey = method.getDeclaredAnnotation(ApiCacheKey.class);
        EvaluationContext evaluationContext = createEvaluationContext(apiCacheKey, target, method, params);
        if (null == evaluationContext) {
            return SimpleKeyGenerator.generateKey(params);
        }
        StringBuilder key = getKeyPref();
        String key1 = getKeyPref(evaluationContext);
        int count = StringUtils.count(key1, "#");
        Expression exp = (new SpelExpressionParser()).parseExpression(key1, count > 1 ? new TemplateParserContext() : null);
        String expValue = exp.getValue(evaluationContext, String.class);
        key.append(expValue);
        logprint(key, evaluationContext);
        return key.toString();
    }


    private void logprint(StringBuilder key, EvaluationContext evaluationContext) {
        Object value = evaluationContext.getRootObject().getValue();
        if (value instanceof CacheableOperation cacheableOperation) {
            log.info("缓存key: {}", key);
            return;
        }

        if (value instanceof CacheEvictOperation cacheEvictOperation) {
            log.info("删除key: {}", key);
            return;
        }

        if (value instanceof CachePutOperation cachePutOperation) {
            log.info("更新key: {}", key);
            return;
        }

    }

    private String getKeyPref(EvaluationContext evaluationContext) {
        Object value = evaluationContext.getRootObject().getValue();
        if (value instanceof CacheableOperation cacheableOperation) {
            return cacheableOperation.getKey();
        }

        if (value instanceof CacheEvictOperation cacheEvictOperation) {
            return cacheEvictOperation.getKey();
        }

        if (value instanceof CachePutOperation cachePutOperation) {
            return cachePutOperation.getKey();
        }

        return "";
    }

    private EvaluationContext createEvaluationContext(ApiCacheKey apiCacheKey, Object target, Method method, Object[] params) {
        Cacheable cacheable = method.getDeclaredAnnotation(Cacheable.class);
        if (null != cacheable) {
            return createCacheableEvaluationContext(apiCacheKey, cacheable, method, params);
        }
        CacheEvict cacheEvict = method.getDeclaredAnnotation(CacheEvict.class);
        if (null != cacheEvict) {
            return createCacheEvictEvaluationContext(apiCacheKey, cacheEvict, method, params);
        }

        CachePut cachePut = method.getDeclaredAnnotation(CachePut.class);
        if (null != cachePut) {
            return createCachePutEvaluationContext(apiCacheKey, cachePut, method, params);
        }
        return null;
    }


    private EvaluationContext createCachePutEvaluationContext(ApiCacheKey apiCacheKey, CachePut cachePut, Method method, Object[] params) {
        ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();
        CachePutOperation.Builder builder = new CachePutOperation.Builder();
        builder.setKey(null != apiCacheKey ? apiCacheKey.value() : cachePut.key());
        builder.setCacheNames(cachePut.cacheNames());
        builder.setCacheManager(cachePut.cacheManager());
        builder.setCondition(cachePut.condition());
        builder.setUnless(StringUtils.defaultString(cachePut.unless(), "false"));
        builder.setCacheNames(cachePut.cacheNames());
        CachePutOperation cacheableOperation = new CachePutOperation(builder);
        return new MethodBasedEvaluationContext(cacheableOperation, method, params, paramNameDiscoverer);
    }

    private EvaluationContext createCacheEvictEvaluationContext(ApiCacheKey apiCacheKey, CacheEvict cacheEvict, Method method, Object[] params) {
        ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();
        CacheEvictOperation.Builder builder = new CacheEvictOperation.Builder();
        builder.setKey(null != apiCacheKey ? apiCacheKey.value() : cacheEvict.key());
        builder.setCacheNames(cacheEvict.cacheNames());
        builder.setCacheManager(cacheEvict.cacheManager());
        builder.setCondition(cacheEvict.condition());
        builder.setCacheNames(cacheEvict.cacheNames());
        CacheEvictOperation cacheableOperation = new CacheEvictOperation(builder);
        return new MethodBasedEvaluationContext(cacheableOperation, method, params, paramNameDiscoverer);
    }

    private EvaluationContext createCacheableEvaluationContext(ApiCacheKey apiCacheKey, Cacheable cacheable, Method method, Object[] params) {
        ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();
        CacheableOperation.Builder builder = new CacheableOperation.Builder();
        builder.setKey(null != apiCacheKey ? apiCacheKey.value() : cacheable.key());
        builder.setCacheNames(cacheable.cacheNames());
        builder.setCacheManager(cacheable.cacheManager());
        builder.setCondition(cacheable.condition());
        builder.setUnless(StringUtils.defaultString(cacheable.unless(), "false"));
        builder.setSync(cacheable.sync());
        builder.setCacheNames(cacheable.cacheNames());
        CacheableOperation cacheableOperation = new CacheableOperation(builder);
        return new MethodBasedEvaluationContext(cacheableOperation, method, params, paramNameDiscoverer);
    }


}
