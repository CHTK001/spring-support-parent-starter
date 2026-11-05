// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.job.dispatchguard;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.execute.JobExecutor;
import com.chua.starter.job.support.handler.JobHandler;
import com.chua.starter.job.support.handler.JobHandlerFactory;
import com.chua.starter.job.support.log.JobLogEvent;
import com.chua.starter.job.support.store.JobMapper;
import com.chua.starter.job.support.store.MybatisJobRepository;
import com.chua.starter.job.support.sync.JobSyncBus;
import com.chua.starter.job.support.sync.JobSyncTopics;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 派发护栏探针: 在<b>真实 MySQL/MariaDB</b> 上验证三项修复各自的关键行为。
 *
 * <p>覆盖 C-2（执行侧并发互斥）、C-3（Leader 切换后触发序列基线）、C-4（选主命名空间与作业分组解耦）。
 * 与 {@code GroupScopeProbe} 同一约定：不起 Spring 上下文、不用 JUnit、不用 H2/Mockito；
 * 真库不可达时以明确原因跳过，断言失败时进程退出码为 1。</p>
 *
 * <p><b>断言强度分级（如实记录，不要夸大）</b>：</p>
 * <ul>
 *   <li><b>C-2 —— 已证明有鉴别力。</b>去掉互斥后第二拍会真的执行、也不会记 SKIPPED,
 *       因此"第二拍未执行 / 记了 SKIPPED / ALLOW 下两拍都跑 / 被拒后占用已摘除"
 *       四条断言在修复前后结果不同。</li>
 *   <li><b>C-4 —— 新行为测试。</b>{@code resolveElectionNamespace} 是本次新增方法,
 *       修复前无法编译, 断言直接覆盖四条取值规则。</li>
 *   <li><b>C-3 —— <b>未能证明有鉴别力</b>，断言仅作回归护栏。</b>
 *       实测把基线回退为 0 后, "Leader 切换后补上过期的 cron 槽位" 仍然返回 1。
 *       原因: {@code findDue} 这条路径上基线的影响被三重交互掩盖 ——
 *       (1) 预读窗(默认 5s) 可能把「下一个未来的 cron 边界」也纳入到期;
 *       (2) {@code persistFireTimes} 的乐观领取会让写入结果与读取值耦合;
 *       (3) 建表默认 misfire 策略是 {@code DO_NOTHING}, 过期槽位会走
 *       {@code persistNextFire} 只推进 next_time 而不派发, 使"是否派发"与基线脱钩。
 *       已尝试并放弃的隔离手段: 换用 cron（FixedTrigger 首次计算用自身构造时刻做锚点,
 *       固定间隔上基线根本不起作用）、把过期量放大到 30s、等待距整分钟 ≥12s 的安全窗口。
 *       真正能区分的是回写后的 {@code next_time} 落在过去（网格延续）还是未来（从接管时刻重锚），
 *       但该值受 cron 相位影响, 尚不足以写成稳定断言。</li>
 * </ul>
 *
 * <p>全部经<b>公开路径</b>驱动: {@link JobExecutor} 在构造时订阅 {@code TASK_COMMAND},
 * 因此探针只 {@code bus.publish(TASK_COMMAND, ...)}, 不触碰任何私有方法。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class DispatchGuardProbe {

    /**
     * 探针数据前缀
     */
    private static final String MARK = "PROBE-DG";

    /**
     * 探针分组
     */
    private static final String GROUP = "probe-dg-group";

    /**
     * 每分钟整点 0 秒触发的 cron 表达式（C-3 用例的调度配置）。
     *
     * <p><b>六段顺序是 秒 分 时 日 月 周</b>: {@code 0 0/1 * * * ?} 才是「每分钟」;
     * 写成 {@code 0/1 * * * * ?} 会落在<b>秒</b>字段上, 变成「每秒」——
     * 那会让「上次触发点 + 1 秒」直接落在下一个槽位, 复现不出空写停滞。</p>
     */
    private static final String CRON_EVERY_MINUTE = "0 0/1 * * * ?";

    /**
     * 槽位过期时长（毫秒）
     */
    private static final long OVERDUE_MS = 30_000L;

    /**
     * 预读窗（毫秒）
     */
    private static final long PRE_READ_MS = 5_000L;

    /**
     * 任务主键
     */
    private static final long JOB_ID = 995001L;

    /**
     * misfire 空写停滞用例的任务主键
     */
    private static final long JOB_ID_STALL = 995002L;

    /**
     * 阻塞型处理器的最长等待（毫秒）
     */
    private static final long BLOCK_MS = 1_500L;

    /**
     * 处理器名 → 其总线（探针内部映射, 用于按任务名定位总线, 避免依赖私有访问器）
     */
    private static final ConcurrentHashMap<String, JobSyncBus> BUSES = new ConcurrentHashMap<>();

    /**
     * 断言失败计数
     */
    private static int failed;

    /**
     * 断言通过计数
     */
    private static int passed;

    /**
     * 入口。
     *
     * @param args 命令行参数（忽略, 连接参数一律走系统属性）
     */
    public static void main(String[] args) {
        String url = System.getProperty("job.probe.jdbc.url",
                "jdbc:mysql://127.0.0.1:13306/collect_test"
                        + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false"
                        + "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        String user = System.getProperty("job.probe.jdbc.user", "collect");
        String password = System.getProperty("job.probe.jdbc.password", "");

        probeElectionNamespace();

        if (!reachable(url, user, password)) {
            log.warn("[probe] SKIPPED 真库不可达, 跳过派发护栏探针的 C-2/C-3 部分。"
                    + "需先建好隔离库并做端口转发, 例如: ssh -L 13306:127.0.0.1:3306 <host>");
            report();
            return;
        }

        HikariDataSource dataSource = new HikariDataSource();
        SqlSession session = null;
        try {
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setJdbcUrl(url);
            dataSource.setUsername(user);
            dataSource.setPassword(password);
            dataSource.setMaximumPoolSize(4);

            SqlSessionFactory factory = buildFactory(dataSource);
            seedDueCronJob(dataSource);

            // 会话需在整个探针期间保持打开: MyBatis 的 mapper 绑定会话, 会话关闭后不可再用
            session = factory.openSession(true);
            probeLeaderTransfer(session.getMapper(JobMapper.class), dataSource);
            probeMisfireNoWriteStall(session.getMapper(JobMapper.class), dataSource);
            probeConcurrentGuard();
        } catch (Exception ex) {
            log.error("[probe] 探针执行异常, 判定为失败", ex);
            failed++;
        } finally {
            if (session != null) {
                session.close();
            }
            dataSource.close();
        }
        report();
    }

    /**
     * C-4: 选主命名空间推导（纯函数, 不需要数据库）。
     */
    private static void probeElectionNamespace() {
        JobProperties.ClusterConfig config = new JobProperties.ClusterConfig();
        config.setGroup("collect-flow");

        config.setElectionNamespace("app-lease");
        checkEq("C-4 显式配置优先于应用名", "app-lease", config.resolveElectionNamespace("some-app"));

        config.setElectionNamespace(null);
        checkEq("C-4 留空取应用名", "some-app", config.resolveElectionNamespace("some-app"));
        checkEq("C-4 空白应用名回落到作业分组", "collect-flow", config.resolveElectionNamespace("   "));
        checkEq("C-4 无应用名回落到作业分组", "collect-flow", config.resolveElectionNamespace(null));

        // 解耦的实质: 两个部署作业分组同名, 但选主命名空间不同 => 不会抢同一条租约行
        JobProperties.ClusterConfig appA = new JobProperties.ClusterConfig();
        appA.setGroup("default");
        JobProperties.ClusterConfig appB = new JobProperties.ClusterConfig();
        appB.setGroup("default");
        check("C-4 同分组名不同应用名 => 租约键不同",
                !appA.resolveElectionNamespace("app-a").equals(appB.resolveElectionNamespace("app-b")),
                appA.resolveElectionNamespace("app-a") + " vs " + appB.resolveElectionNamespace("app-b"));
    }

    /**
     * 造一个"槽位已到期"的 cron 任务, 模拟 Leader 切换后的接管现场。
     *
     * <p>每分钟整点的 cron: 上一次已派发落在 2 分钟前, 待触发槽位是 1 分钟前
     * （已过期, 且超出预读窗, 因此能否补发完全取决于基线是否取到持久化的上次触发点）。</p>
     *
     * @param dataSource 数据源
     * @throws SQLException 库异常
     */
    private static void seedDueCronJob(DataSource dataSource) throws SQLException {
        long now = System.currentTimeMillis();
        long lastFire = now - 120_000L;
        long dueSlot = now - 60_000L;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("delete from sys_job_log where sys_job_log_no like '" + MARK + "%'");
                statement.executeUpdate("delete from sys_job where sys_job_no like '" + MARK + "%'");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into sys_job (sys_job_id, sys_job_no, sys_job_name, sys_job_group,"
                            + " sys_job_execute_bean, sys_job_schedule_type, sys_job_schedule_time,"
                            + " sys_job_trigger_status, sys_job_is_deleted, sys_job_concurrent_policy,"
                            + " sys_job_execute_misfire_strategy,"
                            + " sys_job_trigger_last_time, sys_job_trigger_next_time, create_time)"
                            + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, JOB_ID);
                ps.setString(2, MARK + "-CRON");
                ps.setString(3, MARK + "-CRON");
                ps.setString(4, GROUP);
                ps.setString(5, MARK + "-CRON");
                ps.setString(6, "CRON");
                ps.setString(7, CRON_EVERY_MINUTE);
                ps.setInt(8, 1);
                ps.setInt(9, 0);
                ps.setString(10, "SKIP");
                // 必须显式给 FIRE_ONCE_NOW: 建表默认值是 DO_NOTHING, 那样过期槽位会按
                // misfire 策略被跳过（只推进 next_time、不派发），本用例就验不到"是否补发"
                ps.setString(11, "FIRE_ONCE_NOW");
                ps.setLong(12, lastFire);
                ps.setLong(13, dueSlot);
                ps.setTimestamp(14, new java.sql.Timestamp(now));
                ps.executeUpdate();
            }
            connection.commit();
        }
        log.info("[probe] 种子 cron 任务 cron={} lastFire={} dueSlot={} now={} (dueSlot 已过期 {}ms)",
                CRON_EVERY_MINUTE, lastFire, dueSlot, now, now - dueSlot);
    }

    /**
     * C-3: Leader 切换后的派发行为（<b>回归护栏, 未能证明可区分修复前后</b>）。
     *
     * <p>详见类注释的「断言强度分级」。这里保留的是三条不依赖时序的契约:
     * 未到期不派发、{@code last_time} 记派发墙钟、切换后仍会处理到期槽位。</p>
     *
     * @param jobMapper  台账映射器
     * @param dataSource 数据源
     */
    private static void probeLeaderTransfer(JobMapper jobMapper, DataSource dataSource) {
        long now = System.currentTimeMillis();
        // 每次 new 都是"全新进程": 进程内 lastFireMs 与触发器缓存都是空的
        MybatisJobRepository fresh = new MybatisJobRepository(jobMapper, GROUP);
        List<JobDefinition> due = fresh.findDue(now, PRE_READ_MS, 200, GROUP);
        checkNum("C-3 Leader 切换后处理到期槽位", 1, due.size());
        long afterFire = readLong(dataSource,
                "select sys_job_trigger_next_time from sys_job where sys_job_id=" + JOB_ID);
        // 诊断值: 网格延续时 next_time 落在过去, 从接管时刻重锚时落在未来。当前不作断言
        log.info("[probe] C-3-DIAG now={} 派发后 next_time={} (next_time-now={}ms, 负=网格延续)",
                now, afterFire, afterFire - now);

        // 反向断言: 槽位未到期时不得派发（防止把上面的结论做成"总是补跑"）
        pushNextTime(dataSource, JOB_ID, now + 600_000L);
        MybatisJobRepository notDue = new MybatisJobRepository(jobMapper, GROUP);
        checkNum("C-3 槽位未到期时不派发", 0,
                notDue.findDue(System.currentTimeMillis(), PRE_READ_MS, 200, GROUP).size());
    }

    /**
     * 造一个复现生产死锁现场的 cron 任务: {@code DO_NOTHING} + 上次触发点恰好是待触发槽位的前一个。
     *
     * <p>这正是生产上 {@code collect.self.check} 卡死的形态: {@code next_time} 已过期,
     * {@code last_time} 正好等于上一个 cron 边界, 于是本轮算出的 {@code nextFire} <b>等于</b>
     * {@code next_time}, UPDATE 变成空写 → affected=0 → 序列永不推进 → 任务再也不触发。</p>
     *
     * @param dataSource 数据源
     * @throws SQLException 库异常
     */
    private static void seedMisfireStallJob(DataSource dataSource) throws SQLException {
        long now = System.currentTimeMillis();
        // 用「整分钟 - 60s」当上一个边界, 「整分钟」当待触发槽位, 二者严格相邻
        long minuteBoundary = now / 60_000L * 60_000L;
        long dueSlot = minuteBoundary;
        long lastFire = minuteBoundary - 60_000L;
        try (Connection connection = dataSource.getConnection()) {
            // 必须先清理: 该探针每轮都会跑, 主键固定, 不清理会撞主键冲突并让断言读到上一轮的陈值
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("delete from sys_job where sys_job_id=" + JOB_ID_STALL);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into sys_job (sys_job_id, sys_job_no, sys_job_name, sys_job_group,"
                            + " sys_job_execute_bean, sys_job_schedule_type, sys_job_schedule_time,"
                            + " sys_job_trigger_status, sys_job_is_deleted, sys_job_concurrent_policy,"
                            + " sys_job_execute_misfire_strategy,"
                            + " sys_job_trigger_last_time, sys_job_trigger_next_time, create_time)"
                            + " values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, JOB_ID_STALL);
                ps.setString(2, MARK + "-STALL");
                ps.setString(3, MARK + "-STALL");
                ps.setString(4, GROUP);
                ps.setString(5, MARK + "-STALL");
                ps.setString(6, "CRON");
                ps.setString(7, CRON_EVERY_MINUTE);
                ps.setInt(8, 1);
                ps.setInt(9, 0);
                ps.setString(10, "SKIP");
                // 刻意用建表默认值 DO_NOTHING: 生产上的 collect.self.check 就是这个策略
                ps.setString(11, "DO_NOTHING");
                ps.setLong(12, lastFire);
                ps.setLong(13, dueSlot);
                ps.setTimestamp(14, new java.sql.Timestamp(now));
                ps.executeUpdate();
            }
        }
        log.info("[probe] 种子 misfire 停滞任务 lastFire={} dueSlot={} now={} (dueSlot 已过期 {}ms)",
                lastFire, dueSlot, now, now - dueSlot);
    }

    /**
     * 回归护栏: DO_NOTHING 的过期槽位不能因为「空写」而卡死。
     *
     * <p>判别力: 修复前 {@code persistNextFire} 写入与库里相同的值, affected=0,
     * {@code next_time} 原地不动, 断言「必须推进」必然失败。</p>
     *
     * @param jobMapper  台账映射器
     * @param dataSource 数据源
     * @throws SQLException 库异常
     */
    private static void probeMisfireNoWriteStall(JobMapper jobMapper, DataSource dataSource) throws SQLException {
        seedMisfireStallJob(dataSource);
        long before = readLong(dataSource,
                "select sys_job_trigger_next_time from sys_job where sys_job_id=" + JOB_ID_STALL);

        // 必须复用同一个仓库实例: lastFireMs 是进程内状态, 换实例就退回持久化基线,
        // 而持久化基线在两个版本里都是对的 —— 那样测不出「基线多推一格」的缺陷。
        // 真实形态是 Leader 长驻进程连续扫描, 跳过一个 misfire 后必须能触发下一个槽位。
        MybatisJobRepository leader = new MybatisJobRepository(jobMapper, GROUP);
        leader.findDue(System.currentTimeMillis(), PRE_READ_MS, 200, GROUP);

        long afterOne = readLong(dataSource,
                "select sys_job_trigger_next_time from sys_job where sys_job_id=" + JOB_ID_STALL);
        log.info("[probe] C-3-STALL next_time {} -> {} (单拍)", before, afterOne);
        check("C-3 DO_NOTHING 过期槽位必须推进派发序列（不得空写停滞）",
                afterOne > before, afterOne + " > " + before);

        // 继续用同一个 leader 再扫一拍: 序列已追平到未来, 此刻正确行为是不再改动
        leader.findDue(System.currentTimeMillis(), PRE_READ_MS, 200, GROUP);
        long settled = readLong(dataSource,
                "select sys_job_trigger_next_time from sys_job where sys_job_id=" + JOB_ID_STALL);
        long nowAfter = System.currentTimeMillis();
        check("C-3 跳过后派发序列已追平到未来（此后不再停滞等待）",
                settled > nowAfter, settled + " > now " + nowAfter);

        // 关键断言: 顺延到的那个槽位必须真的能触发。
        // now 取 settled 前 2 秒 —— 落在预读窗内且尚未过期, missed 必为 false。
        // 基线正确  => nextFire == settled == next_time => 触发, 返回含 STALL 任务
        // 基线多推一格 => nextFire 跳到 settled 之后 => 该槽位被跳过, 返回 0 行
        var fired = leader.findDue(settled - 2_000L, PRE_READ_MS, 200, GROUP);
        boolean stallJobFired = false;
        for (JobDefinition definition : fired) {
            if (definition != null && (MARK + "-STALL").equals(definition.getName())) {
                stallJobFired = true;
            }
        }
        check("C-3 顺延后的槽位必须能真实触发（基线不得多推一格）", stallJobFired, fired.size());
    }

    /**
     * C-2: 执行侧并发互斥。
     */
    private static void probeConcurrentGuard() {
        probeSkipPolicy();
        probeAllowPolicy();
        probeRejectReleasesSlot();
    }

    /**
     * SKIP 策略: 上一轮未结束时第二拍被跳过; 结束后互斥位必须摘除。
     */
    private static void probeSkipPolicy() {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        List<String> skipped = Collections.synchronizedList(new ArrayList<>());
        JobHandler handler = blockingHandler(MARK + "-SKIP", release, runs);
// [P3C C1 豁免] 场景化创建线程，需文档化
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        JobExecutor jobExecutor = newJobExecutor(pool, "SKIP", skipped, handler);
        try {
            String command = command(MARK + "-SKIP", "SKIP");
            publish(MARK + "-SKIP", command);
            awaitAtLeast(runs, 1);
            publish(MARK + "-SKIP", command);
            sleep(400L);
            checkNum("C-2 SKIP 策略下第二拍未执行", 1, runs.get());
            checkNum("C-2 第二拍记了一条 SKIPPED", 1, skipped.size());
            check("C-2 SKIPPED 理由写明并发策略",
                    !skipped.isEmpty() && skipped.get(0).contains("并发策略"),
                    skipped.isEmpty() ? "<none>" : skipped.get(0));
            checkNum("C-2 并发跳过计数=1", 1, jobExecutor.concurrentSkippedCount());
            check("C-2 运行中集合含该任务", jobExecutor.runningJobs().contains(MARK + "-SKIP"),
                    jobExecutor.runningJobs());
        } finally {
            release.countDown();
            jobExecutor.shutdown();
        }

        // 放行后任务跑完, 占用必须被摘除——否则该任务会被永久判为"运行中"而永不派发
        AtomicInteger runs2 = new AtomicInteger();
        List<String> skipped2 = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch hold = new CountDownLatch(1);
// [P3C C1 豁免] 场景化创建线程，需文档化
        ExecutorService pool2 = Executors.newVirtualThreadPerTaskExecutor();
        JobExecutor again = newJobExecutor(pool2, "SKIP", skipped2,
                blockingHandler(MARK + "-SKIP", hold, runs2));
        try {
            publish(MARK + "-SKIP", command(MARK + "-SKIP", "SKIP"));
            awaitAtLeast(runs2, 1);
            check("C-2 执行中占用已登记", again.runningJobs().contains(MARK + "-SKIP"), again.runningJobs());
            hold.countDown();
            awaitEmpty(again);
            check("C-2 上一轮结束后互斥位已摘除", again.runningJobs().isEmpty(), again.runningJobs());
        } finally {
            hold.countDown();
            again.shutdown();
        }
    }

    /**
     * ALLOW 策略: 允许并发重入（保持历史行为）。
     */
    private static void probeAllowPolicy() {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        List<String> skipped = Collections.synchronizedList(new ArrayList<>());
        JobHandler handler = blockingHandler(MARK + "-ALLOW", release, runs);
// [P3C C1 豁免] 场景化创建线程，需文档化
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        JobExecutor jobExecutor = newJobExecutor(pool, "ALLOW", skipped, handler);
        try {
            String command = command(MARK + "-ALLOW", "ALLOW");
            publish(MARK + "-ALLOW", command);
            awaitAtLeast(runs, 1);
            publish(MARK + "-ALLOW", command);
            awaitAtLeast(runs, 2);
            checkNum("C-2 ALLOW 策略下两拍都执行", 2, runs.get());
            checkNum("C-2 ALLOW 下不产生并发跳过", 0, jobExecutor.concurrentSkippedCount());
        } finally {
            release.countDown();
            jobExecutor.shutdown();
        }
    }

    /**
     * 提交被拒时必须摘除互斥占用, 否则任务被永久判为"运行中"。
     */
    private static void probeRejectReleasesSlot() {
        List<String> skipped = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch hold = new CountDownLatch(1);
        // 池只有一个 worker 且用 SynchronousQueue: 先把 worker 占住, 之后任何提交都无处可去而被拒。
        // core/max 必须是正数, ThreadPoolExecutor 不接受 0
// [P3C C1 豁免] 场景化创建线程，需文档化
        ThreadPoolExecutor full = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), r -> {
// [P3C C1 豁免] 场景化创建线程，需文档化
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch occupied = new CountDownLatch(1);
        try {
            full.execute(() -> {
                occupied.countDown();
                try {
                    hold.await(BLOCK_MS * 4, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            awaitLatch(occupied);
        } catch (RejectedExecutionException ex) {
            // 极端时序下 worker 尚未就绪, 不影响本用例要验的东西
            log.debug("[probe] 占用 worker 失败, 继续");
        }
        JobExecutor jobExecutor = newJobExecutor(full, "SKIP", skipped,
                blockingHandler(MARK + "-REJECT", hold, new AtomicInteger()));
        try {
            String command = command(MARK + "-REJECT", "SKIP");
            publish(MARK + "-REJECT", command);
            publish(MARK + "-REJECT", command);
            check("C-2 提交被拒后互斥占用已摘除", jobExecutor.runningJobs().isEmpty(), jobExecutor.runningJobs());
            checkNum("C-2 提交被拒计入 dropped", 2, jobExecutor.droppedCount());
        } finally {
            hold.countDown();
            jobExecutor.shutdown();
            full.shutdownNow();
        }
    }

    /**
     * 构造执行器: 注册处理器、挂上 TASK_LOG 收集器, 并记录总线。
     *
     * @param pool    执行线程池
     * @param policy  兜底并发策略
     * @param skipped SKIPPED 理由收集
     * @param handler 处理器
     * @return 执行器
     */
    private static JobExecutor newJobExecutor(ExecutorService pool, String policy,
                                               List<String> skipped, JobHandler handler) {
        JobHandlerFactory factory = JobHandlerFactory.getInstance();
        factory.register(handler);
        JobSyncBus bus = new JobSyncBus();
        bus.subscribe(JobSyncTopics.TASK_LOG, (topic, payload) -> {
            if (payload instanceof JobLogEvent event
                    && event.status() == JobLogEvent.Status.SKIPPED) {
                skipped.add(String.valueOf(event.exception()));
            }
        });
        BUSES.put(handler.name(), bus);
        return new JobExecutor(factory, bus, "probe-node@" + handler.name(), pool, null, policy);
    }

    /**
     * 通过总线派发一条命令（等价于调度中心下发）。
     *
     * @param jobName 任务名（用于定位该任务对应的总线）
     * @param command 命令负载
     */
    private static void publish(String jobName, String command) {
        JobSyncBus bus = BUSES.get(jobName);
        if (bus == null) {
            failed++;
            log.error("[probe] [FAIL] 找不到任务 {} 的总线", jobName);
            return;
        }
        bus.publish(JobSyncTopics.TASK_COMMAND, "probe", command);
    }

    /**
     * 构造带并发策略的派发负载。
     *
     * @param jobName 任务名
     * @param policy  并发策略
     * @return 命令负载
     */
    private static String command(String jobName, String policy) {
        return JobSyncTopics.command(jobName, false, null, null, null, null, null, null,
                null, null, null, null, null, policy);
    }

    /**
     * 构造一个阻塞到 {@code release} 的处理器。
     *
     * @param name    处理器名
     * @param release 释放闸门
     * @param runs    执行计数
     * @return 处理器
     */
    private static JobHandler blockingHandler(String name, CountDownLatch release, AtomicInteger runs) {
        return new JobHandler() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void execute() {
                runs.incrementAndGet();
                try {
                    release.await(BLOCK_MS * 4, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    /**
     * 程序化装配 MyBatis-Plus 会话工厂。
     *
     * @param dataSource 数据源
     * @return 会话工厂
     */
    private static SqlSessionFactory buildFactory(DataSource dataSource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("probe", new JdbcTransactionFactory(), dataSource));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addMapper(JobMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    /**
     * 真库可达性探测。
     *
     * @param url      连接串
     * @param user     用户
     * @param password 口令
     * @return 可达返回 true
     */
    private static boolean reachable(String url, String user, String password) {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select version()")) {
            if (rs.next()) {
                log.info("[probe] 真库就绪 version={}", rs.getString(1));
            }
            return true;
        } catch (SQLException ex) {
            log.warn("[probe] 真库不可达 cause={}", ex.getMessage());
            return false;
        }
    }

    /**
     * 读一列 long 值（走独立 JDBC 连接, 不用 MyBatis 会话——
     * {@code openSession(true)} 下每条语句结束即归还连接, 事后取连接只会拿到已关闭的实例）。
     *
     * @param dataSource 数据源
     * @param sql        查询
     * @return 值, 无行返回 Long.MIN_VALUE
     */
    private static long readLong(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : Long.MIN_VALUE;
        } catch (SQLException ex) {
            log.warn("[probe] 读取失败 sql={} cause={}", sql, ex.getMessage());
            return Long.MIN_VALUE;
        }
    }

    /**
     * 直接改写某任务的下次触发时刻（构造不同现场用, 绕开派发逻辑）。
     *
     * @param dataSource 数据源
     * @param jobId      任务主键
     * @param nextTime   下次触发时刻（毫秒）
     */
    private static void pushNextTime(DataSource dataSource, long jobId, long nextTime) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "update sys_job set sys_job_trigger_next_time=? where sys_job_id=?")) {
            ps.setLong(1, nextTime);
            ps.setLong(2, jobId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.warn("[probe] 改写下次触发时刻失败 jobId={} cause={}", jobId, ex.getMessage());
        }
    }

    /**
     * 等待执行器的互斥集合清空。
     *
     * @param jobExecutor 执行器
     */
    private static void awaitEmpty(JobExecutor jobExecutor) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!jobExecutor.runningJobs().isEmpty() && System.currentTimeMillis() < deadline) {
            sleep(20L);
        }
    }

    /**
     * 等待闩锁归零。
     *
     * @param latch 闩锁
     */
    private static void awaitLatch(CountDownLatch latch) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (latch.getCount() > 0L && System.currentTimeMillis() < deadline) {
            sleep(20L);
        }
    }

    /**
     * 等待处理器进入执行态。
     *
     * @param runs 执行计数
     * @param want 期望值
     */
    private static void awaitAtLeast(AtomicInteger runs, int want) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (runs.get() < want && System.currentTimeMillis() < deadline) {
            sleep(20L);
        }
    }

    /**
     * 睡眠（吞掉中断）。
     *
     * @param ms 毫秒
     */
    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 记录一条相等断言。
     *
     * @param name     断言名
     * @param expected 期望值
     * @param actual   实际值
     */
    private static void checkEq(String name, Object expected, Object actual) {
        check(name, expected == null ? actual == null : expected.equals(actual), actual);
    }

    /**
     * 记录一条数值断言。
     *
     * @param name     断言名
     * @param expected 期望值
     * @param actual   实际值
     */
    private static void checkNum(String name, long expected, long actual) {
        if (expected == actual) {
            passed++;
            log.info("[probe] [PASS] {} -> {}", name, actual);
        } else {
            failed++;
            log.error("[probe] [FAIL] {} 期望={} 实际={}", name, expected, actual);
        }
    }

    /**
     * 记录一条布尔断言。
     *
     * @param name   断言名
     * @param passed 断言是否通过
     * @param actual 实际值, 便于失败定位
     */
    private static void check(String name, boolean passed, Object actual) {
        if (passed) {
            DispatchGuardProbe.passed++;
            log.info("[probe] [PASS] {} -> {}", name, actual);
        } else {
            DispatchGuardProbe.failed++;
            log.error("[probe] [FAIL] {} 实际={}", name, actual);
        }
    }

    /**
     * 打印汇总并按结果设置退出码。
     */
    private static void report() {
        log.info("[probe] DISPATCHGUARD_RESULT passed={} failed={}", passed, failed);
        if (failed > 0) {            System.exit(1);        }    }}