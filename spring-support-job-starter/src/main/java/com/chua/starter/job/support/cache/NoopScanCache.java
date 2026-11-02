package com.chua.starter.job.support.cache;

import java.util.List;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiDefault;
import com.chua.starter.job.support.store.JobEntity;

/**
 * 非缓存实现（空对象）：永不命中、写入即丢弃。
 *
 * <p>它承担「没开缓存」这一档：{@code plugin.job.cache.enabled=false}（默认）时
 * {@code MybatisJobRepository} 装的就是本实现，而不是一个 {@code null} 字段。
 * 这样写侧出口不需要每个都判空，{@code clear} 天然是空操作，
 * 而 {@code findDue} 的实路与历史行为逐字一致（每拍真实查库）。</p>
 *
 * <p>注册名 {@code none}；{@code noop} / {@code off} 是同义键。SPI 注册文件见
 * {@code META-INF/extensions/com.chua.starter.job.support.cache.JobScanCache}。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@SpiDefault
@Spi({"none", "noop", "off"})
public class NoopScanCache implements JobScanCache {

    /**
     * 无状态单例：本实现不持有任何数据，装配侧共用一个实例即可。
     */
    public static final NoopScanCache INSTANCE = new NoopScanCache();

    /**
     * 创建非缓存实现。
     */
    public NoopScanCache() {
    }

    @Override
    public ScanEntry get(ScanCacheKey key) {
        return null;
    }

    @Override
    public void put(ScanCacheKey key, long dbNowMs, List<JobEntity> candidates) {
        // no-op: 非缓存档不保存扫描结果，下一拍由实路重新查库
    }

    @Override
    public void clear() {
        // no-op: 本实现没有需要失效的数据
    }

    @Override
    public void remove(ScanCacheKey key) {
        // no-op: 本实现没有需要失效的数据
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public long ttlMs() {
        return 0L;
    }

    @Override
    public boolean caching() {
        return false;
    }
}
