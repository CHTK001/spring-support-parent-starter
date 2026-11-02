package com.chua.starter.job.support.cache;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.job.support.JobProperties;

/**
 * {@link JobScanCache} 的装配入口：把 {@code plugin.job.cache.*} 翻译成 SPI 键并取实现。
 *
 * <p>两档语义与配置一一对应：</p>
 * <ul>
 *   <li>{@code enabled=false}（默认）&rarr; {@code none}（{@link NoopScanCache}）：
 *       每拍真实查库，与历史行为一致；</li>
 *   <li>{@code enabled=true} 且 {@code ttl-ms > 0} &rarr; {@code memory}（{@link ScanCache}）：
 *       扫描结果与相位在 TTL 内跨拍次复用。</li>
 * </ul>
 *
 * <p>{@code enabled=true} 但 {@code ttl-ms <= 0} 时同样落到 {@code none}：TTL 非正数的缓存
 * 永远不活，语义上就是「没开」，直接选空对象比留一个恒不命中的缓存更容易解释（少一次
 * 无意义的 {@code put}）。</p>
 *
 * <p>取实现一律带兜底：SPI 注册缺失、类加载失败、构造器签名不匹配时都退回直接 {@code new}，
 * 保证「缓存配置」这个纯优化项不会把整个自动配置拖挂。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class JobScanCaches {

    /**
     * 缓存档的 SPI 键
     */
    public static final String KEY_MEMORY = "memory";

    /**
     * 非缓存档的 SPI 键
     */
    public static final String KEY_NONE = "none";

    /**
     * 工具类，不实例化。
     */
    private JobScanCaches() {
    }

    /**
     * 按配置解析扫描缓存实现。
     *
     * @param config 缓存配置，允许为 null（按默认关闭处理）
     * @return 非缓存实现或缓存实现，永不为 null
     */
    public static JobScanCache resolve(JobProperties.CacheConfig config) {
        boolean enabled = config != null && config.isEnabled();
        long ttlMs = config == null ? 0L : config.getTtlMs();
        if (!enabled || ttlMs <= 0L) {
            return resolveNoop();
        }
        return resolveMemory(ttlMs);
    }

    /**
     * 非缓存档：永不命中、写入即丢弃。
     *
     * @return 非缓存实现
     */
    public static JobScanCache resolveNoop() {
        JobScanCache spi = newExtension(KEY_NONE, new Object[0]);
        return spi == null ? NoopScanCache.INSTANCE : spi;
    }

    /**
     * 缓存档：按 TTL 缓存扫描结果。
     *
     * <p>SPI 取到的实现若不是缓存档（例如注册缺失后回落到 {@code @SpiDefault} 的非缓存实现），
     * 就本地 {@code new} 一个缓存实现：配置说「开」，就不该静默退化成不缓存。</p>
     *
     * @param ttlMs 缓存有效期（毫秒），调用方保证为正
     * @return 缓存实现
     */
    public static JobScanCache resolveMemory(long ttlMs) {
        JobScanCache spi = newExtension(KEY_MEMORY, new Object[]{ttlMs});
        if (spi != null && spi.caching()) {
            return spi;
        }
        return new ScanCache(ttlMs);
    }

    /**
     * 经 SPI 取一个新实例。
     *
     * <p>带参实例化失败（自定义实现没有单体 TTL 构造器、被 SPI 内部吞掉异常）时回落无参；
     * 仍取不到就返回 null，由调用方决定兜底实现。</p>
     *
     * @param key  SPI 键
     * @param args 构造参数
     * @return 实例，取不到返回 null
     */
    private static JobScanCache newExtension(String key, Object[] args) {
        ServiceProvider<JobScanCache> provider = ServiceProvider.of(JobScanCache.class);
        try {
            Object[] actual = (args == null || args.length == 0) ? null : args;
            JobScanCache instance = actual == null
                    ? provider.getNewExtension(key)
                    : provider.getNewExtension(key, actual);
            if (instance != null) {
                return instance;
            }
        } catch (RuntimeException ex) {
            // 落到无参尝试或本地兜底，异常细节对配置无关的调用方没有价值
        }
        try {
            return provider.getExtension(key);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
