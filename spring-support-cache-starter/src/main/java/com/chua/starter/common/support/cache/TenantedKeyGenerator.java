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

/**
 * 租户缓存键生成器
 * <p>
   * 在缓存键中自动追加应用名、平台名、租户标识，支持与 @缓存 的 钥匙 属性兼容。
 * 开关配置由 {@link CacheProperties.KeyProperties} 控制。
 * </p>
 *
 * <h3>租户ID提供方式：</h3>
 * <ul>
 *   <li>若 Spring 容器中存在 {@link TenantIdProvider} Bean，则自动装配并从请求上下文获取租户ID。</li>
 *   <li>若不存在，则使用默认值 {@code "0"}（单租户场景）。</li>
 * </ul>
 *
 * @since 2024/9/5
 * @author CH
 */
public class TenantedKeyGenerator implements KeyGenerator, ApplicationContextAware, InitializingBean {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(TenantedKeyGenerator.class);

    /** Application上下文 */
    private ApplicationContext applicationContext;

    @Value("${spring.application.name:app}")
    /** Application名称 */
    private String applicationName;

    @Value("${plugin.api.platform.platform-name:default}")
    /** Platform名称 */
    private String platformName;

    /**
       * 租户标识提供者（由 缓存配置 自动注入）
     */
    private TenantIdProvider tenantIdProvider;

    /**
      * 缓存键配置（由 缓存配置 注入）
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

    /**
      * 设置租户标识提供者
     *
     * @param tenantIdProvider 租户标识提供者
     */
    public void setTenantIdProvider(TenantIdProvider tenantIdProvider) {
        this.tenantIdProvider = tenantIdProvider;
    }

    @Override
    /** 设置application上下文 */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    /** 之后属性设置 */
    public void afterPropertiesSet() {
 // 尝试从上下文中自动绑定租户标识提供者
        if (tenantIdProvider == null) {
            try {
                tenantIdProvider = applicationContext.getBean(TenantIdProvider.class);
                log.info("租户ID提供者已自动装配: {}", tenantIdProvider.getClass().getName());
            } catch (Exception ignored) {
 // 无 tenantid提供者 Bean 时保持默认
            }
        }
        log.info("租户缓存键生成器初始化: app={}, platform={}, withTenant={}, withApp={}, withPlatform={}",
                applicationName, platformName,
                keyProperties != null && keyProperties.isWithTenant(),
                keyProperties != null && keyProperties.isWithApplication(),
                keyProperties != null && keyProperties.isWithPlatform());
    }

    /**
      * 获取当前租户标识
     *
     * @return 租户ID，由 {@link TenantIdProvider} 提供；若未设置则返回 {@code "0"}
     */
    private String resolveTenantId() {
        if (tenantIdProvider != null) {
            String tenantId = tenantIdProvider.getCurrentTenantId();
            if (tenantId != null && !tenantId.isEmpty()) {
                return tenantId;
            }
        }
        return "0";
    }

    @Override
    /** Generate */
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

 // 租户标识
        if (keyProperties == null || keyProperties.isWithTenant()) {
            keyBuilder.append(resolveTenantId()).append(":");
        }

        // 后缀
        String keySuffix = SimpleKeyGenerator.generateKey(params).toString();
        keyBuilder.append(keySuffix);

        String fullKey = keyBuilder.toString();
        log.debug("生成缓存键: {}", fullKey);
        return fullKey;
    }

    /**
      * 构建带指定租户标识的缓存键
     *
     * @param tenantId 指定的租户标识
     * @param target   目标对象
     * @param method   目标方法
     * @param params   方法参数
     * @return 完整的缓存键字符串
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
 // 强制使用传入的租户标识
        keyBuilder.append(tenantId).append(":");

        String keySuffix = SimpleKeyGenerator.generateKey(params).toString();
        keyBuilder.append(keySuffix);
        return keyBuilder.toString();
    }

    /**
      * 获取当前租户标识（供外部使用）
     *
     * @return 当前租户ID
     */
    public String getCurrentTenantId() {
        return resolveTenantId();
    }

    /**
     * 获取当前应用名（供外部使用）
     *
     * @return 应用名
     */
    public String getApplicationName() {
        return applicationName;
    }

    /**
     * 获取当前平台名（供外部使用）
     *
     * @return 平台名
     */
    public String getPlatformName() {
        return platformName;
    }
}
