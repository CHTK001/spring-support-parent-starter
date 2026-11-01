package com.chua.starter.job.support.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chua.starter.job.support.store.JobEntity;

/**
 * 任务扫描结果缓存。
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
 * 所以命中时只取 *到期* 的行，用一条 {@code WHERE id IN (...)} 按主键批读最新行
 * 再下乐观条件，确保滚动与实时查询语义一致。</p>
 *
 * <h3>失效</h3>
 * <p>所有写侧出口（保存、改调度时间、启停、删除、注销（upsert）、同步 next_time）都失效扫描缓存，
 * 让任何状态变更都在下一拍用新相位重新扫描，不在缓存行上漂移。**
 *
 * @since 4.0.0.51
 * @author CH
 */
public class ScanCache {

    /**
     * 缓存键：查询条件与扫描结果一一对应。
     *
     * @param group     扫描分组
     * @param preReadMs 预读时间窗
     * @param preReadCount 单拍扫描行数上限
     */
    public static class ScanCacheKey {

        private final String group;
        private final long preReadMs;
        private final int preReadCount;

        ScanCacheKey(String group, long preReadMs, int preReadCount) {
            this.group = group;
            this.preReadMs = preReadMs;
            this.preReadCount = preReadCount;
        }

        String group() {
            return group;
        }

        long preReadMs() {
            return preReadMs;
        }

        int preReadCount() {
            return preReadCount;
        }
    }

    /**
     * 一条扫描结果。
     *
     * @param key      查询条件
     * @param dbNowMs  扫描时使用的数据库时钟
     * @param buildAtMs 缓存生成时刻（系统时钟）
     * @param candidates 候选行
     */
    public static class ScanEntry {

        private final ScanCacheKey key;
        private final long dbNowMs;
        private final long buildAtMs;
        private final List<JobEntity> candidates;

        ScanEntry(ScanCacheKey key, long dbNowMs, long buildAtMs, List<JobEntity> candidates) {
            this.key = key;
            this.dbNowMs = dbNowMs;
            this.buildAtMs = buildAtMs;
            this.candidates = candidates;
        }

        ScanCacheKey key() {
            return key;
        }

        long dbNowMs() {
            return dbNowMs;
        }

        long buildAtMs() {
            return buildAtMs;
        }

        List<JobEntity> candidates() {
            return candidates;
        }

        /**
         * 缓存是否仍然活着（在 TTL 窗内）：活着才允许跨拍次复用。
         *
         * @param now     系统时钟
         * @param ttlMs   缓存有效期
         * @return 过期返回 false
         */
        boolean alive(long now, long ttlMs) {
            return candidates != null && now - buildAtMs <= ttlMs;
        }
    }

    private final long ttlMs;
    private final Map<ScanCacheKey, ScanEntry> cache = new ConcurrentHashMap<>();

    /**
     * 以毫秒为单位的缓存有效期创建；过期后候选行和相位要重新查库。
     *
     * @param ttlMs 扫描结果缓存有效期（毫秒）；小于等于 0 永远悬挂，实际等于未启用
     */
    public ScanCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public ScanCacheKey newKey(String group, long preReadMs, int preReadCount) {
        return new ScanCacheKey(group, preReadMs, preReadCount);
    }

    /**
     * 取命中广播的扫描结果；已过期或不存在对应查询条件的扫描结果不命中。
     *
     * @param group 扫描分组
     * @param preReadMs 预读时间窗
     * @param preReadCount 每拍扫描上限
     * @return 命中返回扫描结果，未命中返回 null
     */
    public ScanEntry get(String group, long preReadMs, int preReadCount) {
        return get(newKey(group, preReadMs, preReadCount));
    }

    private ScanEntry get(ScanCacheKey key) {
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
     * 记录一次扫描结果（按条件 + 时钟 + 候选行），供之后 TTL 秒内的拍次复用。
     *
     * @param key 查询条件
     * @param dbNowMs 扫描时使用的数据库时钟
     * @param candidates 扫描结果
     */
    public void put(ScanCacheKey key, long dbNowMs, List<JobEntity> candidates) {
        if (key == null) {
            return;
        }
        cache.put(key, new ScanEntry(key, dbNowMs, System.currentTimeMillis(), candidates));
    }

    /**
     * 记录一次扫描结果（按条件 + 时钟 + 候选行），供之后 TTL 秒内的拍次复用。
     *
     * @param group 扫描分组
     * @param preReadMs 预读时间窗
     * @param preReadCount 每拍扫描上限
     * @param dbNowMs 扫描时使用的数据库时钟
     * @param candidates 扫描结果
     */
    public void put(String group, long preReadMs, int preReadCount, long dbNowMs, List<JobEntity> candidates) {
        put(new ScanCacheKey(group, preReadMs, preReadCount), dbNowMs, candidates);
    }

    /**
     * 失效全部扫描结果（写侧出口调用）。
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 仅清除与本次查询条件对应的扫描结果。
     *
     * @param key 查询条件
     */
    public void remove(ScanCacheKey key) {
        cache.remove(key);
    }

    /**
     * 当前缓存条数，仅在统计/观察时才有意义。
     *
     * @return 条数
     */
    public int size() {
        return cache.size();
    }

    /**
     * 缓存有效期（毫秒）。
     *
     * @return TTL 毫秒数
     */
    public long ttlMs() {
        return ttlMs;
    }
}
