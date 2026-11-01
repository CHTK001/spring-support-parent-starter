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
 * 查询到期任务；这拍需要两次查库：
 * <ol>
 *   <li>{@code currentDbTimeMs()} &rarr; 数据库时钟（并统一跨节点的触发相位）；</li>
 *   <li>{@code selectList(...)} &rarr; 全部满足 {@code next_time <= now + preReadMs} 的候选行（上限 {@code preReadCount} 行）。</li>
 * </ol>
 * 在一次 TTL 内，候选行集合与数据库时钟不会变（只有到期写回或管理操作才会改行），所以可以跨拍次复用，
 * 把单拍查询从两次压到一次（用于验证到期行更新状态的按主键重读），查询量 задач % 随 TTL 线性下降。</p>
 *
 * <h3>缓存持有三样东西</h3>
 * <ol>
 *   <li>扫描时的数据库时钟 / 相位（{@code dbNowMs}）&mdash; 撞上后直接拿来当相位，省掉下一拍的时钟查询；</li>
 *   <li>候选行列表 &mdash; 同一查询条件的扫描结果，这个窗内它们是静态的；</li>
 *   <li>缓存生成时刻 &mdash; 与 TTL 一起控制失效。</li>
 * </ol>
 *
 * <h3>命中后的写回</h3>
 * <p>缓存行是从上一次扫描读到的行；这拍（或别阶段）把表写回后，候选行的
 * {@code sys_job_trigger_last_time / sys_job_trigger_next_time} 已不是缓存值，
 * 如果还拿缓存行去下乐观省略条件，写回永远匹配 0 行，调度就越推越死。</p>
 * 所以命中的缓存扫描里，只有到期的行才用主键按 {@code SELECT * WHERE id IN (...)} 最新重读一次，
 * 未到期行（没有写回需求）继续用缓存行。
 *
 * <h3>失效</h3>
 * <p>消息缓存应当被所有 . 写侧操作清除：保存、修改、停启、删除、注销和同步（
 * 这些入口之后关于行状态都以库为准，不在缓存行上航标）。
 *
 * @since 4.0.0.51
 * @author CH
 */
public class ScanCache {

    /**
     * 缓存键：查询条件与扫描结果一一对应。
     *
     * @param group 扫描分组
     * @param preReadMs 预读时间窗
     * @param preReadCount 单拍扫描行数上限
     */
    public record ScanCacheKey(String group, long preReadMs, int preReadCount) {
    }

    /**
     * 一条扫描结果。
     *
     * @param dbNowMs 扫描时（调用方给完参后换算为）的数据库时钟
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

        public ScanCacheKey key() {
            return key;
        }

        public long dbNowMs() {
            return dbNowMs;
        }

        public long buildAtMs() {
            return buildAtMs;
        }

        public List<JobEntity> candidates() {
            return candidates;
        }

        /**
         * 还需要查库吗？还没有过期，任意一次拍次调用都不需要查库。
         *
         * @param now 当前系统时钟
         * @param ttlMs 缓存有效期
         * @return 过期返回 false
         */
        public boolean alive(long now, long ttlMs) {
            return candidates != null && now - buildAtMs <= ttlMs;
        }
    }

    private final long ttlMs;
    private final Map<ScanCacheKey, ScanEntry> cache = new ConcurrentHashMap<>();

    /**
     * 以毫秒为单位的缓存有效期创建，过期后候选行和相位要重新查库。
     *
     * @param ttlMs 扫描结果缓存有效期
     */
    public ScanCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public ScanCacheKey newKey(String group, long preReadMs, int preReadCount) {
        return new ScanCacheKey(group, preReadMs, preReadCount);
    }

    /**
     * 取命中广播的扫描结果，已过期或没有对应查询条件的扫描结果不命中。
     *
     * @param group 扫描分组
     * @param preReadMs 预读时间窗
     * @param preReadCount 每拍扫描上限
     * @return 命中返回扫描结果，未命中/null らず
     */
    public ScanEntry get(String group, long preReadMs, int preReadCount) {
        return get(new ScanCacheKey(group, preReadMs, preReadCount));
    }

    private ScanEntry get(ScanCacheKey key) {
        ScanEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        return entry;
    }

    /**
     * 记录一次扫描结果（按条件 + 时钟 + 候选行）。
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
     * 失效全部扫描结果（写侧出口调用）。
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 清除与本扫描条件匹配的那一组（按参数）——误差不可控，只清同一条件组。
     *
     * @param key 查询条件
     */
    public void remove(ScanCacheKey key) {
        cache.remove(key);
    }

    /**
     * 缓存容量，供统计/指示使用；不在关键路径上。
     *
     * @return 条数
     */
    public int size() {
        return cache.size();
    }

    /**
     * 返回当前的 TTL（毫秒）。
     *
     * @return TTL（毫秒）
     */
    public long ttlMs() {
        return ttlMs;
    }
}
