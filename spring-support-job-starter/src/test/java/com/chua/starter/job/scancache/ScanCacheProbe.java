package com.chua.starter.job.scancache;

import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.cache.JobScanCache;
import com.chua.starter.job.support.cache.JobScanCaches;
import com.chua.starter.job.support.cache.NoopScanCache;
import com.chua.starter.job.support.cache.ScanCache;
import com.chua.starter.job.support.store.JobEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描结果缓存（{@link JobScanCache} SPI）的契约探针。
 *
 * <p>这类探针<b>不需要 MySQL / Spring 容器</b>，只验证缓存自身的语义：
 * 命中、过期、条件隔离、失效、「同一查询条件不会每拍新增一条」，以及
 * 「缓存档 / 非缓存档」两档 SPI 实现是否各就各位。</p>
 *
 * <p>运行方式（纯 JDK，无 JUnit）：</p>
 * <pre>
 * java -cp "target/classes;target/test-classes" com.chua.starter.job.scancache.ScanCacheProbe
 * </pre>
 *
 * <p>之所以要这个探针：缓存关闭（{@code plugin.job.cache.enabled=false}，默认）时装的应当是
 * {@link NoopScanCache}，它若退化成 {@code null} 则每次 {@code clear()/put()} 都在<b>每一拍</b>
 * 抛 NPE；而 {@code ScanCacheKey} 若漏了 {@code equals/hashCode}，每次 {@code findDue}
 * 新建的 key 永远命不中，map 每拍还会多一条永不释放的条目。
 * 这两条都只能在没有数据库的情况下验证。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public class ScanCacheProbe {

    private static int passed;
    private static int failed;

    /**
     * 探针入口。
     *
     * @param args 忽略
     */
    public static void main(String[] args) {
        say("A. 同一查询条件在 TTL 内必须命中（key 的身份不能是对象地址）");
        caseHitWithinTtl();

        say("B. 超过 TTL 必须失效（不返回上一批陈旧候选行）");
        caseExpiredMiss();

        say("C. 查询条件不同不得串味（分组 / 预读窗 / 上限任一不同即不同键）");
        caseKeyIsolation();

        say("D. 同一查询条件反复 put 不得每拍新增一条（ConcurrentHashMap 不能无限增长）");
        caseNoEntryLeak();

        say("E. clear / remove 之后必须立即不命中（写侧出口靠它做失效）");
        caseInvalidation();

        say("F. TTL <= 0 永远不活（等效关闭，与 MybatisJobRepository 的注释一致）");
        caseNonPositiveTtl();

        say("G. 非缓存档（NoopScanCache）必须永不命中且写入即丢弃");
        caseNoopSemantics();

        say("H. 配置 -> SPI 键的映射：enabled=false / ttl<=0 走非缓存，否则走缓存");
        caseConfigMapping();

        say("I. SPI 注册文件里两档键都必须可取到实现");
        caseSpiRegistration();

        System.out.println();
        System.out.println("==== 结果: passed=" + passed + " failed=" + failed + " ====");
        if (failed > 0) {
            throw new IllegalStateException("扫描缓存探针失败 " + failed + " 条");
        }
    }

    /**
     * A. TTL 内命中：同一组参数两次 get 必须拿到同一批候选行。
     */
    private static void caseHitWithinTtl() {
        ScanCache cache = new ScanCache(5000L);
        List<JobEntity> rows = rows(2);
        cache.put("payment", 5000L, 10, 1_000_000L, rows);
        JobScanCache.ScanEntry hit = cache.get("payment", 5000L, 10);
        check("A1 同一条件在 TTL 内命中", hit != null, "hit=" + hit);
        check("A2 命中的是上一次扫描的候选行", hit != null && hit.candidates().size() == 2,
                hit == null ? "hit=null" : "size=" + hit.candidates().size());
        check("A3 命中带回了扫描相位（数据库时钟）", hit != null && hit.dbNowMs() == 1_000_000L,
                hit == null ? "hit=null" : "phase=" + hit.dbNowMs());
    }

    /**
     * B. 过期后不命中：TTL 收缩到 0 毫秒时必然已过期。
     */
    private static void caseExpiredMiss() {
        ScanCache cache = new ScanCache(0L);
        cache.put("payment", 5000L, 10, 1_000_000L, rows(1));
        // buildAtMs 与 now 至少差几毫秒，0ms 的 TTL 一定过期；偶尔同一毫秒时再等一拍
        JobScanCache.ScanEntry first = cache.get("payment", 5000L, 10);
        if (first != null) {
            sleep(5);
        }
        JobScanCache.ScanEntry after = cache.get("payment", 5000L, 10);
        check("B1 TTL=0 时不命中（等效关闭）", after == null, "after=" + after);
    }

    /**
     * C. 条件隔离：三个维度任一不同都不得命中。
     */
    private static void caseKeyIsolation() {
        ScanCache cache = new ScanCache(60_000L);
        cache.put("payment", 5000L, 10, 1_000_000L, rows(1));
        check("C1 分组不同不命中", cache.get("settle", 5000L, 10) == null,
                "settle 不应命中 payment 的缓存");
        check("C2 预读窗不同不命中", cache.get("payment", 6000L, 10) == null,
                "preReadMs=6000 不应命中 preReadMs=5000 的缓存");
        check("C3 扫描上限不同不命中", cache.get("payment", 5000L, 20) == null,
                "preReadCount=20 不应命中 preReadCount=10 的缓存");
        check("C4 原条件仍然命中（隔离未误伤）", cache.get("payment", 5000L, 10) != null,
                "原条件的缓存被误删");
    }

    /**
     * D. 不得泄漏条目：同一条件反复 put 只应保留一条。
     *
     * <p>这是 {@code ScanCacheKey} 漏实现 {@code equals/hashCode} 时最典型的表现：
     * 每拍 {@code findDue} 都会 {@code new ScanCacheKey(...)}，身份比较下 map 只增不减。</p>
     */
    private static void caseNoEntryLeak() {
        ScanCache cache = new ScanCache(60_000L);
        for (int tick = 0; tick < 100; tick++) {
            cache.put("payment", 5000L, 10, 1_000_000L + tick, rows(3));
            // 每拍都像真实调度那样先查一次
            checkSilently(cache.get("payment", 5000L, 10));
        }
        check("D1 同一条件 100 拍后仍只有 1 条缓存", cache.size() == 1, "size=" + cache.size());
    }

    /**
     * E. 失效：clear 清空全部，remove 只清指定条件。
     */
    private static void caseInvalidation() {
        ScanCache cache = new ScanCache(60_000L);
        cache.put("payment", 5000L, 10, 1_000_000L, rows(1));
        cache.put("settle", 5000L, 10, 1_000_000L, rows(1));
        check("E1 两种条件各自命中", cache.size() == 2, "size=" + cache.size());
        cache.clear();
        check("E2 clear 后全部不命中（写侧出口靠它失效）",
                cache.get("payment", 5000L, 10) == null && cache.get("settle", 5000L, 10) == null,
                "size=" + cache.size());
        cache.put("payment", 5000L, 10, 1_000_000L, rows(1));
        cache.put("settle", 5000L, 10, 1_000_000L, rows(1));
        cache.remove(cache.newKey("payment", 5000L, 10));
        check("E3 remove 只清指定条件",
                cache.get("payment", 5000L, 10) == null && cache.get("settle", 5000L, 10) != null,
                "size=" + cache.size());
    }

    /**
     * F. TTL <= 0 一律不命中，即使刚刚写入。
     */
    private static void caseNonPositiveTtl() {
        ScanCache negative = new ScanCache(-1L);
        negative.put("payment", 5000L, 10, 1_000_000L, rows(1));
        check("F1 负 TTL 不命中", negative.get("payment", 5000L, 10) == null, "负 TTL 竟命中");
        ScanCache nullTtl = new ScanCache((Long) null);
        nullTtl.put("payment", 5000L, 10, 1_000_000L, rows(1));
        check("F2 null TTL 不命中（SPI 无参构造的兜底值）", nullTtl.get("payment", 5000L, 10) == null,
                "null TTL 竟命中");
    }

    /**
     * G. 非缓存档：永不命中、写入即丢弃、失效是空操作。
     *
     * <p>这条守的是「默认关闭时不是 {@code null}」：{@code clear/put} 必须能无条件调用。</p>
     */
    private static void caseNoopSemantics() {
        JobScanCache noop = new NoopScanCache();
        noop.put("payment", 5000L, 10, 1_000_000L, rows(2));
        check("G1 非缓存档写入后仍不命中", noop.get("payment", 5000L, 10) == null, "noop 竟命中");
        check("G2 非缓存档条数恒为 0", noop.size() == 0, "size=" + noop.size());
        check("G3 非缓存档 TTL 恒为 0", noop.ttlMs() == 0L, "ttl=" + noop.ttlMs());
        check("G4 非缓存档 caching() 为 false", !noop.caching(), "caching=" + noop.caching());
        noop.clear();
        noop.remove(noop.newKey("payment", 5000L, 10));
        check("G5 非缓存档 clear/remove 不抛异常（写侧出口可无条件调用）", true, "");
    }

    /**
     * H. 配置映射：默认关闭与非正 TTL 走非缓存档，开启且 TTL 为正才走缓存档。
     */
    private static void caseConfigMapping() {
        JobProperties.CacheConfig disabled = new JobProperties.CacheConfig();
        disabled.setEnabled(false);
        check("H1 默认配置（关闭）解析为非缓存档", !JobScanCaches.resolve(disabled).caching(),
                "caching=" + JobScanCaches.resolve(disabled).caching());

        JobProperties.CacheConfig enabled = new JobProperties.CacheConfig();
        enabled.setEnabled(true);
        enabled.setTtlMs(5000L);
        JobScanCache caching = JobScanCaches.resolve(enabled);
        check("H2 开启且 TTL>0 解析为缓存档", caching.caching(), "caching=" + caching.caching());
        check("H3 缓存档带回配置的 TTL", caching.ttlMs() == 5000L, "ttl=" + caching.ttlMs());

        JobProperties.CacheConfig zeroTtl = new JobProperties.CacheConfig();
        zeroTtl.setEnabled(true);
        zeroTtl.setTtlMs(0L);
        check("H4 开启但 TTL=0 落到非缓存档（恒不活 = 没开）",
                !JobScanCaches.resolve(zeroTtl).caching(),
                "caching=" + JobScanCaches.resolve(zeroTtl).caching());

        check("H5 配置为 null 落到非缓存档（不抛 NPE）", !JobScanCaches.resolve(null).caching(), "");
    }

    /**
     * I. SPI 注册：两档键都能从 {@code META-INF/extensions} 取到实现。
     *
     * <p>这条守的是「换实现只改注册文件」的承诺：键写错时 {@link JobScanCaches} 会兜底成
     * 本地 {@code new}，功能仍可用，但接入方自定义的实现会被静默忽略 —— 所以注册本身要验证。</p>
     */
    private static void caseSpiRegistration() {
        JobScanCache none = JobScanCaches.resolveNoop();
        check("I1 键 none 取到非缓存档", none != null && !none.caching(),
                "impl=" + (none == null ? "null" : none.getClass().getSimpleName()));

        JobScanCache memory = JobScanCaches.resolveMemory(5000L);
        check("I2 键 memory 取到缓存档", memory != null && memory.caching() && memory.ttlMs() == 5000L,
                memory == null ? "null" : "impl=" + memory.getClass().getSimpleName() + " ttl=" + memory.ttlMs());
    }

    // ------------------------------------------------------------------ 基础设施

    /**
     * 造若干候选行（只为占位，内容不参与缓存语义）。
     *
     * @param count 行数
     * @return 候选行
     */
    private static List<JobEntity> rows(int count) {
        List<JobEntity> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            JobEntity entity = new JobEntity();
            entity.setSysJobId((long) (i + 1));
            entity.setSysJobName("probe-" + i);
            list.add(entity);
        }
        return list;
    }

    /**
     * 静默断言（循环里只关心最终条数，不刷屏）。
     *
     * @param hit 命中结果
     */
    private static void checkSilently(JobScanCache.ScanEntry hit) {
        if (hit == null) {
            failed++;
        }
    }

    /**
     * 记录一条分节标题。
     *
     * @param text 标题
     */
    private static void say(String text) {
        System.out.println();
        System.out.println("--- " + text);
    }

    /**
     * 断言并计数。
     *
     * @param desc      断言描述
     * @param condition 条件
     * @param detail    细节（失败时打印）
     */
    private static void check(String desc, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + desc);
        } else {
            failed++;
            System.out.println("  [FAIL] " + desc + "  细节：" + detail);
        }
    }

    /**
     * 睡一小会儿，用于跨过毫秒级窗口。
     *
     * @param millis 毫秒
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
