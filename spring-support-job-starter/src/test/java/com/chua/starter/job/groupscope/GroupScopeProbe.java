package com.chua.starter.job.groupscope;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.chua.starter.job.support.log.JobLogQuery;
import com.chua.starter.job.support.log.JobLogRecord;
import com.chua.starter.job.support.store.JobLogEntity;
import com.chua.starter.job.support.store.JobLogMapper;
import com.chua.starter.job.support.store.JobMapper;
import com.chua.starter.job.support.store.MybatisJobLogRepository;
import com.chua.starter.job.support.store.MybatisJobRepository;
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
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * 分组作用域探针: 在<b>真实 MySQL/MariaDB</b> 上验证「概览与日志按作业归属分组收敛」这条修复。
 *
 * <p>对应修复: {@code JobGovernanceService#summary} 的 {@code totalJobs}/{@code recentFailures},
 * 以及 {@code MybatisJobLogRepository} 的 {@code group} 过滤。修复前概览统计全库,
 * 修复后只统计本节点所属 {@code sys_job_group}; 分组过滤经 {@code sys_job_id} 关联台账,
 * 分组内查不到任务时走恒假条件而不是退化成不过滤。</p>
 *
 * <p><b>为什么是探针而不是 JUnit 用例</b>: 本模块《全功能单元测试文档.md》成文规定
 * 「全业务功能验证一律采用启动 Spring 服务测接口 / 驱动真实业务链路的方式, 不使用 JUnit /
 * {@code @SpringBootTest} 断言」。同时本探针刻意<b>不</b>引入 H2 与 Mockito ——
 * 生产库是 MariaDB, 而这次的 bug 出在 SQL 作用域上, 必须在真方言真数据上验;
 * 内存库验不出 {@code sys_job_id} 关联与逻辑删除的真实行为。</p>
 *
 * <p><b>覆盖的失效模式</b>（每条都对应一个曾真实发生、或极易发生的错法）:</p>
 * <ol>
 *   <li><b>分组查不到任务时退化成全表</b>: 期望 0 行。恒假条件若写错就会变成全库失败数,
 *       并且这种错法在「该分组本来就没有失败」时完全看不出来, 必须同时断言库内总数不为 0。</li>
 *   <li><b>按 {@code sys_job_no} 关联</b>: 该编号会被删除后重建复用。探针造一条
 *       「编号属 alpha 但 {@code sys_job_id} 属 beta」的日志, 按编号关联会把它错算进 alpha。</li>
 *   <li><b>未排除软删除台账行</b>: 分组里的软删任务不该再把它的历史日志召回。</li>
 *   <li><b>未排除软删除日志行</b>: {@code sys_job_log_is_deleted=1} 不该进统计。</li>
 *   <li><b>不分组时口径被污染</b>: {@code group} 为空仍应是全库可见口径。</li>
 *   <li><b>时间窗</b>: 带 {@code startTime} 的查询应只统计窗口内的行。</li>
 * </ol>
 *
 * <p><b>运行</b>: 真库不可达时以明确原因跳过（不静默通过）。默认连本机 13306,
 * 通常配合 SSH 端口转发到真库: {@code ssh -L 13306:127.0.0.1:3306 <host>}。
 * 连接参数全部可由系统属性覆盖:</p>
 * <pre>
 * java -Djob.probe.jdbc.url=jdbc:mysql://127.0.0.1:13306/collect_test \
 *      -Djob.probe.jdbc.user=collect -Djob.probe.jdbc.password=*** \
 *      -cp ... com.chua.starter.job.groupscope.GroupScopeProbe
 * </pre>
 *
 * <p>注意 {@code -D} 必须写在主类名<b>之前</b>: 写在后面会被当成应用程序参数传给 {@code main},
 * {@code System.getProperty} 只能拿到默认值, 表现为「口令没发送」的 Access denied。</p>
 *
 * <p>断言失败时进程以退出码 1 结束, 便于脚本直接判成败。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class GroupScopeProbe {

    /**
     * 探针组 A 的分组名
     */
    private static final String GROUP_ALPHA = "probe-grp-alpha";

    /**
     * 探针组 B 的分组名
     */
    private static final String GROUP_BETA = "probe-grp-beta";

    /**
     * 故意不存在的分组: 用于验证恒假条件, 不得退化成全表
     */
    private static final String GROUP_ABSENT = "probe-grp-absent-zzz";

    /**
     * 探针数据前缀: 清理与识别都用它, 避免碰到库里其它行
     */
    private static final String MARK = "PROBE-GS";

    /**
     * 组 A 的存活任务主键
     */
    private static final long JOB_A1 = 990001L;

    /**
     * 组 A 的存活任务主键
     */
    private static final long JOB_A2 = 990002L;

    /**
     * 组 A 的软删除任务主键
     */
    private static final long JOB_A_DEAD = 990003L;

    /**
     * 组 B 的存活任务主键
     */
    private static final long JOB_B1 = 991001L;

    /**
     * 组 A 存活任务的主键集合（字符串形式, 供分页断言比对）
     */
    private static final Set<String> ALPHA_JOB_IDS = Set.of(String.valueOf(JOB_A1), String.valueOf(JOB_A2));

    /**
     * 日志主键自增起点
     */
    private static final long LOG_ID_BASE = 9920000L;

    /**
     * 一天毫秒数
     */
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

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

        log.info("[probe] 目标真库 url={} user={}", redact(url), user);
        if (!reachable(url, user, password)) {
            log.warn("[probe] SKIPPED 真库不可达, 跳过分组作用域探针。"
                    + "需先建好隔离库并做端口转发, 例如: ssh -L 13306:127.0.0.1:3306 <host>");
            return;
        }

        HikariDataSource dataSource = new HikariDataSource();
        try {
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setJdbcUrl(url);
            dataSource.setUsername(user);
            dataSource.setPassword(password);
            dataSource.setMaximumPoolSize(4);

            seed(dataSource);
            probeScope(buildFactory(dataSource), dataSource);
        } catch (Exception ex) {
            log.error("[probe] 探针执行异常, 判定为失败", ex);
            failed++;
        } finally {
            dataSource.close();
        }

        log.info("[probe] GROUPSCOPE_RESULT passed={} failed={}", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
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
     * 程序化装配 MyBatis-Plus 会话工厂（不起 Spring 上下文, 不用 {@code @SpringBootTest}）。
     *
     * @param dataSource 数据源
     * @return 会话工厂
     */
    private static SqlSessionFactory buildFactory(DataSource dataSource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("probe", new JdbcTransactionFactory(), dataSource));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 与生产一致的分页上限, 防止分页溢出成全表
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        configuration.addInterceptor(interceptor);
        configuration.addMapper(JobLogMapper.class);
        configuration.addMapper(JobMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    /**
     * 播种探针数据（纯 JDBC 显式写列, 绕开 {@code SysBase} 审计字段）。
     *
     * @param dataSource 数据源
     * @throws SQLException 库异常
     */
    private static void seed(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("delete from sys_job_log where sys_job_log_no like '" + MARK + "%'");
                statement.executeUpdate("delete from sys_job where sys_job_no like '" + MARK + "%'");
            }
            insertJob(connection, JOB_A1, MARK + "-A1", "alpha.one", GROUP_ALPHA, 0);
            insertJob(connection, JOB_A2, MARK + "-A2", "alpha.two", GROUP_ALPHA, 0);
            insertJob(connection, JOB_A_DEAD, MARK + "-ADEAD", "alpha.dead", GROUP_ALPHA, 1);
            insertJob(connection, JOB_B1, MARK + "-B1", "beta.one", GROUP_BETA, 0);

            long id = LOG_ID_BASE;
            // 组 A 的存活任务: 3 失败 + 2 成功（daysAgo=0 即窗口内, 别写成 1:
            // 写成 1 会让这些行正好落在 24h 窗口边界之外, 使时间窗断言恒为 0）
            for (int i = 0; i < 3; i++) {
                id = insertLog(connection, id++, JOB_A1, MARK + "-A1", "FAILURE", 0, 0);
            }
            id = insertLog(connection, id++, JOB_A2, MARK + "-A2", "FAILURE", 0, 0);
            for (int i = 0; i < 2; i++) {
                id = insertLog(connection, id++, JOB_A1, MARK + "-A1", "SUCCESS", 0, 0);
            }
            // 组 B: 5 失败
            for (int i = 0; i < 5; i++) {
                id = insertLog(connection, id++, JOB_B1, MARK + "-B1", "FAILURE", 0, 0);
            }
            // 组 A 的软删任务: 4 条失败, 必须被排除
            for (int i = 0; i < 4; i++) {
                id = insertLog(connection, id++, JOB_A_DEAD, MARK + "-ADEAD", "FAILURE", 0, 0);
            }
            // 编号复用陷阱: sys_job_no 写 alpha 的编号, 但 sys_job_id 指向 beta 的任务。
            // 按 sys_job_id 关联才算 beta; 按 sys_job_no 关联会被错算进 alpha。
            id = insertLog(connection, id++, JOB_B1, MARK + "-A1", "FAILURE", 0, 0);
            // 软删日志行: 7 条失败挂在组 A 的存活任务上, 必须被排除
            for (int i = 0; i < 7; i++) {
                id = insertLog(connection, id++, JOB_A1, MARK + "-A1", "FAILURE", 1, 0);
            }
            // 组 B 一条 3 天前的失败: 用于验证时间窗
            insertLog(connection, id, JOB_B1, MARK + "-B1", "FAILURE", 0, 3);
            connection.commit();
        }
        log.info("[probe] 种子已写入 groupA=({} 存活 + 1 软删) groupB=(1 存活)", 2);
    }

    /**
     * 插入一条台账行。
     *
     * @param connection 连接
     * @param id         主键
     * @param no         业务编号
     * @param name       任务名
     * @param group      分组
     * @param isDeleted  逻辑删除标记
     * @throws SQLException 库异常
     */
    private static void insertJob(Connection connection, long id, String no, String name,
                                  String group, int isDeleted) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into sys_job (sys_job_id, sys_job_no, sys_job_name, sys_job_group,"
                        + " sys_job_execute_bean, sys_job_trigger_status, sys_job_is_deleted,"
                        + " sys_job_schedule_type, sys_job_dispatch_mode, create_time)"
                        + " values (?,?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, id);
            ps.setString(2, no);
            ps.setString(3, name);
            ps.setString(4, group);
            ps.setString(5, name);
            ps.setInt(6, 1);
            ps.setInt(7, isDeleted);
            ps.setString(8, "CRON");
            ps.setString(9, "LOCAL");
            ps.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    /**
     * 插入一条日志行。
     *
     * @param connection   连接
     * @param id           主键
     * @param jobId        关联任务主键
     * @param no           关联任务编号
     * @param executeCode  结果码
     * @param logIsDeleted 日志逻辑删除标记
     * @param daysAgo      几天前
     * @return 下一个可用主键
     * @throws SQLException 库异常
     */
    private static long insertLog(Connection connection, long id, long jobId, String no,
                                  String executeCode, int logIsDeleted, int daysAgo) throws SQLException {
        long at = System.currentTimeMillis() - daysAgo * DAY_MS;
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into sys_job_log (sys_job_log_id, sys_job_log_no, sys_job_id, sys_job_no,"
                        + " sys_job_log_app, sys_job_log_trigger_bean, sys_job_log_execute_code,"
                        + " sys_job_log_trigger_code, sys_job_log_trigger_time, sys_job_log_trigger_date,"
                        + " sys_job_log_node_id, sys_job_log_is_deleted, create_time)"
                        + " values (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, id);
            ps.setString(2, MARK + "-LOG-" + id);
            ps.setLong(3, jobId);
            ps.setString(4, no);
            ps.setString(5, "scheduler");
            ps.setString(6, "probe.bean");
            ps.setString(7, executeCode);
            ps.setString(8, executeCode);
            ps.setTimestamp(9, new Timestamp(at));
            ps.setTimestamp(10, new Timestamp(at));
            ps.setString(11, "probe-node@" + jobId);
            ps.setInt(12, logIsDeleted);
            ps.setTimestamp(13, new Timestamp(at));
            ps.executeUpdate();
        }
        return id + 1L;
    }

    /**
     * 核心作用域验证。
     *
     * @param factory    会话工厂
     * @param dataSource 数据源（诊断查询走独立连接, 不复用 MyBatis 会话连接）
     */
    private static void probeScope(SqlSessionFactory factory, DataSource dataSource) {
        try (SqlSession session = factory.openSession(true)) {
            JobLogMapper logMapper = session.getMapper(JobLogMapper.class);
            JobMapper jobMapper = session.getMapper(JobMapper.class);
            MybatisJobLogRepository logRepository = new MybatisJobLogRepository(logMapper, jobMapper);
            MybatisJobRepository jobRepository = new MybatisJobRepository(jobMapper, "probe-default");

            // 台账侧: 分组列出必须排除软删任务
            checkNum("listGroup(alpha) 排除软删任务", 2, jobRepository.listGroup(GROUP_ALPHA).size());
            checkNum("listGroup(beta)", 1, jobRepository.listGroup(GROUP_BETA).size());
            checkNum("listGroup(不存在的分组)", 0, jobRepository.listGroup(GROUP_ABSENT).size());

            // 日志侧: 分组口径
            checkNum("日志 group=alpha 的 FAILURE=3+1", 4, count(logRepository, GROUP_ALPHA, "FAILURE"));
            checkNum("日志 group=beta 的 FAILURE=5+复用1+3天前1=7", 7, count(logRepository, GROUP_BETA, "FAILURE"));
            checkNum("日志 group=alpha 的 SUCCESS=2", 2, count(logRepository, GROUP_ALPHA, "SUCCESS"));

            // 失效模式 1: 分组查不到任务必须恒假, 而不是全表。
            // 同时断言库内确有可见失败, 否则 0 可能只是「库里本来就没有失败」而掩盖了退化。
            long absent = count(logRepository, GROUP_ABSENT, "FAILURE");
            long rawVisible = rawCount(session, true);
            checkNum("日志 group=不存在 恒假收敛为 0", 0, absent);
            checkNum("对照: 库内可见 FAILURE=15（证明上面的 0 不是碰巧）", 15, rawVisible);

            // 失效模式 2: 编号复用 —— 按 sys_job_no 关联会把那条 beta 日志错算进 alpha
            checkNum("编号复用: alpha 全码行数=3F+1F+2S=6", 6, count(logRepository, GROUP_ALPHA, null));
            checkNum("编号复用: beta 全码行数=5F+复用1+3天前1=7", 7, count(logRepository, GROUP_BETA, null));

            // 失效模式 3/4: 软删台账任务的 4 条 + 软删日志行的 7 条。
            // 不分组时日志侧不关联台账, 所以软删任务的 4 条仍在内（这正是 A-2 修复前
            // summary() 的口径: 统计全库）; 软删日志行则任何口径下都该被排除。
            checkNum("软删日志行的 7 条被排除: 含软删 22 -> 可见 15",
                    15, rawCount(session, true));
            checkNum("软删日志行的 7 条被排除: 库里含软删共 22", 22, rawCount(session, false));
            checkNum("alpha 分组排除软删台账任务的 4 条", 4, count(logRepository, GROUP_ALPHA, "FAILURE"));

            // 失效模式 5: 不分组仍是全库可见口径（含软删任务的孤儿日志, 修复前的行为）
            checkNum("不分组 FAILURE=15（含软删任务的 4 条孤儿日志）", 15, count(logRepository, null, "FAILURE"));

            // 时间窗: 3 天前那条只应被带时间窗的查询排除。
            // 先把时区与时间边界落到日志上: TIMESTAMP 列的写入与比较都经会话时区转换,
            // 排查这一类问题时必须能看清 JVM 时区、库时区与实际落库边界。
            log.info("[probe] 时区 JVM={} 库={} 落库范围=[{} .. {}]",
                    TimeZone.getDefault().getID(), serverTimeZone(dataSource),
                    scalarTime(dataSource, "min"), scalarTime(dataSource, "max"));
            checkNum("诊断: 只带 24h 时间窗(不分组)命中窗口内 14 条", 14,
                    logRepository.count(windowOnlyQuery("FAILURE", 1)));
            checkNum("诊断: 只带分组(不带时间窗)beta 失败=7", 7,
                    count(logRepository, GROUP_BETA, "FAILURE"));
            checkNum("带 24h 时间窗的 beta 失败排除 3 天前那条=6", 6,
                    logRepository.count(windowQuery(GROUP_BETA, "FAILURE", 1)));

            // 分页: 返回行必须全部落在 alpha 的任务主键集合内
            var page = logRepository.page(query(GROUP_ALPHA, null, 1, 10));
            checkNum("分页 alpha 行数=6", 6, page.getData().size());
            boolean allAlpha = true;
            for (JobLogRecord record : page.getData()) {
                if (!ALPHA_JOB_IDS.contains(record.getJobId())) {
                    allAlpha = false;
                    log.error("[probe] 分页混入了非 alpha 任务行 jobId={} jobNo={}",
                            record.getJobId(), record.getJobNo());
                }
            }
            check("分页不含 beta 任务行（含编号复用行）", true, allAlpha);
        }
    }

    /**
     * 统计某分组某结果码的行数（结果码为空则不限结果码）。
     *
     * @param repository 日志仓库
     * @param group      分组, 可空
     * @param code       结果码, 可空
     * @return 行数
     */
    private static long count(MybatisJobLogRepository repository, String group, String code) {
        return repository.count(query(group, code, 1, 500));
    }

    /**
     * 构造查询条件。
     *
     * @param group    分组, 可空
     * @param code     结果码, 可空
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return 查询条件
     */
    private static JobLogQuery query(String group, String code, int pageNum, int pageSize) {
        JobLogQuery query = new JobLogQuery();
        query.setGroup(group);
        query.setExecuteCode(code);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return query;
    }

    /**
     * 构造带时间窗的查询条件。
     *
     * @param group 分组
     * @param code  结果码
     * @param days  回溯天数
     * @return 查询条件
     */
    private static JobLogQuery windowQuery(String group, String code, int days) {
        JobLogQuery query = query(group, code, 1, 500);
        query.setStartTime(System.currentTimeMillis() - days * DAY_MS);
        return query;
    }

    /**
     * 构造只带时间窗、不带分组的查询条件（用于把时间条件与分组条件拆开定位）。
     *
     * @param code 结果码
     * @param days 回溯天数
     * @return 查询条件
     */
    private static JobLogQuery windowOnlyQuery(String code, int days) {
        JobLogQuery query = query(null, code, 1, 500);
        query.setStartTime(System.currentTimeMillis() - days * DAY_MS);
        return query;
    }

    /**
     * 统计库内失败行数。
     *
     * @param session        会话
     * @param onlyNotDeleted 为 true 时只看未软删的日志行
     * @return 失败行数
     */
    private static long rawCount(SqlSession session, boolean onlyNotDeleted) {
        LambdaQueryWrapper<JobLogEntity> wrapper = new LambdaQueryWrapper<JobLogEntity>()
                .eq(JobLogEntity::getSysJobLogExecuteCode, "FAILURE");
        if (onlyNotDeleted) {
            wrapper.eq(JobLogEntity::getSysJobLogIsDeleted, 0);
        }
        Long counted = session.getMapper(JobLogMapper.class).selectCount(wrapper);
        return counted == null ? 0L : counted;
    }

    /**
     * 读取库会话时区。
     *
     * <p>刻意不复用 MyBatis 会话的连接: {@code openSession(true)} 下每条语句结束即归还连接,
     * {@code session.getConnection()} 拿到的是已关闭的实例, 诊断会一律报
     * {@code Connection is closed} 而看不出真实时区。</p>
     *
     * @param dataSource 数据源
     * @return 时区标识
     */
    private static String serverTimeZone(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select @@session.time_zone")) {
            return rs.next() ? rs.getString(1) : "?";
        } catch (SQLException ex) {
            return "err:" + ex.getMessage();
        }
    }

    /**
     * 读取日志触发时间的极值。
     *
     * @param dataSource 数据源
     * @param which     min 或 max
     * @return 时间字符串
     */
    private static String scalarTime(DataSource dataSource, String which) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select " + which + "(sys_job_log_trigger_time) from sys_job_log")) {
            return rs.next() ? String.valueOf(rs.getString(1)) : "?";
        } catch (SQLException ex) {
            return "err:" + ex.getMessage();
        }
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
     * @param actual 实际值, 便于失败时定位
     */
    private static void check(String name, boolean passed, Object actual) {
        if (passed) {
            GroupScopeProbe.passed++;
            log.info("[probe] [PASS] {} -> {}", name, actual);
        } else {
            GroupScopeProbe.failed++;
            log.error("[probe] [FAIL] {} 实际={}", name, actual);
        }
    }

    /**
     * 遮蔽连接串里的口令。
     *
     * @param url 连接串
     * @return 脱敏后的连接串
     */
    private static String redact(String url) {
        return url == null ? "null" : url.replaceAll("password=[^&]*", "password=***");
    }
}
