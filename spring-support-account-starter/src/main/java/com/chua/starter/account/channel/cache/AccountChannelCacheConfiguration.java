package com.chua.starter.account.channel.cache;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;

/**
 * 渠道模型状态缓存自动配置。
 *
 * <p>账号模块的渠道模型清单（哪些渠道有哪些模型、哪些开着）是高频只读数据：
 * 管理面清单、免费渠道路由表重建、按模型名反查供应商都会读它。本配置把它放进
 * {@link AccountChannelModelCache}，读走 {@code @Cacheable}，写由
 * {@link ChannelModelCacheEvictor} 在事务提交后失效。</p>
 *
 * <p><b>不注册 {@code CacheManager}</b>：见 {@link AccountChannelModelCache} 的说明——
 * 宿主已有多个缓存管理器时，再加一个会把按类型注入变成不唯一。这里只提供具名
 * {@link CacheResolver} 与被 {@code @Cacheable} 显式引用的缓存。</p>
 *
 * <p><b>为什么在本模块打开 {@code @EnableCaching}</b>：声明式缓存必须有人开启，
 * 否则 {@code @Cacheable} 会被静默忽略，等于「写了缓存但从来没生效」。</p>
 *
 * <p><b>为什么不提供开关</b>：{@code @Cacheable} 上用 bean 名引用了这里的解析器，
 * 一旦把本配置整体关掉，注解仍在但解析器不存在，调用时会抛
 * {@code No CacheResolver found with name 'accountChannelModelCache'}——
 * 关掉开关不是「退化成回表」，而是直接失败。要么全开，要么不留开关。</p>
 *
 * @author CH
 * @since 4.0.0.59
 */
@AutoConfiguration
@EnableCaching
@ConditionalOnClass(CacheResolver.class)
public class AccountChannelCacheConfiguration {

    /**
     * 渠道模型清单缓存。
     *
     * @return 缓存解析器（同时是缓存本体）
     */
    @Bean(AccountChannelModelCache.RESOLVER)
    public AccountChannelModelCache accountChannelModelCache() {
        return new AccountChannelModelCache();
    }

    /**
     * 渠道目录变更后的缓存失效器。
     *
     * @param modelCache 渠道模型清单缓存
     * @return 失效器
     */
    @Bean
    public ChannelModelCacheEvictor channelModelCacheEvictor(AccountChannelModelCache modelCache) {
        return new ChannelModelCacheEvictor(modelCache);
    }
}
