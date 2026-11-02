package com.chua.starter.job.support.cache;

import com.chua.starter.job.support.store.JobEntity;

import java.util.List;
import java.util.Objects;

/**
 * 扫描结果缓存扩展点（SPI）。
 *
 * <p>{@code JobLeaderLoop} 每拍通过 {@code repository.findDue(...)} 取到期任务，实路是两次查库
 * （数据库时钟 + 候选行）。这两批数据在 TTL 内是静态的，可以跨拍次复用，于是本扩展点把
 * 「要不要缓存」收敛成两个实现：</p>
 * <ul>
 *   <li>{@link NoopScanCache} &mdash; <b>非缓存</b>。永不适配，{@code put} 丢弃，{@code size} 恒 0，
 *       等价于历史行为（每拍真实查库）。它是本扩展点的 {@code none} 键；</li>
 *   <li>{@link ScanCache} &mdash; <b>缓存</b>。按「分组 + 预读窗 + 扫描上限」缓存 TTL，
 *       命中时连相位（数据库时钟）一起复用。它是本扩展点的 {@code memory} 键。</li>
 * </ul>
 *
 * <p>注册文件：{@code META-INF/extensions/com.chua.starter.job.support.cache.JobScanCache}。
 * 接入方可以自行实现（例如 Redis / 多节点共享），只要满足下面的契约：</p>
 * <ul>
 *   <li>值必须是<b>扫描时</b>的候选行，命中方不得把它当作「新鲜行」直接写回；</li>
 *   <li>{@link #clear()} 是写侧出口的唯一失效手段，保存 / 改调度 / 启停 / 删除 / 注销 /
 *       同步 {@code next_time} / {@code refresh} 都会调用；</li>
 *   <li>{@code sys_job_id} 在库里不保证唯一，键只能按查询条件聚合，不能按行聚合。</li>
 * </ul>
 *
 * <p>默认装配在 {@code plugin.job.cache.enabled=false} 时使用 {@link NoopScanCache}，
 * 因此缓存关闭不再表现为 {@code null} 字段，调用方也不需要每个出口都判空。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface JobScanCache {

    /**
     * 缓存键：查询条件与扫描结果一一对应。
     *
     * <p><b>必须是值对象</b>：每次 {@code findDue} 都会新建一个 key 实例，
     * 而实现通常把它当 Map 键。若按对象身份比较，{@code get} 永远命不中，
     * 并且每拍都会插入一个永不释放的新条目（内存只增不减）。</p>
     *
     * @param group        扫描分组
     * @param preReadMs    预读时间窗
     * @param preReadCount 单拍扫描行数上限
     */
    final class ScanCacheKey {

        /**
         * 扫描分组
         */
        private final String group;

        /**
         * 预读时间窗
         */
        private final long preReadMs;

        /**
         * 单拍扫描行数上限
         */
        private final int preReadCount;

        /**
         * 创建缓存键。
         *
         * @param group        扫描分组
         * @param preReadMs    预读时间窗
         * @param preReadCount 单拍扫描行数上限
         */
        ScanCacheKey(String group, long preReadMs, int preReadCount) {
            this.group = group;
            this.preReadMs = preReadMs;
            this.preReadCount = preReadCount;
        }

        /**
         * 扫描分组。
         *
         * @return 分组
         */
        public String group() {
            return group;
        }

        /**
         * 预读时间窗。
         *
         * @return 毫秒数
         */
        public long preReadMs() {
            return preReadMs;
        }

        /**
         * 单拍扫描行数上限。
         *
         * @return 上限
         */
        public int preReadCount() {
            return preReadCount;
        }

        /**
         * 查询条件相等即同一个缓存键。
         *
         * <p>本方法是缓存语义的一部分，不是可选的性能优化：漏实现时命中率恒为 0 且条目只增不减。</p>
         *
         * @param o 对照对象
         * @return 条件相同返回 true
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ScanCacheKey)) {
                return false;
            }
            ScanCacheKey other = (ScanCacheKey) o;
            return preReadMs == other.preReadMs
                    && preReadCount == other.preReadCount
                    && Objects.equals(group, other.group);
        }

        /**
         * 与 {@link #equals(Object)} 保持一致的散列值。
         *
         * @return 散列值
         */
        @Override
        public int hashCode() {
            return Objects.hash(group, preReadMs, preReadCount);
        }
    }

    /**
     * 一条扫描结果。
     *
     * @param key        查询条件
     * @param dbNowMs    扫描时使用的数据库时钟
     * @param buildAtMs  缓存生成时刻（系统时钟）
     * @param candidates 候选行
     */
    final class ScanEntry {

        /**
         * 查询条件
         */
        private final ScanCacheKey key;

        /**
         * 扫描时使用的数据库时钟
         */
        private final long dbNowMs;

        /**
         * 缓存生成时刻（系统时钟）
         */
        private final long buildAtMs;

        /**
         * 候选行
         */
        private final List<JobEntity> candidates;

        /**
         * 创建一条扫描结果。
         *
         * @param key        查询条件
         * @param dbNowMs    扫描时使用的数据库时钟
         * @param buildAtMs  缓存生成时刻（系统时钟）
         * @param candidates 候选行
         */
        public ScanEntry(ScanCacheKey key, long dbNowMs, long buildAtMs, List<JobEntity> candidates) {
            this.key = key;
            this.dbNowMs = dbNowMs;
            this.buildAtMs = buildAtMs;
            this.candidates = candidates;
        }

        /**
         * 查询条件。
         *
         * @return 查询条件
         */
        public ScanCacheKey key() {
            return key;
        }

        /**
         * 扫描时使用的数据库时钟（触发相位）。
         *
         * @return 毫秒时间戳
         */
        public long dbNowMs() {
            return dbNowMs;
        }

        /**
         * 缓存生成时刻。
         *
         * @return 系统时钟毫秒数
         */
        public long buildAtMs() {
            return buildAtMs;
        }

        /**
         * 候选行。
         *
         * @return 候选行
         */
        public List<JobEntity> candidates() {
            return candidates;
        }

        /**
         * 缓存是否仍然活着（在 TTL 窗内）：活着才允许跨拍次复用。
         *
         * <p>非正的 TTL 一律视为「不缓存」：否则 {@code now - buildAtMs} 在写入的同一毫秒内
         * 可能恰好等于 0，{@code ttlMs=0} 就会在这个毫秒内命中，行为变得依赖时钟精度。</p>
         *
         * @param now   系统时钟
         * @param ttlMs 缓存有效期
         * @return 过期、TTL 非正、或没有候选行返回 false
         */
        public boolean alive(long now, long ttlMs) {
            return ttlMs > 0L && candidates != null && now - buildAtMs <= ttlMs;
        }
    }

    /**
     * 按查询条件取扫描结果。
     *
     * @param key 查询条件
     * @return 命中返回扫描结果，未命中 / 已过期返回 null
     */
    ScanEntry get(ScanCacheKey key);

    /**
     * 记录一次扫描结果（按条件 + 时钟 + 候选行），供之后 TTL 内的拍次复用。
     *
     * @param key        查询条件
     * @param dbNowMs    扫描时使用的数据库时钟
     * @param candidates 扫描结果
     */
    void put(ScanCacheKey key, long dbNowMs, List<JobEntity> candidates);

    /**
     * 失效全部扫描结果（写侧出口调用）。
     */
    void clear();

    /**
     * 仅清除与本次查询条件对应的扫描结果。
     *
     * @param key 查询条件
     */
    void remove(ScanCacheKey key);

    /**
     * 当前缓存条数，仅在统计 / 观察时才有意义。
     *
     * @return 条数
     */
    int size();

    /**
     * 缓存有效期（毫秒）。
     *
     * @return TTL 毫秒数；非缓存实现返回 0
     */
    long ttlMs();

    /**
     * 本实现是否真的会缓存扫描结果。
     *
     * <p>仅供日志 / 诊断区分「配置开了」与「默认的非缓存实现」，业务逻辑不应依赖它做分支。</p>
     *
     * @return 会缓存返回 true
     */
    default boolean caching() {
        return true;
    }

    /**
     * 构造一条查询条件对应的缓存键。
     *
     * @param group        扫描分组
     * @param preReadMs    预读时间窗
     * @param preReadCount 单拍扫描行数上限
     * @return 缓存键
     */
    default ScanCacheKey newKey(String group, long preReadMs, int preReadCount) {
        return new ScanCacheKey(group, preReadMs, preReadCount);
    }

    /**
     * 按查询条件取扫描结果。
     *
     * @param group        扫描分组
     * @param preReadMs    预读时间窗
     * @param preReadCount 每拍扫描上限
     * @return 命中返回扫描结果，未命中返回 null
     */
    default ScanEntry get(String group, long preReadMs, int preReadCount) {
        return get(newKey(group, preReadMs, preReadCount));
    }

    /**
     * 记录一次扫描结果（按条件 + 时钟 + 候选行）。
     *
     * @param group        扫描分组
     * @param preReadMs    预读时间窗
     * @param preReadCount 每拍扫描上限
     * @param dbNowMs      扫描时使用的数据库时钟
     * @param candidates   扫描结果
     */
    default void put(String group, long preReadMs, int preReadCount, long dbNowMs,
                     List<JobEntity> candidates) {
        put(newKey(group, preReadMs, preReadCount), dbNowMs, candidates);
    }
}
