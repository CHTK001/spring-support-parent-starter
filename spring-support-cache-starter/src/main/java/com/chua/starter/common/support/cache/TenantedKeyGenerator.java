package com.chua.starter.common.support.cache;

import com.chua.common.support.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * 租户缓存键生成器
 * <p>
 * 在缓存键中自动追加应用名、平台名、租户ID，支持与 @Cacheable 的 key 属性兼容。
 * 开关配置由 {@link CacheProperties.KeyProperties} 控制。
 * </p>
 *
 * @author CH
 * @since 2024/9/5
 */
public class TenantedKeyGenerator implements KeyGenerator, ApplicationContextAware, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(TenantedKeyGenerator.class);

    private ApplicationContext applicationContext;

    @Value("${spring.application.name:app}")
    private String applicationName;

    @Value("${plugin.api.platform.platform-name:default}")
    private String platformName;

    private Supplier<String> tenantIdSupplier = () -> "0";

    /**
     * 缓存键配置（由 CacheConfiguration 注入）
     */
    private CacheProperties.KeyProperties keyProperties;

    /**
     * 设置缓存键配置
     *
     * @param keyProperties 缓存键配置
     */
    public void setKeyProperties(CacheProperties.KeyProperties keyProperties) {
        this.keyProperties = keyProperties;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        // 尝试从上下文中自动绑定租户ID提供者
        try {
            Supplier<String> supplier = applicationContext.getBean(Supplier.class);
            if (supplier != null) {
                this.tenantIdSupplier = supplier;
                log.info("租户ID提供者已绑定: {}", supplier.getClass().getName());
            }
        } catch (Exception ignored) {
            // 无 Supplier Bean 时保持默认
        }
        log.info("租户缓存键生成器初始化: app={}, platform={}, withTenant={}, withApp={}, withPlatform={}",
                applicationName, platformName,
                keyProperties != null && keyProperties.isWithTenant(),
                keyProperties != null && keyProperties.isWithApplication(),
                keyProperties != null && keyProperties.isWithPlatform());
    }

    /**
     * 设置租户ID提供者
     */
    public void setTenantIdSupplier(Supplier<String> supplier) {
        if (supplier != null) {
            this.tenantIdSupplier = supplier;
        }
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder keyBuilder = new StringBuilder();

        // 前缀
        String prefix = keyProperties != null ? keyProperties.getPrefix() : "cache:";
        keyBuilder.append(prefix);

        // 应用名
        if (keyProperties == null || keyProperties.isWithApplication()) {
            keyBuilder.append(applicationName).append(":");
        }

        // 平台名
        if (keyProperties == null || keyProperties.isWithPlatform()) {
            keyBuilder.append(platformName).append(":");
        }

        // 租户ID
        if (keyProperties == null || keyProperties.isWithTenant()) {
            String tenantId = tenantIdSupplier.get();
            keyBuilder.append(tenantId).append(":");
        }

        // 后缀
        String keySuffix = SimpleKeyGenerator.generateKey(params).toString();
        keyBuilder.append(keySuffix);

        String fullKey = keyBuilder.toString();
        log.debug("生成缓存键: {}", fullKey);
        return fullKey;
    }

    /**
     * 构建带指定租户ID的缓存键
     */
    public String generateWithTenant(String tenantId, Object target, Method method, Object... params) {
        StringBuilder keyBuilder = new StringBuilder();

        String prefix = keyProperties != null ? keyProperties.getPrefix() : "cache:";
        keyBuilder.append(prefix);

        if (keyProperties == null || keyProperties.isWithApplication()) {
            keyBuilder.append(applicationName).append(":");
        }
        if (keyProperties == null || keyProperties.isWithPlatform()) {
            keyBuilder.append(platformName).append(":");
        }
        // 强制使用传入的租户ID
        keyBuilder.append(tenantId).append(":");

        String keySuffix = SimpleKeyGenerator.generateKey(params).toString();
        keyBuilder.append(keySuffix);
        return keyBuilder.toString();
    }

    /**
     * 获取当前租户ID
     */
    public String getCurrentTenantId() {
        return tenantIdSupplier.get();
    }
}