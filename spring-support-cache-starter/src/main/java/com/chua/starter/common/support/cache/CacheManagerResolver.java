package com.chua.starter.common.support.cache;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.chua.starter.common.support.constant.CacheConstant.CAFFEINE_CACHE_MANAGER;
import static com.chua.starter.common.support.constant.CacheConstant.CACHE_MANAGER_FOR_MULTI;

/**
 * 主缓存管理器解析器。
 *
 * <p>Spring 的 {@code CacheAspectSupport} 在没有 {@code CacheResolver} 时，会直接
 * {@code beanFactory.getBean(CacheManager.class)} 兜底：上下文里只要存在<b>两个及以上</b>
 * {@code CacheManager} 且没有一个可解析的主管理器（{@code @Primary} 缺失或被同名 Bean 覆盖），
 * 它就抛 {@code NoUniqueBeanDefinitionException: no CacheResolver specified and expected single
 * matching CacheManager but found N}，并<b>导致整个应用启动失败</b>——是否踩中取决于类路径/配置里
 * 究竟装配了几个缓存管理器（仅 AI 模块就贡献 {@code aiCacheManager} 与两个 Redis 管理器）。</p>
 *
 * <p>本类把这段脆弱的「唯一性探测」换成确定的选择，供 {@code CachingConfigurer} 使用：
 * 先走 {@code getBean}（能唯一解析时结果与 Spring 原生完全一致，行为不变），
 * 唯一性失败时按 {@link #PREFERRED_CACHE_MANAGERS} 确定选择，保证同一份配置每次结果相同。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class CacheManagerResolver {

    /**
     * 主缓存管理器偏好顺序：多级缓存管理器（框架约定的主管理器）优先，
     * 其次 Caffeine，最后通用名 {@code cacheManager}。
     */
    private static final List<String> PREFERRED_CACHE_MANAGERS =
            List.of(CACHE_MANAGER_FOR_MULTI, CAFFEINE_CACHE_MANAGER, "cacheManager");

    /**
     * 工具类，禁止实例化。
     */
    private CacheManagerResolver() {
    }

    /**
     * 解析主缓存管理器。
     *
     * @param applicationContext 应用上下文
     * @return 主缓存管理器；上下文没有 {@code CacheManager} 时返回 {@code null}
     */
    public static CacheManager resolve(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return null;
        }
        try {
            // 唯一解析成功（单候选或 @Primary）——与 Spring 原生取值一致
            return applicationContext.getBean(CacheManager.class);
        } catch (NoUniqueBeanDefinitionException ignored) {
            // 多候选且无可用主管理器：按确定顺序选择，避免启动失败
            return pick(applicationContext.getBeansOfType(CacheManager.class));
        } catch (BeansException ignored) {
            // 没有任何 CacheManager：交由调用方按原语义处理
            return null;
        }
    }

    /**
     * 从多个候选中按确定顺序挑选一个主缓存管理器。
     *
     * @param beans Bean 名称 -&gt; 缓存管理器
     * @return 选中的缓存管理器；集合为空时返回 {@code null}
     */
    public static CacheManager pick(Map<String, CacheManager> beans) {
        if (beans == null || beans.isEmpty()) {
            return null;
        }
        for (String preferred : PREFERRED_CACHE_MANAGERS) {
            CacheManager hit = beans.get(preferred);
            if (hit != null) {
                return hit;
            }
        }
        // 兜底：按 Bean 名排序取第一个，保证同一份配置每次都选出同一个（可复现）
        return beans.entrySet().stream()
                .min(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .orElse(null);
    }
}
