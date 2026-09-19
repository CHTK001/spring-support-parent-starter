package com.chua.starter.common.support.cache;

/**
 * 租户标识提供者接口
 * <p>
 * 用于从当前请求上下文中获取租户标识，由 {@link com.chua.starter.common.support.cache.TenantedKeyGenerator}
 * 在生成缓存键时自动调用，确保多租户场景下缓存键天然隔离。
 * </p>
 *
 * <h3>使用方式：</h3>
 * <pre>{@code
 * // OAuth 模块实现该接口，读取请求头 X-Tenant-Id
 * @Component
 * public class OAuthTenantIdProvider implements TenantIdProvider {
 *     @Override
 *     public String getCurrentTenantId() {
 *         return RequestUtils.getCurrentTenantId();
 *     }
 * }
 * }</pre>rn RequestUtils.getCurrentTenantId();
 *     }
 * }
 * }</pre>
 *
 * <p>当 Spring 容器中存在 {@link TenantIdProvider} Bean 时，
 * {@link TenantedKeyGenerator} 会自动装配并使用；否则回退到默认值 {@code "0"}。</p>
 *
 * @since 2024/9/5
 * @author CH
 */
@FunctionalInterface
public interface TenantIdProvider {

    /**
     * 获取当前请求的租户标识
     *
     * @return 租户ID，不可返回 {@code null}
     */
    String getCurrentTenantId();
}
