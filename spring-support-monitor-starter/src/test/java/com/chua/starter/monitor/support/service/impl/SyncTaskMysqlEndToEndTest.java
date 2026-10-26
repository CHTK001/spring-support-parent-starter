package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.Update;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.monitor.support.datasync.SyncTaskHookNotifier;
import com.chua.starter.monitor.support.entity.MsSyncTask;
import com.chua.starter.monitor.support.mapper.MsSyncTaskMapper;
import com.chua.starter.monitor.support.model.SyncTaskRunVO;
import com.chua.starter.monitor.support.model.dto.SyncTargetDTO;
import com.chua.starter.monitor.support.model.dto.SyncTaskDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 同步任务「保存 → 执行 → 水位回写」的真库端到端测试（MySQL）。
 *
 * <p><b>为什么要有这一层</b>：多目标改造里最容易被单测放过、却一定会在线上爆的三件事，都只在
 * <b>真库 + 真驱动</b> 下才成立：</p>
 * <ol>
 *   <li>「一次采集写多张表」是否真的把行落到两张表里，且每张表自己的写入模式（INSERT / UPSERT）都生效；</li>
 *   <li>每个目标的增量水位是否<b>各记各的</b>，下一轮只取新数据（而不是互相覆盖或一起跳变）；</li>
 *   <li>一个目标失败（表被删）时，另一个目标是否照常写完并把水位推进——
 *       这是「A 表失败不影响 B 表推进」这条需求的唯一可信证据。</li>
 * </ol>
 *
 * <p><b>依赖的真实性</b>：走的是生产同一条路径——{@code SyncTaskServiceImpl#runOnce} →
 * {@code JdbcDataSyncAgentSource}（HikariCP）→ 字段映射 → {@code MonitorJdbcSyncSink}（真 JDBC 提交）。
 * 只把两个「不属于数据面」的协作者替换成最薄的桩：{@code MsSyncTaskMapper}（记录 insert/update，
 * 用于断言水位回写 SQL）与 {@code DatasourceService}（返回本机 MySQL 的连接三元组，
 * 代替台账查询）。其余全部是真的。</p>
 *
 * <p><b>自跳过</b>：没有本机 MySQL 时整个类 skip，不污染常规 {@code mvn test}。
 * 需要真跑时显式给参数：</p>
 * <pre>
 * mvn -o test -DskipTests=false -pl spring-support-monitor-starter \
 *     -Dtest=SyncTaskMysqlEndToEndTest \
 *     -Dsync.it.mysql.host=127.0.0.1 -Dsync.it.mysql.port=3306 \
 *     -Dsync.it.mysql.user=root -Dsync.it.mysql.password=root
 * </pre>
 *
 * <p>测试只在 {@code sync_e2e_it} 这个专用库里建表，结束不删库（便于人工复查），
 * 每轮 {@code TRUNCATE} 保证可重复执行。</p>
 *
 * @author CH
 */
@DisplayName("同步任务多目标：真 MySQL 端到端")
class SyncTaskMysqlEndToEndTest {

    /**
     * 测试库名（专用，勿与业务库同名）
     */
    private static final String SCHEMA = "sync_e2e_it";

    /**
     * 数据源 ID：源与目标共用同一个 MySQL，正好覆盖「同库不同表」这种最常见配置
     */
    private static final long DS_ID = 9001L;

    /**
     * 源表
     */
    private static final String SRC = SCHEMA + ".src_rows";

    /**
     * 目标表 A
     */
    private static final String TGT_A = SCHEMA + ".tgt_a";

    /**
     * 目标表 B
     */
    private static final String TGT_B = SCHEMA + ".tgt_b";

    /**
     * JSON 读写（生产用的是 Spring 单例，这里语义一致）
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 本机 MySQL 的 JDBC 地址（含库名），null 表示不可用、整类跳过
     */
    private static String jdbcUrl;

    /**
     * 账号
     */
    private static String dbUser;

    /**
     * 口令
     */
    private static String dbPassword;

    /**
     * 任务行的内存替身：只承接 insert / update / selectById 这三类被真正调用到的方法
     */
    private TaskStore store;

    /**
     * 被测服务（真实执行链）
     */
    private SyncTaskServiceImpl service;

    /**
     * 探活 + 建库建表；连不上就跳过整个类。
     *
     * <p>默认口令留空：本机 MySQL 若允许 root 免密（常见于开发机）则自动跑通；
     * 需要口令时显式传 {@code -Dsync.it.mysql.password=...}。连不上时整类 skip 而不是 fail，
     * 避免没有数据库的机器上常规构建变红。</p>
     */
    @BeforeAll
    static void prepareDatabase() {
        String host = prop("sync.it.mysql.host", "127.0.0.1");
        String port = prop("sync.it.mysql.port", "3306");
        String user = prop("sync.it.mysql.user", "root");
        String password = prop("sync.it.mysql.password", "");
        String baseUrl = "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Shanghai&connectTimeout=3000&socketTimeout=10000";

        boolean reachable = false;
        try (Connection ignored = DriverManager.getConnection(baseUrl, user, password)) {
            reachable = true;
        } catch (Exception e) {
            // 连不上就当本机没有可用 MySQL：断言失败会让每次常规构建都红，这不是本测试的目的
            assumeTrue(false, "本机 MySQL 不可用（" + host + ":" + port + " / " + user + "），跳过真库端到端测试。"
                    + "需要真跑时加参数：-Dsync.it.mysql.password=<口令>（可另配 host/port/user）。原因: "
                    + e.getMessage());
        }
        assumeTrue(reachable, "本机 MySQL 不可用");

        dbUser = user;
        dbPassword = password;
        jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + SCHEMA
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&useUnicode=true&characterEncoding=UTF-8";
        initLambdaCache();
        try (Connection conn = DriverManager.getConnection(baseUrl, user, password);
             Statement statement = conn.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + SCHEMA
                    + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            statement.execute("CREATE TABLE IF NOT EXISTS " + SRC + " ("
                    + "`id` INT NOT NULL PRIMARY KEY,"
                    + "`name` VARCHAR(64) DEFAULT NULL,"
                    + "`amount` DECIMAL(12,2) DEFAULT NULL,"
                    + "`version` INT NOT NULL,"
                    + "`updated_at` DATETIME(3) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute("DROP TABLE IF EXISTS " + TGT_A);
            statement.execute("DROP TABLE IF EXISTS " + TGT_B);
            statement.execute("CREATE TABLE " + TGT_A + " LIKE " + SRC);
            statement.execute("CREATE TABLE " + TGT_B + " LIKE " + SRC);
        } catch (SQLException e) {
            throw new IllegalStateException("准备测试库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 初始化 MyBatis-Plus 的 lambda 列名缓存。
     *
     * <p>生产里由 MyBatis 启动时扫描实体、注册 {@code TableInfo} 完成；这里没有 Spring 上下文，
     * 而 {@code LambdaUpdateWrapper.getSqlSet()} 必须靠它把方法引用翻译成列名。不注册的话，
     * 凡是要读 wrapper SQL 的地方都会抛 {@code can not find lambda cache for this entity}——
     * 这正是本测试真正想验证的「水位回写语句」，缺了它测试就失去意义。</p>
     */
    private static void initLambdaCache() {
        var configuration = new org.apache.ibatis.session.Configuration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, MsSyncTask.class);
    }

    /**
     * 清空数据并重建目标表（上一个用例可能把 tgt_b 删了）。
     */
    @BeforeEach
    void resetTables() throws Exception {
        store = new TaskStore();
        service = new SyncTaskServiceImpl(
                proxy(MsSyncTaskMapper.class, store),
                proxy(DatasourceService.class, new LocalDatasource()),
                JSON, new SyncTaskRunLogBuffer(), new SyncTaskHookNotifier(JSON));
        execute("TRUNCATE TABLE " + SRC);
        execute("DROP TABLE IF EXISTS " + TGT_B);
        execute("CREATE TABLE " + TGT_B + " LIKE " + SRC);
        execute("TRUNCATE TABLE " + TGT_A);
        execute("TRUNCATE TABLE " + TGT_B);
    }

    /**
     * 收尾：无需清理（Hook 通知池只在配置了地址时才入队，测试从未配置）。
     */
    @AfterAll
    static void tearDown() {
        // 保留空实现：若将来加了 Hook 用例，这里负责关闭行通知线程池
    }

    /**
     * 全量模式 + 两个目标：一次采集把同一份数据分别写进两张表。
     *
     * <p>两个目标刻意用不同的写入模式与批量大小，验证「每目标的配置各自生效」
     * （而不是只按第一个目标的配置写两次）。</p>
     */
    @Test
    @DisplayName("全量：两个目标都写入，各自写入模式与批量生效")
    void fullModeWritesEveryTarget() throws Exception {
        insertRows(1, 2, 3, 4);

        MsSyncTask task = newTask("全量-双目标", sourceSql(), List.of(
                target(TGT_A, "INSERT", "FULL", null, null, 2),
                target(TGT_B, "UPSERT", "FULL", null, null, 3)));

        SyncTaskRunVO run = runOnce(task.getId());

        assertEquals(SyncTaskRunVO.STATUS_SUCCESS, run.status(), run.message());
        // 每个目标各自重读一次源（流式，不落内存），4 行 × 2 目标
        assertEquals(8L, run.readCount(), "读行数应为每目标 4 行");
        assertEquals(8L, run.writeCount(), "写行数应为两张表各 4 行");

        assertEquals(4, count(TGT_A), "目标 A 应有 4 行");
        assertEquals(4, count(TGT_B), "目标 B 应有 4 行");
        // 小数位必须原样保留（这条曾经被 VARCHAR 化的中间层吃掉）
        assertEquals(new BigDecimal("12.34"), decimalOf(TGT_A, 1, "amount"));
        assertEquals("行-3", stringOf(TGT_B, 3, "name"));
        System.out.println("[e2e] 全量双目标: read=" + run.readCount() + " write=" + run.writeCount()
                + " A=" + count(TGT_A) + " B=" + count(TGT_B) + " cost=" + run.costMs() + "ms");
    }

    /**
     * 增量模式 + 两个目标：水位各记各的，第二轮只取新数据。
     */
    @Test
    @DisplayName("增量：两个目标各有独立水位，第二轮只取新增行")
    void incrementalKeepsIndependentWatermarks() throws Exception {
        insertRows(1, 2, 3);
        MsSyncTask task = newTask("增量-双目标", sourceSql(), List.of(
                target(TGT_A, "INSERT", "INCREMENTAL", List.of("updated_at", "id"), null, 2),
                target(TGT_B, "UPSERT", "INCREMENTAL", List.of("updated_at", "id"), null, 2)));

        SyncTaskRunVO first = runOnce(task.getId());
        assertEquals(SyncTaskRunVO.STATUS_SUCCESS, first.status(), first.message());
        assertEquals(3, count(TGT_A));
        assertEquals(3, count(TGT_B));

        Map<String, String> seqs = seqsOf(task);
        assertEquals(2, seqs.size(), "两个目标必须各有一条水位: " + seqs);
        assertTrue(seqs.containsKey(key(TGT_A)) && seqs.containsKey(key(TGT_B)), "水位键应为 数据源ID#表: " + seqs);

        // 水位真的通过 mapper 的 update 回写到多目标列（不是只改了内存对象）
        assertTrue(store.seqUpdates().stream().anyMatch(sql -> sql.toLowerCase().contains("target_seqs")),
                "缺少 ms_sync_task_target_seqs 的回写: " + store.seqUpdates());

        insertRows(4, 5);
        SyncTaskRunVO second = runOnce(task.getId());
        assertEquals(SyncTaskRunVO.STATUS_SUCCESS, second.status(), second.message());
        assertEquals(4L, second.readCount(), "第二轮每个目标只应读到 2 行新增数据（2 行 × 2 目标）");
        assertEquals(4L, second.writeCount(), "第二轮应写入 2 行 × 2 目标");
        assertEquals(5, count(TGT_A));
        assertEquals(5, count(TGT_B));

        // 复合水位：取到的是 (updated_at, id) 数组，id 段是最大 id
        List<String> seqValues = JSON.readValue(seqsOf(task).get(key(TGT_A)), new TypeReference<List<String>>() { });
        assertEquals("5", seqValues.get(1), "水位第二段应为主键最大值");
        System.out.println("[e2e] 增量双目标: 首轮写 3×2，次轮写 " + second.writeCount()
                + "，水位=" + seqsOf(task));
    }

    /**
     * 一个目标失败（表被删）时，另一个目标照常写完并推进水位。
     *
     * <p>这是多目标需求里最硬的一条：失败隔离。若实现是「一个事务包住所有目标」，
     * 或者「任一目标失败就整轮回滚」，本用例会立刻暴露。</p>
     */
    @Test
    @DisplayName("失败隔离：B 表被删，A 表照常写入并推进水位")
    void failingTargetDoesNotBlockTheOther() throws Exception {
        insertRows(1, 2);
        MsSyncTask task = newTask("增量-失败隔离", sourceSql(), List.of(
                target(TGT_A, "INSERT", "INCREMENTAL", List.of("updated_at", "id"), null, 2),
                target(TGT_B, "UPSERT", "INCREMENTAL", List.of("updated_at", "id"), null, 2)));

        assertEquals(SyncTaskRunVO.STATUS_SUCCESS, runOnce(task.getId()).status());
        String seqA1 = seqsOf(task).get(key(TGT_A));
        String seqB1 = seqsOf(task).get(key(TGT_B));
        assertNotNull(seqA1);
        assertNotNull(seqB1);
        // B 表被删之前先记下它的行数，用于证明「失败后没有任何残留写入」
        long bRowsBeforeDrop = count(TGT_B);
        assertEquals(2, bRowsBeforeDrop);

        execute("DROP TABLE " + TGT_B);
        insertRows(3);
        SyncTaskRunVO failed = runOnce(task.getId());

        assertEquals(SyncTaskRunVO.STATUS_FAILED, failed.status(), "有目标失败时整轮状态应记 FAILED");
        assertTrue(failed.message().contains("部分目标失败"), "消息应指明是部分失败: " + failed.message());
        assertTrue(failed.message().contains("tgt_b"), "消息应指出哪张表失败: " + failed.message());
        assertEquals(1L, failed.writeCount(), "成功的那张表应写入 1 行");

        assertEquals(3, count(TGT_A), "A 表必须照常写入新增行");

        String seqA2 = seqsOf(task).get(key(TGT_A));
        String seqB2 = seqsOf(task).get(key(TGT_B));
        assertNotEquals(seqA1, seqA2, "A 表水位必须推进");
        assertEquals(seqB1, seqB2, "B 表失败，水位必须原地不动");
        System.out.println("[e2e] 失败隔离: status=" + failed.status()
                + " A行数=3 水位推进=" + (seqA1 + " -> " + seqA2) + " B水位保持=" + seqB2);
    }

    /**
     * 复合水位不漏行：同一版本号下后到的、主键更大的行必须还能被取到。
     *
     * <p>单字段水位（只按 {@code version}）在这种情况下会把后到的行永久漏掉：
     * 第一轮水位已是 7，第二轮 {@code version > 7} 匹配不到任何行。</p>
     */
    @Test
    @DisplayName("复合水位：同版本号后到的行不漏")
    void compositeWatermarkDoesNotMissRows() throws Exception {
        // 两行同一版本号 7，只有主键不同：单字段水位（只按 version）第一轮就把水位推到 7，
        // 之后 version > 7 再也匹配不到任何行，同版本后到的数据会被永久漏掉。
        insertRow(11, 7, "行-11");
        insertRow(12, 7, "行-12");
        MsSyncTask task = newTask("增量-复合水位", sourceSql(), List.of(
                target(TGT_A, "INSERT", "INCREMENTAL", List.of("version", "id"), null, 10)));

        SyncTaskRunVO first = runOnce(task.getId());
        assertEquals(SyncTaskRunVO.STATUS_SUCCESS, first.status(), first.message());
        assertEquals(2, count(TGT_A));
        assertEquals("12", JSON.readValue(seqsOf(task).get(key(TGT_A)),
                new TypeReference<List<String>>() { }).get(1), "水位第二段应停在主键 12");

        // 同 version、更大 id：只按 version 比较的水位会漏掉这一行
        insertRow(13, 7, "行-13");
        SyncTaskRunVO second = runOnce(task.getId());
        assertEquals(SyncTaskRunVO.STATUS_SUCCESS, second.status(), second.message());
        assertEquals(1L, second.writeCount(), "复合水位应只取回同版本后到的那 1 行");
        assertEquals(3, count(TGT_A), "同版本号后到的行不能被漏掉");
        System.out.println("[e2e] 复合水位: 第二轮写 " + second.writeCount() + " 行，A 表共 " + count(TGT_A)
                + "，水位=" + seqsOf(task).get(key(TGT_A)));
    }

    /**
     * 全量 + 「先清空再插入」：两个目标各自清空一次，重复执行不产生重复行。
     *
     * <p>这是多目标最容易出错的一种组合：清空标志若是共享的，第二个目标就不会被清空，
     * 重复执行会把数据翻倍；反之若每次批量都清空，表里只会剩最后一批。</p>
     */
    @Test
    @DisplayName("OVERWRITE：两个目标各自清空，重复执行不翻倍")
    void overwriteClearsEachTargetOnce() throws Exception {
        insertRows(1, 2, 3);
        MsSyncTask task = newTask("全量-清空重写", sourceSql(), List.of(
                target(TGT_A, "OVERWRITE", "FULL", null, null, 2),
                target(TGT_B, "OVERWRITE", "FULL", null, null, 2)));

        assertEquals(SyncTaskRunVO.STATUS_SUCCESS, runOnce(task.getId()).status());
        assertEquals(3, count(TGT_A), "批量 2 + 1，表里应是完整的 3 行而不是最后一批");
        assertEquals(3, count(TGT_B));

        assertEquals(SyncTaskRunVO.STATUS_SUCCESS, runOnce(task.getId()).status());
        assertEquals(3, count(TGT_A), "重复执行必须先清空，不能翻倍");
        assertEquals(3, count(TGT_B));
        System.out.println("[e2e] OVERWRITE 双目标: 两轮后 A=" + count(TGT_A) + " B=" + count(TGT_B));
    }

    /**
     * 保存链：多目标配置写进 {@code ms_sync_task_targets}，首个目标镜像回旧列。
     *
     * <p>顺带验证保存期就用真库校验了增量字段——不存在的列必须当场拒绝，
     * 而不是等到跑的时候才炸。</p>
     */
    @Test
    @DisplayName("保存链：多目标落库 + 首个目标镜像 + 增量字段真库校验")
    void savePathPersistsTargetsAndValidatesAgainstRealDb() throws Exception {
        insertRows(1);
        ReturnResult<MsSyncTask> created = service.create(dto("保存-多目标", List.of(
                target(TGT_A, "UPSERT", "INCREMENTAL", List.of("updated_at", "id"), null, 100),
                target(TGT_B, "INSERT", "FULL", null, null, 50))));

        assertTrue(created.isSuccess(), created.getMsg());
        MsSyncTask saved = created.getData();
        assertNotNull(saved);
        List<SyncTargetDTO> persisted = JSON.readValue(saved.getSyncTaskTargets(),
                new TypeReference<List<SyncTargetDTO>>() { });
        assertEquals(2, persisted.size(), "两个目标都应落库");
        assertEquals(TGT_A, persisted.get(0).targetTable());
        // 旧列镜像第一个目标，保证旧前端/报表仍能读到可用值
        assertEquals(TGT_A, saved.getSyncTaskTargetTable());
        assertEquals("UPSERT", saved.getSyncTaskWriteMode());
        assertEquals("INCREMENTAL", saved.getSyncTaskSyncMode(), "只要有一个增量目标，任务级就应标增量");
        assertEquals("updated_at,id", saved.getSyncTaskIncField(), "任务级增量字段取首个目标的顺序");

        ReturnResult<MsSyncTask> rejected = service.create(dto("保存-坏字段", List.of(
                target(TGT_A, "INSERT", "INCREMENTAL", List.of("updated_at", "no_such_col"), null, 100))));
        assertFalse(rejected.isSuccess(), "不存在的增量字段必须在保存时被拒");
        assertTrue(rejected.getMsg().contains("no_such_col"), "错误应点名具体字段: " + rejected.getMsg());
        System.out.println("[e2e] 保存链: targets=" + saved.getSyncTaskTargets()
                + " 镜像列=" + saved.getSyncTaskTargetTable()
                + " 坏字段拒绝=" + rejected.getMsg());
    }

    // ------------------------------------------------------------------ 辅助

    /**
     * 读取配置项：优先系统属性，其次环境变量，最后默认值。
     *
     * @param key         属性名
     * @param defaultValue 默认值
     * @return 配置值
     */
    private static String prop(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key.replace('.', '_').toUpperCase());
        }
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /**
     * 源 SQL：只取同步需要的列。
     *
     * @return SELECT 语句
     */
    private static String sourceSql() {
        return "SELECT id, name, amount, version, updated_at FROM " + SRC;
    }

    /**
     * 构造一个目标配置。
     *
     * @param table      目标表（库.表）
     * @param writeMode  写入模式
     * @param syncMode   同步模式
     * @param incFields  增量字段（按顺序）
     * @param incBatch   增量每批
     * @param batchSize  批量提交条数
     * @return 目标 DTO
     */
    private static SyncTargetDTO target(String table, String writeMode, String syncMode,
                                        List<String> incFields, Integer incBatch, Integer batchSize) {
        return new SyncTargetDTO(DS_ID, table, writeMode, syncMode, incFields, null, batchSize, incBatch);
    }

    /**
     * 构造保存入参（多目标）。
     *
     * @param name    任务名
     * @param targets 目标列表
     * @return 入参
     */
    private static SyncTaskDTO dto(String name, List<SyncTargetDTO> targets) {
        SyncTargetDTO first = targets.get(0);
        return new SyncTaskDTO(name, "JDBC", DS_ID, sourceSql(), null, null,
                first.targetDatasourceId(), first.targetTable(), targets, null,
                first.writeMode(), first.batchSize(), null, 0,
                first.syncMode(), null, first.incBatchSize(), null, null);
    }

    /**
     * 直接构造任务实体（跳过保存校验，专注执行链）。
     *
     * @param name      任务名
     * @param sql       源 SQL
     * @param targets   目标列表
     * @return 已放进内存替身的任务
     */
    private MsSyncTask newTask(String name, String sql, List<SyncTargetDTO> targets) throws Exception {
        MsSyncTask task = new MsSyncTask();
        task.setId(store.nextId());
        task.setSyncTaskName(name);
        task.setSyncTaskSourceType("JDBC");
        task.setSyncTaskSourceDatasourceId(DS_ID);
        task.setSyncTaskSourceSql(sql);
        task.setSyncTaskTargets(JSON.writeValueAsString(targets));
        task.setSyncTaskSyncMode(targets.stream()
                .anyMatch(item -> "INCREMENTAL".equalsIgnoreCase(item.syncMode())) ? "INCREMENTAL" : "FULL");
        SyncTargetDTO first = targets.get(0);
        task.setSyncTaskTargetDatasourceId(first.targetDatasourceId());
        task.setSyncTaskTargetTable(first.targetTable());
        task.setSyncTaskWriteMode(first.writeMode());
        task.setSyncTaskBatchSize(first.batchSize());
        task.setSyncTaskEnabled(0);
        store.rows().put(task.getId(), task);
        return task;
    }

    /**
     * 执行一次并断言没有走「任务级」失败路径。
     *
     * @param id 任务 ID
     * @return 执行结果
     */
    private SyncTaskRunVO runOnce(Long id) {
        ReturnResult<SyncTaskRunVO> result = service.runOnce(id);
        assertNotNull(result.getData(), "执行未返回结果: " + result.getMsg());
        return result.getData();
    }

    /**
     * 读取任务的独立水位表。
     *
     * @param task 任务
     * @return 键 → 水位
     */
    private static Map<String, String> seqsOf(MsSyncTask task) throws Exception {
        String json = task.getSyncTaskTargetSeqs();
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JSON.readValue(json, new TypeReference<Map<String, String>>() { });
    }

    /**
     * 水位键（与生产实现同口径）。
     *
     * @param table 目标表
     * @return 键
     */
    private static String key(String table) {
        return DS_ID + "#" + table;
    }

    /**
     * 插入 1..n 行，{@code updated_at} 随时间递增、{@code version} 等于 id。
     *
     * @param ids 主键
     */
    private void insertRows(int... ids) throws SQLException {
        for (int id : ids) {
            insertRow(id, id, "行-" + id);
        }
    }

    /**
     * 插入一行。
     *
     * @param id      主键
     * @param version 版本
     * @param name    名称
     */
    private void insertRow(int id, int version, String name) throws SQLException {
        String sql = "INSERT INTO " + SRC + " (id, name, amount, version, updated_at)"
                + " VALUES (?, ?, ?, ?, TIMESTAMPADD(MICROSECOND, ?, TIMESTAMP '2026-01-01 00:00:00'))";
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setBigDecimal(3, new BigDecimal("12.34"));
            ps.setInt(4, version);
            ps.setLong(5, id * 1000L);
            ps.executeUpdate();
        }
    }

    /**
     * 统计表行数。
     *
     * @param table 表
     * @return 行数
     */
    private static long count(String table) throws SQLException {
        try (Connection conn = open();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : -1L;
        }
    }

    /**
     * 取某行某列的小数。
     *
     * @param table  表
     * @param id     主键
     * @param column 列
     * @return 值
     */
    private static BigDecimal decimalOf(String table, int id, String column) throws SQLException {
        try (Connection conn = open();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT " + column + " FROM " + table + " WHERE id = " + id)) {
            return rs.next() ? rs.getBigDecimal(1) : null;
        }
    }

    /**
     * 取某行某列的字符串。
     *
     * @param table  表
     * @param id     主键
     * @param column 列
     * @return 值
     */
    private static String stringOf(String table, int id, String column) throws SQLException {
        try (Connection conn = open();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT " + column + " FROM " + table + " WHERE id = " + id)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /**
     * 执行一条 DDL/DML。
     *
     * @param sql 语句
     */
    private static void execute(String sql) throws SQLException {
        try (Connection conn = open();
             Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * 打开测试库连接。
     *
     * @return 连接
     */
    private static Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
    }

    /**
     * 生成接口代理。
     *
     * @param type    接口
     * @param handler 处理器
     * @return 代理实例
     */
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    /**
     * 原始类型默认值。
     *
     * @param type 返回类型
     * @return 默认值
     */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }

    /**
     * 任务行的内存替身。
     *
     * <p>只实现执行链真正调用到的几个方法；{@code update(null, wrapper)} 把 SQL SET 片段与参数捕获下来，
     * 用来断言「水位确实被写回多目标列」——这是单测里看不见、却决定下一轮会不会重复消费的关键。</p>
     */
    private static final class TaskStore implements InvocationHandler {

        /**
         * 已保存的任务
         */
        private final Map<Long, MsSyncTask> rows = new LinkedHashMap<>();

        /**
         * 每次 update 的 SET 片段
         */
        private final List<String> setSql = new ArrayList<>();

        /**
         * 每次 update 的参数快照
         */
        private final List<Map<String, Object>> params = new ArrayList<>();

        /**
         * 自增主键
         */
        private long nextId = 1L;

        /**
         * 取任务表。
         *
         * @return 任务表
         */
        Map<Long, MsSyncTask> rows() {
            return rows;
        }

        /**
         * 下一个 ID。
         *
         * @return ID
         */
        long nextId() {
            return nextId++;
        }

        /**
         * 捕获到的 SET 片段。
         *
         * @return SET 片段列表
         */
        List<String> seqUpdates() {
            return setSql;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            switch (name) {
                case "selectById":
                    return rows.get(((Number) args[0]).longValue());
                case "insert": {
                    MsSyncTask task = (MsSyncTask) args[0];
                    if (task.getId() == null) {
                        task.setId(nextId());
                    }
                    rows.put(task.getId(), task);
                    return 1;
                }
                case "updateById": {
                    MsSyncTask task = (MsSyncTask) args[0];
                    rows.put(task.getId(), task);
                    return 1;
                }
                case "update": {
                    Update<?, ?> wrapper = (Update<?, ?>) args[1];
                    setSql.add(wrapper.getSqlSet());
                    params.add(new LinkedHashMap<>(
                            ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper)
                                    .getParamNameValuePairs()));
                    return 1;
                }
                case "selectList":
                    return new ArrayList<>(rows.values());
                case "deleteById":
                    rows.remove(((Number) args[0]).longValue());
                    return 1;
                case "toString":
                    return "TaskStore(" + rows.size() + ")";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    /**
     * 数据源中心的替身：把任意数据源 ID 解析成本机测试库。
     *
     * <p>生产里这一步是查 {@code ms_datasource} 台账并拼 URL；端到端测试不关心台账本身，
     * 只关心「拿到 URL 之后整条链路是否成立」，所以这里固定返回本机 MySQL。</p>
     */
    private final class LocalDatasource implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("jdbcInfo".equals(name)) {
                return new DatasourceJdbcInfo(jdbcUrl, dbUser, dbPassword, "com.mysql.cj.jdbc.Driver");
            }
            if ("connection".equals(name)) {
                try {
                    return open();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
            if ("toString".equals(name)) {
                return "LocalDatasource";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            return defaultValue(method.getReturnType());
        }
    }
}
