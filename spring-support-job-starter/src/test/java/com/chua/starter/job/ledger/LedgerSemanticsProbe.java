// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.job.ledger;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.chua.starter.job.support.JobProperties;
import com.chua.starter.job.support.dispatch.JobDefinition;
import com.chua.starter.job.support.store.JobMapper;
import com.chua.starter.job.support.store.MybatisJobRepository;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * 调度台账语义与 misfire 补发上限的回归探针。
 *
 * <p>覆盖 2026-10-01 在 198 实机上修掉的两个缺陷（线上现象：30 秒周期的 cron 被逐秒重复派发，
 * 台账出现 {@code next_time < last_time}）：</p>
 * <ol>
 *   <li><b>台账落库语义</b>：{@code last_time} 必须是「被派发的槽位」、{@code next_time} 必须是
 *       「再下一个槽位」。原实现把墙钟写进 last_time、把刚派发完的槽位写进 next_time，导致扫描
 *       谓词 {@code next_time <= now + preReadMs} 恒成立、该行每拍都重新命中。</li>
 *   <li><b>misfire 补发上限</b>：基线陈旧时只补发 <b>1 次</b>，不得把错过的每个槽位都补一遍。
 *       原实现每拍只推进一个槽位，风暴长度 ≈（now − 基线）÷ 周期。</li>
 * </ol>
 *
 * <p>与 {@code DispatchGuardProbe} 同一约定：不起 Spring 上下文、不用 JUnit、不用 Mockito，
 * 直接对真 MySQL 跑真 MyBatis。每条断言都落到库里的实际数值上，不依赖日志。</p>
 *
 * <p>连接参数全部走系统属性，便于在任何机器上跑（默认指向本机 3306 的隔离库）：</p>
 * <pre>
 * java -cp ... com.chua.starter.job.ledger.LedgerSemanticsProbe \
 *      -Djob.probe.jdbc.url=jdbc:mysql://127.0.0.1:3306/job_regress \
 *      -Djob.probe.jdbc.user=root -Djob.probe.jdbc.password=xxx
 * </pre>
 *
 * @author CH
 */
@Slf4j
public final class LedgerSemanticsProbe {

    /** 断言失败计数 */
    private static int failed;

    /** 断言通过计数 */
    private static int passed;

    /** 被测作业的分组（与线上采集登记的分组一致） */
    private static final String GROUP = "ledger-probe";

    /** 基准时刻：2026-01-01 00:00:00 的毫秒时间戳（固定值，让断言可复现） */
    private static final long BASE_MS = 1767225600000L;

    /**
     * 私有构造，禁止实例化。
     */
    private LedgerSemanticsProbe() {
    }

    /**
     * 入口。
     *
     * @param args 未使用，连接参数走系统属性
     * @throws Exception 建库或断言过程异常
     */
    public static void main(String[] args) throws Exception {
        String url = System.getProperty("job.probe.jdbc.url", "jdbc:mysql://127.0.0.1:3306/job_regress");
        String user = System.getProperty("job.probe.jdbc.user", "root");
        String password = System.getProperty("job.probe.jdbc.password", "");

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(4);
        try {
            prepareSchema(dataSource);
            SqlSessionFactory factory = buildFactory(dataSource);
            runCases(factory);
        } finally {
            dataSource.close();
        }

        log.info("[probe] ---- 汇总：passed={} failed={} ----", passed, failed);
        System.out.println("PROBE_RESULT passed=" + passed + " failed=" + failed); // [P3C E1 豁免]
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ 断言

    /**
     * A. 正常派发后：last_time 是被派发的槽位，next_time 是再下一个槽位，且 next &gt; last。
     *
     * @param factory 会话工厂
     * @throws Exception 数据库异常
     */
    private static void caseNormalDispatch(SqlSessionFactory factory) throws Exception {
        say("A. 正常派发：台账两列都必须是槽位，且 next > last");
        String jobNo = "ledger-A";
        long baseline = BASE_MS + 30000L;
        seedJob(jobNo, "0/30 * * * * ?", null, baseline, baseline + 30000L, 1);
        try (SqlSession session = factory.openSession(true)) {
            MybatisJobRepository repository = newRepository(session);
            // now 必须让「下一个槽位」落进预读窗：next_time = baseline+30s，
            // 预读 5s，所以 now 取 baseline+26s 时 next 才 due（提前 4 秒派发，正是预读语义）。
            // 取 baseline+5s 会让行根本不被选中，due 恒为 0 —— 那是种子数据写错，不是代码问题。
            long now = baseline + 26000L;
            List<JobDefinition> due = repository.findDue(now, 5000L, 10, GROUP);
            check("A1 该槽位应被派发一次", due.size() == 1, "due=" + due.size());
        }
        long[] row = readTimes(jobNo);
        // 被派发的槽位是「基线之后的那一个」= baseline + 周期，不是 baseline 本身
        long fired = baseline + 30000L;
        check("A2 last_time = 被派发的槽位", row[0] == fired, "last=" + row[0] + " 期望=" + fired);
        check("A3 next_time = 再下一个槽位", row[1] == fired + 30000L,
                "next=" + row[1] + " 期望=" + (fired + 30000L));
        check("A4 next > last（扫描谓词不得恒成立）", row[1] > row[0], "last=" + row[0] + " next=" + row[1]);
        check("A5 间隔恰为一个周期", row[1] - row[0] == 30000L, "间隔=" + (row[1] - row[0]));
        cleanup(jobNo);
    }

    /**
     * B. 陈旧基线 + 默认 misfire 策略：只补发 1 次，随后连续多拍都应为空。
     *
     * @param factory 会话工厂
     * @throws Exception 数据库异常
     */
    private static void caseMisfireCap(SqlSessionFactory factory) throws Exception {
        say("B. 陈旧基线 30 分钟（60 个 30 秒槽位）→ 只补发 1 次，随后安静");
        String jobNo = "ledger-B";
        long now = BASE_MS + 30L * 60L * 1000L;
        long stale = now - 30L * 60L * 1000L;
        seedJob(jobNo, "0/30 * * * * ?", null, stale, 0L, 1);
        int dispatched = 0;
        try (SqlSession session = factory.openSession(true)) {
            MybatisJobRepository repository = newRepository(session);
            for (int tick = 1; tick <= 6; tick++) {
                List<JobDefinition> due = repository.findDue(now, 5000L, 10, GROUP);
                dispatched += due.size();
                log.info("[probe] B tick#{} due={} now={}", tick, due.size(), now);
            }
        }
        check("B1 六拍合计只补发 1 次（原实现是每拍 1 次、共 6 次）", dispatched == 1,
                "dispatched=" + dispatched);
        long[] row = readTimes(jobNo);
        check("B2 last_time 已跳到补发时刻（不再是那个过期槽位）", row[0] == now,
                "last=" + row[0] + " 期望=" + now);
        check("B3 next_time 越过 now（下一拍不再命中）", row[1] > now,
                "next=" + row[1] + " now=" + now);
        cleanup(jobNo);
    }

    /**
     * C. 陈旧基线 + DO_NOTHING：一次都不补发，且一拍之内收敛。
     *
     * @param factory 会话工厂
     * @throws Exception 数据库异常
     */
    private static void caseDoNothing(SqlSessionFactory factory) throws Exception {
        say("C. 陈旧基线 + DO_NOTHING → 0 次补发，且一拍收敛");
        String jobNo = "ledger-C";
        long now = BASE_MS + 30L * 60L * 1000L;
        long stale = now - 30L * 60L * 1000L;
        seedJob(jobNo, "0/30 * * * * ?", "DO_NOTHING", stale, 0L, 1);
        int dispatched = 0;
        try (SqlSession session = factory.openSession(true)) {
            MybatisJobRepository repository = newRepository(session);
            for (int tick = 1; tick <= 6; tick++) {
                List<JobDefinition> due = repository.findDue(now, 5000L, 10, GROUP);
                dispatched += due.size();
            }
        }
        check("C1 六拍合计 0 次补发", dispatched == 0, "dispatched=" + dispatched);
        long[] row = readTimes(jobNo);
        check("C2 last_time 已推过 now（一拍收敛，原实现需 60 拍）", row[0] > stale,
                "last=" + row[0] + " 陈旧基线=" + stale);
        check("C3 next_time 越过 now", row[1] > now, "next=" + row[1] + " now=" + now);
        cleanup(jobNo);
    }

    /**
     * D. Leader 切换（新进程，进程内基线 map 为空）：不得重复派发同一槽位。
     *
     * @param factory 会话工厂
     * @throws Exception 数据库异常
     */
    private static void caseLeaderRestartNoDuplicate(SqlSessionFactory factory) throws Exception {
        say("D. Leader 切换（换一个 repository 实例，进程内基线为空）→ 不得重复派发同槽位");
        String jobNo = "ledger-D";
        long baseline = BASE_MS + 30000L;
        long next = baseline + 30000L;
        seedJob(jobNo, "0/30 * * * * ?", null, baseline, next, 1);
        long now = baseline + 26000L;
        int first;
        try (SqlSession session = factory.openSession(true)) {
            first = newRepository(session).findDue(now, 5000L, 10, GROUP).size();
        }
        int second;
        try (SqlSession session = factory.openSession(true)) {
            // 新实例 = 换 Leader 后进程内 lastFireMs 为空，只能靠库里的 last_time 当基线
            second = newRepository(session).findDue(now, 5000L, 10, GROUP).size();
        }
        check("D1 首次应派发", first == 1, "first=" + first);
        check("D2 换 Leader 后不得再派发同一槽位", second == 0, "second=" + second);
        cleanup(jobNo);
    }

    /**
     * E. 并发两个扫描者：同一槽位只允许被派发一次（last_time 乐观条件生效）。
     *
     * @param factory 会话工厂
     * @throws Exception 数据库异常
     */
    private static void caseConcurrentScanNoDuplicate(SqlSessionFactory factory) throws Exception {
        say("E. 两个扫描者先后扫同一槽位 → 只允许一次派发");
        String jobNo = "ledger-E";
        long baseline = BASE_MS + 30000L;
        seedJob(jobNo, "0/30 * * * * ?", null, baseline, baseline + 30000L, 1);
        long now = baseline + 26000L;
        int a;
        int b;
        try (SqlSession s1 = factory.openSession(true)) {
            a = newRepository(s1).findDue(now, 5000L, 10, GROUP).size();
        }
        try (SqlSession s2 = factory.openSession(true)) {
            b = newRepository(s2).findDue(now, 5000L, 10, GROUP).size();
        }
        check("E1 两个扫描者合计只派发 1 次", a + b == 1, "a=" + a + " b=" + b);
        cleanup(jobNo);
    }

    /**
     * F. 触发器缓存带签名：改 cron 后不重启也必须按新节奏算（缓存值与调度签名不符即重建）。
     *
     * @param factory 会话工厂
     * @throws Exception 数据库异常
     */
    private static void caseTriggerCacheSignature(SqlSessionFactory factory) throws Exception {
        say("F. 改 cron 后不重启 → 触发器必须按新表达式重建");
        String jobNo = "ledger-F";
        long baseline = BASE_MS + 30000L;
        // 第 1 拍：用 30 秒节奏跑一次，把「30 秒的触发器」放进进程内缓存
        seedJob(jobNo, "0/30 * * * * ?", null, baseline, baseline + 30000L, 1);
        try (SqlSession session = factory.openSession(true)) {
            newRepository(session).findDue(baseline + 26000L, 5000L, 10, GROUP);
        }
        long[] afterFirst = readTimes(jobNo);
        check("F1 第 1 拍派发的是 30 秒槽位（缓存里存的是 30 秒触发器）",
                afterFirst[0] == baseline + 30000L,
                "last=" + afterFirst[0] + " 期望=" + (baseline + 30000L));
        // 不重启，只改 cron 为每 10 秒，并把台账摆成「下一个槽位落在 10 秒网格上」
        updateSchedule(jobNo, "0/10 * * * * ?");
        resetTimes(jobNo, baseline + 30000L, baseline + 40000L);
        try (SqlSession session = factory.openSession(true)) {
            newRepository(session).findDue(baseline + 36000L, 5000L, 10, GROUP);
        }
        long[] afterSecond = readTimes(jobNo);
        // 若缓存仍用 30 秒触发器：下一个槽位算出来是 baseline+60s，不在预读窗内 → 一次都不派发，
        // last_time 保持 baseline+30s。用新触发器：下一个槽位是 baseline+40s → 派发，last_time 变成它。
        check("F2 改 cron 后同一进程内按新表达式派发（缓存已按签名重建）",
                afterSecond[0] == baseline + 40000L,
                "last=" + afterSecond[0] + " 期望=" + (baseline + 40000L)
                        + "（若仍为 " + (baseline + 30000L) + " 说明缓存还在用旧触发器）");
        cleanup(jobNo);
    }

    // ------------------------------------------------------------------ 基础设施

    /**
     * 逐条跑用例。
     *
     * @param factory 会话工厂
     * @throws Exception 数据库异常
     */
    private static void runCases(SqlSessionFactory factory) throws Exception {
        caseNormalDispatch(factory);
        caseMisfireCap(factory);
        caseDoNothing(factory);
        caseLeaderRestartNoDuplicate(factory);
        caseConcurrentScanNoDuplicate(factory);
        caseTriggerCacheSignature(factory);
    }

    /**
     * 建被测 repository（只需要 mapper + 分组，与线上装配一致）。
     *
     * @param session 会话
     * @return repository
     */
    private static MybatisJobRepository newRepository(SqlSession session) {
        return new MybatisJobRepository(session.getMapper(JobMapper.class), GROUP);
    }

    /**
     * 构建会话工厂。
     *
     * @param dataSource 数据源
     * @return 会话工厂
     */
    private static SqlSessionFactory buildFactory(DataSource dataSource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("probe", new JdbcTransactionFactory(), dataSource));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(JobMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    /**
     * 确认三张表存在；不存在时用模块自带的 init SQL 建表。
     *
     * @param dataSource 数据源
     * @throws Exception 数据库异常
     */
    private static void prepareSchema(DataSource dataSource) throws Exception {
        String[] tables = {"sys_job", "sys_job_log", "sys_job_cluster"};
        try (Connection connection = dataSource.getConnection()) {
            for (String table : tables) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT 1 FROM " + table + " LIMIT 1");
                } catch (Exception ex) {
                    log.info("[probe] 表 {} 不存在（{}），请先对该库执行 V2.0__init_all_job.sql", table, ex.getMessage());
                    throw new IllegalStateException("缺少表 " + table + "，请先对该库执行 V2.0__init_all_job.sql", ex);
                }
            }
        }
        log.info("[probe] 三张表齐备");
    }

    /**
     * 插入一条被测作业。
     *
     * @param jobNo      作业号
     * @param cron       cron 表达式
     * @param strategy   misfire 策略，可空
     * @param lastTime   上次派发时刻（毫秒）
     * @param nextTime   下次触发时刻（毫秒）
     * @param trigger    是否启用调度
     * @throws Exception 数据库异常
     */
    private static void seedJob(String jobNo, String cron, String strategy,
                               long lastTime, long nextTime, int trigger) throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM sys_job WHERE sys_job_no = '" + jobNo + "'");
            // sys_job_id 没有默认值也没有自增（生产是雪花 id），探针必须自己给；
            // 取 jobNo 哈希落在 90 万段，固定值保证同一用例可重复运行。
            long jobId = 900000L + Math.abs(jobNo.hashCode() % 9000);
            statement.executeUpdate("INSERT INTO sys_job (sys_job_id, sys_job_no, sys_job_name, sys_job_group,"
                            + " sys_job_schedule_type, sys_job_schedule_time, sys_job_trigger_status,"
                            + " sys_job_trigger_last_time, sys_job_trigger_next_time,"
                            + " sys_job_execute_misfire_strategy, sys_job_is_deleted, sys_job_version)"
                            + " VALUES (" + jobId + ", '" + jobNo + "', '" + jobNo + "', '" + GROUP + "',"
                            + " 'CRON', '" + cron + "', " + trigger + ","
                            + " " + lastTime + ", " + nextTime + ","
                            + " " + (strategy == null ? "NULL" : "'" + strategy + "'") + ", 0, 0)");
        }
    }

    /**
     * 只改 cron（模拟「改调度配置但不重启」）。
     *
     * @param jobNo 作业号
     * @param cron  新表达式
     * @throws Exception 数据库异常
     */
    private static void updateSchedule(String jobNo, String cron) throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE sys_job SET sys_job_schedule_time = '" + cron
                    + "' WHERE sys_job_no = '" + jobNo + "'");
        }
    }

    /**
     * 直接摆台账两列（用于「改调度配置但不重启」的用例）。
     *
     * @param jobNo    作业号
     * @param lastTime 上次派发时刻（毫秒）
     * @param nextTime 下次触发时刻（毫秒）
     * @throws Exception 数据库异常
     */
    private static void resetTimes(String jobNo, long lastTime, long nextTime) throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE sys_job SET sys_job_trigger_last_time = " + lastTime
                    + ", sys_job_trigger_next_time = " + nextTime
                    + " WHERE sys_job_no = '" + jobNo + "'");
        }
    }

    /**
     * 读回台账两列。
     *
     * @param jobNo 作业号
     * @return {lastTime, nextTime}
     * @throws Exception 数据库异常
     */
    private static long[] readTimes(String jobNo) throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT sys_job_trigger_last_time, sys_job_trigger_next_time FROM sys_job"
                             + " WHERE sys_job_no = '" + jobNo + "'")) {
            if (rs.next()) {
                long last = rs.getLong(1);
                long next = rs.getLong(2);
                return new long[]{rs.wasNull() ? 0L : last, next};
            }
        }
        return new long[]{-1L, -1L};
    }

    /**
     * 清理被测行。
     *
     * @param jobNo 作业号
     * @throws Exception 数据库异常
     */
    private static void cleanup(String jobNo) throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM sys_job WHERE sys_job_no = '" + jobNo + "'");
        }
    }

    /**
     * 取一个连接。
     *
     * @return 连接
     * @throws Exception 数据库异常
     */
    private static Connection openConnection() throws Exception {
        String url = System.getProperty("job.probe.jdbc.url", "jdbc:mysql://127.0.0.1:3306/job_regress");
        String user = System.getProperty("job.probe.jdbc.user", "root");
        String password = System.getProperty("job.probe.jdbc.password", "");
        return java.sql.DriverManager.getConnection(url, user, password);
    }

    /**
     * 记录一条分节标题。
     *
     * @param text 标题
     */
    private static void say(String text) {
        System.out.println(); // [P3C E1 豁免]
        System.out.println("--- " + text); // [P3C E1 豁免]
    }

    /**
     * 断言并计数。
     *
     * @param desc        断言描述
     * @param condition   条件
     * @param detail      细节（失败时打印）
     */
    private static void check(String desc, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + desc); // [P3C E1 豁免]
        } else {
            failed++;
            System.out.println("  [FAIL] " + desc + "  细节：" + detail); // [P3C E1 豁免]
        }
    }

    /**
     * 保留字段引用，避免 JobProperties 导入被误删（与线上装配同源的配置类型）。
     *
     * @return 默认配置
     */
    static JobProperties defaults() {
        return new JobProperties();
    }
}