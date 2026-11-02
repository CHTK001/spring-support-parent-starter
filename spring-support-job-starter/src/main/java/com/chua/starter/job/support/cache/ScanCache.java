package com.chua.starter.job.support.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.job.support.store.JobEntity;

/**
 * 任务扫描结果缓存的默认（缓存）实现。
 *
 * <p>{@code JobLeaderLoop} 每拍通过 {@code repository.findDue(now, preReadMs, preReadCount, group)}
 * 查询到期任务；每拍至少两次查库：
 * <ol>
 *   <li>{@code currentDbTimeMs()} &rarr; 数据库时钟（ multis-node 触发相位来源）；</li>
 *   <li>{@code selectList(...)} &rarr; 所有满足 {@code next_time &lt;= now + preReadMs} 的候选行（上限
 *   {@code preReadCount} 行）。</li>
 * </ol>
 * 在一次 TTL 内这两批数据是静态的（只有到期写回或管理操作才会改行），所以可以跨拍次复用，
 * 让每拍查询从 2 次降到 0~1 次（TTL 到期后重建一批）。
 *
 * <h3>缓存持有三样东西</h3>
 * <ol>
 *   <li>扫描时的数据库时钟（相位） {@code dbNowMs} — 撞上后直接使用，省掉下一拍的时钟查询；</li>
 *   <li>候选行列表 — 同一查询条件的扫描结果，这个窗内静态不变；</li>
 *   <li>缓存生成时刻 &mdash; 与 TTL 一起控制失效。</li>
 * </ol>
 *
 * <h3>命中后的写回</h3>
 * <p>缓存行是从上一次扫描读到的行；一旦有派人把 {@code sys_job_trigger_last_time/next_time}
 * 写回，候选行的乐观条件就不再是 PB 值，还拿缓存行去下条件，写回永远匹配 0 行，
 * 调度就越推越死（任务在窗口内不再被派发，直到 TTL 重建）。
 * 所以命中时只取 *到期* 的行，按主键逐行回捞最新行再下乐观条件，
 * 确保滚动与实时查询语义一致。</p>
 *
 * <h3>失效</h3>
 * <p>所有写侧出口（保存、改调度时间、启停、删除、注销（upsert）、同步 next_time）都失效扫描缓存，
 * 让任何状态变更都在下一拍用新相位重新扫描，不在缓存行上漂移。</p>
 *
 * <p>本实现是 {@link JobScanCache} 的 {@code memory} 键；对偶的
 * {@link NoopScanCache}（{@code none}）是「不缓存」那一档，两者由同一个 SPI 点选择。
 * 默认实现刻意留给 {@code none}：本类需要 TTL 才能构造，把它当兜底会让
 * 「键写错」退化成「取不到实现」。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Spi({"memory", "ttl"})
public class ScanCache implements JobScanCache {

    /**
     * 缓存有效期（毫秒）
     */
    private final long ttlMs;

    /**
     * 查询条件 &rarr; 扫描结果
     */
    private final Map<ScanCacheKey, ScanEntry> cache = new ConcurrentHashMap<>();

    /**
     * 以毫秒为单位的缓存有效期创建；过期后候选行和相位要重新查库。
     *
     * <p>参数刻意用包装类型 {@link Long}：SPI 的带参实例化按「实参运行时类型」挑构造器
     * （{@code long} 实参的类型是 {@code Long}，而 {@code long.class} 不接受 {@code Long}），
     * 声明成 {@code Long} 才能让 {@code getNewExtension("memory", ttlMs)} 命中。</p>
     *
     * @param ttlMs 扫描结果缓存有效期（毫秒）；null 或小于等于 0 时永远不活，实际等于未启用
     */
    public ScanCache(Long ttlMs) {
        this.ttlMs = ttlMs == null ? 0L : ttlMs;
    }

    @Override
    public ScanCacheKey newKey(String group, long preReadMs, int preReadCount) {
        return new ScanCacheKey(group, preReadMs, preReadCount);
    }

    /**
     * 取命中广播的扫描结果；已过期或不存在对应查询条件的扫描结果不命中。
     *
     * @param group        扫描分组
     * @param preReadMs    预读时间窗
     * @param preReadCount 每拍扫描上限
     * @return 命中返回扫描结果，未命中返回 null
     */
    @Override
    public ScanEntry get(String group, long preReadMs, int preReadCount) {
        return get(newKey(group, preReadMs, preReadCount));
    }

    @Override
    public ScanEntry get(ScanCacheKey key) {
        if (key == null) {
            return null;
        }
        ScanEntry entry = cache.get(key);
        if (entry == null || !entry.alive(System.currentTimeMillis(), ttlMs)) {
            return null;
        }
        return entry;
    }

    /**
     * 记录一次扫描结果（按条件 + 时钟 + 候选行），供之后 TTL 内的拍次复用。
     *
     * @param key        查询条件
     * @param dbNowMs    扫描时使用的数据库时钟
     * @param candidates 扫描结果
     */
    @Override
    public void put(ScanCacheKey key, long dbNowMs, List<JobEntity> candidates) {
        if (key == null) {
            return;
        }
        cache.put(key, new ScanEntry(key, dbNowMs, System.currentTimeMillis(), candidates));
    }

    /**
     * 记录一次扫描结果（按条件 + 时钟 + 候选行），供之后 TTL 内的拍次复用。
     *
     * @param group        扫描分组
     * @param preReadMs    预读时间窗
     * @param preReadCount 每拍扫描上限
     * @param dbNowMs      扫描时使用的数据库时钟
     * @param candidates   扫描结果
     */
    @Override
    public void put(String group, long preReadMs, int preReadCount, long dbNowMs, List<JobEntity> candidates) {
        put(new ScanCacheKey(group, preReadMs, preReadCount), dbNowMs, candidates);
    }

    /**
     * 失效全部扫描结果（写侧出口调用）。
     */
    @Override
    public void clear() {
        cache.clear();
    }

    /**
     * 仅清除与本次查询条件对应的扫描结果。
     *
     * @param key 查询条件
     */
    @Override
    public void remove(ScanCacheKey key) {
        if (key != null) {
            cache.remove(key);
        }
    }

    /**
     * 当前缓存条数，仅在统计/观察时才有意义。
     *
     * @return 条数
     */
    @Override
    public int size() {
        return cache.size();
    }

    /**
     * 缓存有效期（毫秒）。
     *
     * @return TTL 毫秒数
     */
    @Override
    public long ttlMs() {
        return ttlMs;
    }
}
