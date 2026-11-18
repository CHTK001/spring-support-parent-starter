package com.chua.starter.account.channel.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * 渠道模型清单缓存（账号模块自己的声明式缓存载体）。
 *
 * <p><b>缓存什么</b>：{@code sys_account_channel_model} 的只读快照，也就是「哪些渠道有哪些模型、
 * 哪些开着」这份状态。它被三处高频读取复用：管理面清单、免费渠道路由表重建、按模型名反查供应商。
 * 状态本身只在管理员操作时变化，每次请求都回表没有意义。</p>
 *
 * <p><b>为什么是 {@link CacheResolver} 而不是 {@code CacheManager} Bean</b>：宿主里通常已经存在
 * 多个 {@code CacheManager}（AI 模块的 {@code aiCacheManager} 与两个 Redis 管理器，见
 * {@code AiCacheAutoConfiguration}）。再注册一个 {@code CacheManager} 会让按类型注入
 * {@code CacheManager} 的消费方从「唯一」变成「不唯一」，在容器刷新阶段抛
 * {@code NoUniqueBeanDefinitionException}。这里只暴露一个具名 {@link CacheResolver}，
 * 由 {@code @Cacheable(cacheResolver = ...)} 显式引用，不参与任何按类型的 {@code CacheManager} 解析，
 * 宿主原有的装配结果保持不变。</p>
 *
 * <p><b>一致性</b>：模型开关、改名、删除、批量补录都在账号模块的写方法里广播
 * {@code ChannelCatalogChangedEvent}，{@link ChannelModelCacheEvictor} 在事务提交后整体失效；
 * 事务之外的直改（外部 SQL / 其它模块写表）由 {@value #TTL_MINUTES} 分钟 TTL 兜底，
 * 与 AI 侧路由快照的 5 分钟窗口同档。</p>
 *
 * @author CH
 * @since 4.0.0.59
 */
public class AccountChannelModelCache implements CacheResolver {

    /**
     * 缓存名（{@code @Cacheable(cacheNames = ...)} 必填，实际解析由本类统一给出）。
     */
    public static final String NAME = "account-channel-model";

    /**
     * 缓存解析器 Bean 名，供 {@code @Cacheable(cacheResolver = ...)} 引用。
     */
    public static final String RESOLVER = "accountChannelModelCache";

    /**
     * 缓存有效期（分钟）：与 AI 侧路由快照同档。
     */
    public static final int TTL_MINUTES = 5;

    /**
     * 条目上限：模型行总量级别，取一个不会挤掉正常条目的值。
     */
    private static final long MAX_ENTRIES = 4096L;

    /**
     * 本地缓存（Caffeine）。
     */
    private final Cache<Object, Object> nativeCache;

    /**
     * 交给 Spring Cache 的适配器。
     */
    private final org.springframework.cache.Cache springCache;

    /**
     * 构造缓存：首次使用即生效，不依赖宿主的缓存管理器。
     */
    public AccountChannelModelCache() {
        this.nativeCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(TTL_MINUTES))
                .maximumSize(MAX_ENTRIES)
                .recordStats()
                .build();
        // 不缓存 null：查询结果为空时缓存空列表，语义清晰且不再回表
        this.springCache = new CaffeineCache(NAME, nativeCache, false);
    }

    @Override
    public Collection<? extends org.springframework.cache.Cache> resolveCaches(
            CacheOperationInvocationContext<?> context) {
        return List.of(springCache);
    }

    /**
     * 整体失效：渠道目录任何一次写操作提交后调用。
     */
    public void clear() {
        nativeCache.invalidateAll();
    }
}
