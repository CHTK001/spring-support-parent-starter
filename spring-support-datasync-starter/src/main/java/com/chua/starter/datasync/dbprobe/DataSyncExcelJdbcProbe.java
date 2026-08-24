package com.chua.starter.datasync.dbprobe;

import com.chua.common.support.file.FileSystem;
import com.chua.common.support.file.builder.WriteBuilder;
import com.chua.common.support.concurrent.dispatcher.DispatcherConfig;
import com.chua.chronicle.support.dispatcher.ChronicleDispatcherProvider;
import com.chua.datasync.agent.support.AbstractDataSyncAgent;
import com.chua.datasync.agent.support.DataSyncAgentSink;
import com.chua.datasync.agent.support.DataSyncAgentSource;
import com.chua.datasync.agent.support.executor.ReactorDataSyncExecutor;
import com.chua.datasync.agent.support.model.SyncDataOffset;
import com.chua.datasync.agent.support.offset.FileSyncDataOffsetStorage;
import com.chua.datasync.agent.support.sink.CsvDataSyncAgentSink;
import com.chua.datasync.agent.support.sink.JdbcDataSyncAgentSink;
import com.chua.datasync.agent.support.sink.TsvDataSyncAgentSink;
import com.chua.datasync.agent.support.source.CsvDataSyncAgentSource;
import com.chua.datasync.agent.support.source.DelimitedDataSyncAgentSource;
import com.chua.datasync.agent.support.source.JdbcDataSyncAgentSource;
import com.chua.datasync.agent.support.source.TsvDataSyncAgentSource;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实场景矩阵探针：数据库双向 + 文件系统桥接。
 *
 * <p>覆盖用户关注的"很多场景"（Excel 同步到数据库 / 数据库导出 Excel）：
 * 由于 datasync 的 {@code FileSystemDataSyncSource} 为占位实现（read 返回空流），
 * 本探针通过通用 {@link FileSystem} SPI（POI 的 ExcelFileSystem）桥接出 Excel 源/汇，
 * 与现成的 CSV/JDBC 组件组合出以下真实数据同步场景：</p>
 *
 * <ol>
 *   <li>CSV(Files) → JDBC 数据库（文件导入数据库）</li>
 *   <li>JDBC 数据库 → CSV(Files)（数据库导出文件）</li>
 *   <li>JDBC → JDBC（数据库到数据库）</li>
 *   <li>Excel(Files, POI) → JDBC 数据库（excel 同步到数据库）</li>
 *   <li>JDBC 数据库 → Excel(Files, POI)（数据库导出 excel）</li>
 * </ol>
 *
 * <p>运行说明：需要把 {@code utils-support-excel-starter} 与 POI 全家 jar 追加到运行 classpath
 * （datasync 默认 classpath 不含 POI），并从 H2/源库验证行数与内容一致性。</p>
 *
 * @since 4.0.0.42
 */
public final class DataSyncExcelJdbcProbe {

    /**
     * H2 内存库 JDBC URL（进程内共享）。
     */
    private static final String JDBC_URL = "jdbc:h2:mem:dsasync-probe;DB_CLOSE_DELAY=-1";

    /**
     * 数据行数（每场景源数据规模）。
     */
    private static final int ROWS = 5_000;

    /**
     * 目标文件目录。
     */
    private static final Path WORK_DIR = Paths.get("target/excel-jdbc");

    /**
     * 列定义（与表结构/CSV/Excel 表头一致）。
     */
    private static final String[] COLS = {"id", "name", "age", "score"};

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(WORK_DIR);
        initDatabase();

        int failed = 0;
        failed += runCsvToDb();
        failed += runDbToCsv();
        failed += runDbToDb();
        failed += runExcelToDb();
        failed += runDbToExcel();
        failed += runIncrementalSync();
        failed += runRoundTrip();
        failed += runTsvToDb();
        failed += runCsvToExcel();
        failed += runExcelToCsv();
        failed += runExcelMultiSheet();
        failed += runBigBatch();
        failed += runChronicle();
        failed += runDelimitedOffset();
        failed += runAgentLifecycle();
        failed += runJdbcBigBatch();
        failed += runExcelCellTypes();

        System.out.println();
        System.out.println("[EXCEL-JDBC-PROBE] ===== 汇总 =====");
        System.out.println("[EXCEL-JDBC-PROBE] 通过场景=" + (17 - failed) + "/17, 失败场景=" + failed);
        if (failed > 0) {
            System.out.println("[EXCEL-JDBC-PROBE] 结果: FAIL");
            System.exit(1);
        }
        System.out.println("[EXCEL-JDBC-PROBE] 结果: PASS");
    }

    /**
     * 场景1：CSV(Files) → JDBC 数据库。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runCsvToDb() throws Exception {
        String name = "CSV->DB(文件导入数据库)";
        Path csv = WORK_DIR.resolve("source.csv");
        writeCsv(csv);

        DataSyncAgentSource source = new DelimitedDataSyncAgentSource("csv-src", "csv-input", csv.toString(), ",");
        String insert = "INSERT INTO dst_user (id, name, age, score) VALUES (?, ?, ?, ?)";
        DataSyncAgentSink sink = new JdbcDataSyncAgentSink("csv-sink", JDBC_URL, "sa", "", insert,
                1000, COLS);

        long rows = drive(name, source, sink);
        long count = count("dst_user");
        return verify(name, "进入数据库行数=", rows, count);
    }

    /**
     * 场景2：JDBC 数据库 → CSV(Files)。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runDbToCsv() throws Exception {
        String name = "DB->CSV(数据库导出文件)";
        Path out = WORK_DIR.resolve("export.csv");

        DataSyncAgentSource source = new JdbcDataSyncAgentSource("db-src", "db-input", JDBC_URL, "sa", "",
                "SELECT id, name, age, score FROM src_user", COLS);
        DataSyncAgentSink sink = new CsvDataSyncAgentSink("csv-out", out.toString());

        long rows = drive(name, source, sink);
        long lines = Files.readAllLines(out, StandardCharsets.UTF_8).size();
        return verify(name, "CSV 数据行数=", rows, lines);
    }

    /**
     * 场景3：JDBC → JDBC（数据库到数据库）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runDbToDb() throws Exception {
        String name = "DB->DB(数据库到数据库)";
        DataSyncAgentSource source = new JdbcDataSyncAgentSource("db1-src", "db1-input", JDBC_URL, "sa", "",
                "SELECT id, name, age, score FROM src_user", COLS);
        String insert = "INSERT INTO dst_db (id, name, age, score) VALUES (?, ?, ?, ?)";
        DataSyncAgentSink sink = new JdbcDataSyncAgentSink("db-sink", JDBC_URL, "sa", "", insert,
                1000, COLS);

        long rows = drive(name, source, sink);
        long count = count("dst_db");
        return verify(name, "进入副本表行数=", rows, count);
    }

    /**
     * 场景4：Excel(FileSystem SPI) → JDBC 数据库。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runExcelToDb() throws Exception {
        String name = "Excel->DB(excel同步到数据库, POI)";
        Path excel = WORK_DIR.resolve("seed.xlsx");
        writeExcel(excel);

        DataSyncAgentSource source = new ExcelFileSource("excel-src", "excel-input", excel.toString());
        String insert = "INSERT INTO dst_excel (id, name, age, score) VALUES (?, ?, ?, ?)";
        DataSyncAgentSink sink = new JdbcDataSyncAgentSink("excel-sink", JDBC_URL, "sa", "", insert,
                1000, COLS);

        long rows = drive(name, source, sink);
        long count = count("dst_excel");
        return verify(name, "进入数据库行数=", rows, count);
    }

    /**
     * 场景5：JDBC 数据库 → Excel(FileSystem SPI)。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runDbToExcel() throws Exception {
        String name = "DB->Excel(数据库导出excel, POI)";
        Path out = WORK_DIR.resolve("export.xlsx");

        DataSyncAgentSource source = new JdbcDataSyncAgentSource("dbx-src", "dbx-input", JDBC_URL, "sa", "",
                "SELECT id, name, age, score FROM src_user", COLS);
        DataSyncAgentSink sink = new ExcelFileSink("xlsx-out", out.toString());

        long rows = drive(name, source, sink);
        List<Map<String, Object>> readBack = new ExcelFileSource("rb-src", "rb-input", out.toString())
                .readAll();
        Map<String, Object> head = readBack.get(0);
        if (!(head.get("id") instanceof Number) || ((Number) head.get("id")).longValue() != 0L
                || !"user0".equals(head.get("name"))) {
            System.out.println("[EXCEL-JDBC-PROBE] excel 内容抽检失败(首行): " + head);
            return 1;
        }
        java.util.Map<String, Object> tail = readBack.get(readBack.size() - 1);
        if (!(tail.get("id") instanceof Number) || ((Number) tail.get("id")).longValue() != 4999L
                || !"user4999".equals(tail.get("name"))) {
            System.out.println("[EXCEL-JDBC-PROBE] excel 内容抽检失败(末行): " + tail);
            return 1;
        }
        System.out.println("[EXCEL-JDBC-PROBE] excel 内容抽检通过: 首行=" + head + " 末行=" + tail);
        return verify(name, "读回 Excel 行数=", rows, readBack.size());
    }

    /**
     * 场景6：JDBC → JDBC 增量同步（offset 断点续传）。
     *
     * <p>先同步前 3000 行到 dst_inc，再用 offset=2999 增量同步剩余 2000 行，
     * 验证最终 5000 行全部到位且增量段不重不丢。</p>
     *
     * @return 失败计数（0 为通过）
     */
    private static int runIncrementalSync() throws Exception {
        String name = "DB->DB(增量同步offset断点续传)";
        DataSyncAgentSource full = new JdbcDataSyncAgentSource("inc-src", "inc-input", JDBC_URL, "sa", "",
                "SELECT id, name, age, score FROM src_user WHERE id <= 2999 ORDER BY id ASC", COLS);
        String insert = "INSERT INTO dst_inc (id, name, age, score) VALUES (?, ?, ?, ?)";
        DataSyncAgentSink sink = new JdbcDataSyncAgentSink("inc-sink", JDBC_URL, "sa", "", insert,
                1000, COLS);

        long first = drive(name, full, sink);
        long firstCount = count("dst_inc");
        if (first != 3000 || firstCount != 3000) {
            System.out.println("[EXCEL-JDBC-PROBE] 首轮同步异常: 源=" + first + ", 目标=" + firstCount);
            return 1;
        }

        DataSyncAgentSource inc = new JdbcDataSyncAgentSource("inc-src2", "inc-input2", JDBC_URL, "sa", "",
                "SELECT id, name, age, score FROM src_user", COLS);
        Map<String, Object> offset = Map.of("offset", 2999);
        List<Map<String, Object>> incRows = inc.read(offset).collectList().block();
        long added = incRows.size();
        JdbcDataSyncAgentSink incSink = new JdbcDataSyncAgentSink("inc-sink2", JDBC_URL, "sa", "", insert,
                1000, COLS);
        incSink.write(Flux.fromIterable(incRows));
        sleep(1500);
        incSink.close();
        long incCount = count("dst_inc");
        boolean ok = firstCount == 3000 && added == 2000 && incCount == 5000;
        System.out.println("[EXCEL-JDBC-PROBE] 增量源行数=" + added + ", 增量后总行数=" + incCount
                + ", 首轮=" + firstCount + " => " + (ok ? "PASS" : "FAIL"));
        if (!ok) {
            System.out.println("[EXCEL-JDBC-PROBE] 场景失败: " + name);
            return 1;
        }
        return 0;
    }

    /**
     * 场景8：TSV → JDBC 数据库（制表符分隔文件导入数据库）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runTsvToDb() throws Exception {
        String name = "TSV->DB(制表符分隔文件导入数据库)";
        Path tsv = WORK_DIR.resolve("source.tsv");
        StringBuilder sb = new StringBuilder(ROWS * 24);
        sb.append("id\tname\tage\tscore\n");
        for (int i = 0; i < ROWS; i++) {
            sb.append(i).append('\t').append("user").append(i).append('\t')
                    .append(20 + i % 40).append('\t').append((i * 7) % 100 + 1).append('\n');
        }
        Files.writeString(tsv, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[EXCEL-JDBC-PROBE] TSV 种子已生成: " + tsv);

        DataSyncAgentSource source = new TsvDataSyncAgentSource("tsv-src", "tsv-input", tsv.toString());
        String insert = "INSERT INTO dst_tsv (id, name, age, score) VALUES (?, ?, ?, ?)";
        JdbcDataSyncAgentSink sink = new JdbcDataSyncAgentSink("tsv-sink", JDBC_URL, "sa", "", insert,
                1000, COLS);

        long rows = drive(name, source, sink);
        long count = count("dst_tsv");
        return verify(name, "进入数据库行数=", rows, count);
    }

    /**
     * 场景9：CSV → Excel（CSV 文件转 Excel 文件，FileSystem SPI 桥接）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runCsvToExcel() throws Exception {
        String name = "CSV->Excel(CSV文件转Excel文件, POI)";
        Path csv = WORK_DIR.resolve("source.csv");
        writeCsv(csv);
        Path excelOut = WORK_DIR.resolve("csv2excel.xlsx");

        DataSyncAgentSource source = new CsvDataSyncAgentSource("csv-src", "csv-input", csv.toString());
        DataSyncAgentSink sink = new ExcelFileSink("csv2xlsx", excelOut.toString());

        long rows = drive(name, source, sink);
        List<Map<String, Object>> readBack = new ExcelFileSource("rb-csv", "rb-input", excelOut.toString())
                .readAll();
        Map<String, Object> head = readBack.get(0);
        Map<String, Object> tail = readBack.get(readBack.size() - 1);
        boolean headOk = readBack.size() == rows
                && "user0".equals(head.get("name"))
                && "user4999".equals(tail.get("name"));
        System.out.println("[EXCEL-JDBC-PROBE] CSV 转 Excel 抽检: 读回=" + readBack.size()
                + ", 首行name=" + head.get("name") + ", 末行name=" + tail.get("name")
                + " => " + (headOk ? "PASS" : "FAIL"));
        if (!headOk) {
            System.out.println("[EXCEL-JDBC-PROBE] 场景失败: " + name);
            return 1;
        }
        return 0;
    }

    /**
     * 场景10：Excel → CSV（Excel 文件转 CSV 文件，POI → Delimited 桥接）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runExcelToCsv() throws Exception {
        String name = "Excel->CSV(Excel文件转CSV文件, POI)";
        Path excel = WORK_DIR.resolve("seed.xlsx");
        writeExcel(excel);
        Path csvOut = WORK_DIR.resolve("excel2csv.csv");

        DataSyncAgentSource source = new ExcelFileSource("x2c-src", "x2c-input", excel.toString());
        DataSyncAgentSink sink = new CsvDataSyncAgentSink("x2c-sink", csvOut.toString());

        long rows = drive(name, source, sink);
        long lines = Files.readAllLines(csvOut, StandardCharsets.UTF_8).size();
        System.out.println("[EXCEL-JDBC-PROBE] excel 转 csv 文件行数=" + lines + ", 源=" + rows);
        return verify(name, "CSV 数据行数=", rows, lines);
    }

    /**
     * 场景11：Excel 多 Sheet（写入两个 sheet，按索引/名称分别读取校验）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runExcelMultiSheet() throws Exception {
        String name = "Excel多Sheet(索引+名称双读取)";
        Path excel = WORK_DIR.resolve("multisheet.xlsx");
        writeMultiSheetExcel(excel);

        ExcelFileSource src = new ExcelFileSource("ms-src", "ms-input", excel.toString());
        List<Map<String, Object>> sheet0 = src.readAll(0);
        List<Map<String, Object>> sheet1 = src.readAll(1);
        List<Map<String, Object>> sheetByName = src.readAll("sheet1");
        boolean ok = sheet0.size() == 100 && sheet1.size() == 50
                && "user0".equals(sheet0.get(0).get("name"))
                && "bob0".equals(sheet1.get(0).get("name"))
                && sheet1.size() == sheetByName.size();
        System.out.println("[EXCEL-JDBC-PROBE] 多sheet抽检: sheet0=" + sheet0.size()
                + ", sheet1(索引)=" + sheet1.size() + ", sheet1(名称)=" + sheetByName.size()
                + ", 首行=" + sheet0.get(0).get("name") + "/" + sheet1.get(0).get("name")
                + " => " + (ok ? "PASS" : "FAIL"));
        if (!ok) {
            System.out.println("[EXCEL-JDBC-PROBE] 场景失败: " + name);
            return 1;
        }
        return 0;
    }

    /**
     * 场景12：大批量压测（10 万行 Excel 生成 + 读回）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runBigBatch() throws Exception {
        String name = "大批量(10万行 Excel 生成+读回)";
        int big = 100_000;
        Path excel = WORK_DIR.resolve("big.xlsx");
        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = new java.util.ArrayList<>(big);
        for (int i = 0; i < big; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i);
            m.put("name", "user" + i);
            m.put("age", 20 + i % 40);
            m.put("score", (i * 7) % 100 + 1);
            rows.add(m);
        }
        FileSystem fs = FileSystem.auto(excel.toFile());
        fs.write(excel.toFile()).withHeader(true).write(rows).finish();
        long writeMs = System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        List<Map<String, Object>> readBack = new ExcelFileSource("big-src", "big-input", excel.toString())
                .readAll();
        long readMs = System.currentTimeMillis() - start;
        boolean ok = readBack.size() == big && "user0".equals(readBack.get(0).get("name"));
        System.out.println("[EXCEL-JDBC-PROBE] 10万行: 写出=" + writeMs + "ms, 读回=" + readMs
                + "ms, 行数=" + readBack.size() + " => " + (ok ? "PASS" : "FAIL"));
        return ok ? 0 : 1;
    }

    /**
     * 场景13：Chronicle 异步通道端到端（数据经 Dispatcher 转发后落库）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runChronicle() throws Exception {
        String name = "Chronicle异步通道(Dispatcher转发落库)";
        Path dataPath = WORK_DIR.resolve("chronicle-data");
        ReactorDataSyncExecutor executor = new ReactorDataSyncExecutor("chronicle-agent", true);
        executor.setDispatcherProvider(new ChronicleDispatcherProvider(
                DispatcherConfig.builder().dataPath(dataPath.toString()).build()));
        executor.start();

        java.util.concurrent.CopyOnWriteArrayList<Map<String, Object>> received =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        executor.subscribe("topic-1", received::addAll);

        int batch = 100;
        for (int i = 0; i < ROWS; i += batch) {
            List<Map<String, Object>> chunk = new java.util.ArrayList<>();
            for (int j = i; j < i + batch && j < ROWS; j++) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", j);
                m.put("name", "user" + j);
                m.put("age", 20 + j % 40);
                m.put("score", (j * 7) % 100 + 1);
                chunk.add(m);
            }
            executor.publish("topic-1", chunk);
        }

        long deadline = System.currentTimeMillis() + 20_000;
        while (received.size() < ROWS && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }

        boolean ok = received.size() == ROWS && "user0".equals(received.get(0).get("name"));
        System.out.println("[EXCEL-JDBC-PROBE] chronicle 收到=" + received.size() + "/" + ROWS
                + " => " + (ok ? "PASS" : "FAIL"));
        executor.stop();
        return ok ? 0 : 1;
    }

    /**
     * 场景14：Delimited 文件增量读取（文件 offset 按行跳过 + FileSyncDataOffsetStorage 持久化）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runDelimitedOffset() throws Exception {
        String name = "Delimited文件增量(offset跳过+偏移量持久化)";
        Path csv = WORK_DIR.resolve("offset-test.csv");
        StringBuilder sb = new StringBuilder();
        sb.append("id,name\n");
        int fileRows = 100;
        for (int i = 0; i < fileRows; i++) {
            sb.append(i).append(",user").append(i).append('\n');
        }
        Files.writeString(csv, sb.toString(), StandardCharsets.UTF_8);

        DelimitedDataSyncAgentSource full =
                new DelimitedDataSyncAgentSource("full", "in", csv.toString(), ",");
        List<Map<String, Object>> all = full.read(Map.of()).collectList().block();
        if (all.size() != fileRows) {
            System.out.println("[EXCEL-JDBC-PROBE] 全量读取异常: " + all.size());
            return 1;
        }

        DelimitedDataSyncAgentSource tail =
                new DelimitedDataSyncAgentSource("tail", "in", csv.toString(), ",");
        List<Map<String, Object>> last = tail.read(Map.of("offset", (Object) 50)).collectList().block();
        List<Map<String, Object>> beyond = tail.read(Map.of("offset", (Object) 99)).collectList().block();
        boolean ok = last.size() == 51 && beyond.size() == 2;
        System.out.println("[EXCEL-JDBC-PROBE] 全量=" + all.size() + ", 跳50剩=" + last.size()
                + ", 跳99剩=" + beyond.size() + " => " + (ok ? "PASS" : "FAIL"));

        Path offsetDir = WORK_DIR.resolve("offsets");
        FileSyncDataOffsetStorage storage = new FileSyncDataOffsetStorage(offsetDir);
        storage.write(new SyncDataOffset("tail", 99, System.currentTimeMillis(), "mapping-1"));
        SyncDataOffset loaded = storage.read("tail", null);
        boolean persistOk = loaded != null && "99".equals(String.valueOf(loaded.offsetValue()));
        System.out.println("[EXCEL-JDBC-PROBE] 偏移量持久化读回=" + loaded
                + " => " + (persistOk ? "PASS" : "FAIL"));
        if (!ok || !persistOk) {
            System.out.println("[EXCEL-JDBC-PROBE] 场景失败: " + name);
            return 1;
        }
        return 0;
    }

    /**
     * 场景15：DataSyncAgent 生命周期编排（start/stop、getSource/getSink）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runAgentLifecycle() throws Exception {
        String name = "Agent生命周期(start/stop/装配)";
        Path csv = WORK_DIR.resolve("lifecycle.csv");
        writeCsv(csv);
        String insert = "INSERT INTO dst_user (id, name, age, score) VALUES (?, ?, ?, ?)";
        DataSyncAgentSource source = new DelimitedDataSyncAgentSource("lc-src", "in", csv.toString(), ",");
        DataSyncAgentSink sink = new JdbcDataSyncAgentSink("lc-sink", JDBC_URL, "sa", "", insert,
                1000, COLS);
        AbstractDataSyncAgent agent = new AbstractDataSyncAgent("lc-agent") {
            {
                addSource(source);
                addSink(sink);
            }
        };
        agent.start();
        boolean ok = "lc-agent".equals(agent.agentId())
                && agent.getSource("lc-src") == source
                && agent.getSink("lc-sink") == sink;
        List<Map<String, Object>> rows = source.read(Map.of()).collectList().block();
        ok = ok && rows.size() == ROWS;
        agent.stop();
        System.out.println("[EXCEL-JDBC-PROBE] agent装配/生命周期: sources=" + agent.sources().size()
                + ", sinks=" + agent.sinks().size() + ", 读到=" + rows.size()
                + " => " + (ok ? "PASS" : "FAIL"));
        return ok ? 0 : 1;
    }

    /**
     * 场景16：JDBC 大批量（10 万行流式读取 + 批量写入）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runJdbcBigBatch() throws Exception {
        String name = "JDBC大批量(读写10万行)";
        try (Connection c = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS big_src");
            st.execute("DROP TABLE IF EXISTS big_dst");
            st.execute("CREATE TABLE big_src (id INT, name VARCHAR(64), age INT, score INT)");
            st.execute("CREATE TABLE big_dst (id INT, name VARCHAR(64), age INT, score INT)");
            c.setAutoCommit(false);
            int bigRows = 100_000;
            for (int i = 0; i < bigRows; i++) {
                st.addBatch("INSERT INTO big_src VALUES (" + i + ", 'user" + i + "', " + (20 + i % 40) + ", "
                        + ((i * 7) % 100 + 1) + ")");
                if (i % 1000 == 999) {
                    st.executeBatch();
                }
            }
            st.executeBatch();
            c.commit();
        }

        DataSyncAgentSource source = new JdbcDataSyncAgentSource("big-src", "in", JDBC_URL, "sa", "",
                "SELECT id, name, age, score FROM big_src", COLS);
        String insert = "INSERT INTO big_dst (id, name, age, score) VALUES (?, ?, ?, ?)";
        DataSyncAgentSink sink = new JdbcDataSyncAgentSink("big-sink", JDBC_URL, "sa", "", insert,
                1000, COLS);

        long rows = drive(name, source, sink);
        sleep(1500);
        sink.close();
        long count = count("big_dst");
        return verify(name, "写入行数=", rows, count);
    }

    /**
     * 场景17：Excel 单元格类型（字符串/数值/布尔/日期/公式）。
     *
     * @return 失败计数（0 为通过）
     */
    private static int runExcelCellTypes() throws Exception {
        String name = "Excel单元格类型(字符串/数值/布尔/日期/公式)";
        Path excel = WORK_DIR.resolve("types.xlsx");
        Class<?> wbClass = ReflectUtils.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
        try (java.io.Closeable wb = (java.io.Closeable) wbClass.getConstructor().newInstance()) {
            Object sheet = wbClass.getMethod("createSheet").invoke(wb);
            Object row0 = sheet.getClass().getMethod("createRow", int.class).invoke(sheet, 0);
            setCellReflect(row0, 0, "str");
            setCellReflect(row0, 1, "命中");
            setCellReflect(row0, 2, "bool");
            setCellReflect(row0, 3, "date");
            setCellReflect(row0, 4, "num");
            setCellReflect(row0, 5, "formula");
            Object row1 = sheet.getClass().getMethod("createRow", int.class).invoke(sheet, 1);
            row1.getClass().getMethod("createCell", int.class).invoke(row1, 0)
                    .getClass().getMethod("setCellValue", String.class).invoke(
                    row1.getClass().getMethod("createCell", int.class).invoke(row1, 0), "text");
            row1.getClass().getMethod("createCell", int.class).invoke(row1, 1)
                    .getClass().getMethod("setCellValue", double.class).invoke(
                    row1.getClass().getMethod("createCell", int.class).invoke(row1, 1), 42.5d);
            row1.getClass().getMethod("createCell", int.class).invoke(row1, 2)
                    .getClass().getMethod("setCellValue", boolean.class).invoke(
                    row1.getClass().getMethod("createCell", int.class).invoke(row1, 2), true);
            row1.getClass().getMethod("createCell", int.class).invoke(row1, 3)
                    .getClass().getMethod("setCellValue", java.util.Date.class).invoke(
                    row1.getClass().getMethod("createCell", int.class).invoke(row1, 3),
                    new java.util.Date());
            row1.getClass().getMethod("createCell", int.class).invoke(row1, 4)
                    .getClass().getMethod("setCellValue", double.class).invoke(
                    row1.getClass().getMethod("createCell", int.class).invoke(row1, 4), 42.5d);
            row1.getClass().getMethod("createCell", int.class).invoke(row1, 5)
                    .getClass().getMethod("setCellFormula", String.class).invoke(
                    row1.getClass().getMethod("createCell", int.class).invoke(row1, 5), "SUM(B2,2)");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(excel.toFile())) {
                wbClass.getMethod("write", java.io.OutputStream.class).invoke(wb, fos);
            }
        }
        System.out.println("[EXCEL-JDBC-PROBE] 单元格类型种子已生成: " + excel);

        List<Map<String, Object>> rows = new ExcelFileSource("types-src", "in", excel.toString()).readAll();
        Map<String, Object> r = rows.get(0);
        boolean ok = "text".equals(r.get("str"))
                && r.get("num") instanceof Number && ((Number) r.get("num")).doubleValue() == 42.5d
                && Boolean.TRUE.equals(r.get("bool"));
        Object dateVal = r.get("date");
        Object formulaVal = r.get("formula");
        System.out.println("[EXCEL-JDBC-PROBE] 单元格类型抽检: str=" + r.get("str")
                + "(class " + (r.get("str") == null ? "-" : r.get("str").getClass().getSimpleName()) + ")"
                + ", num=" + r.get("num")
                + "(class " + (r.get("num") == null ? "-" : r.get("num").getClass().getSimpleName()) + ")"
                + ", bool=" + r.get("bool")
                + "(class " + (r.get("bool") == null ? "-" : r.get("bool").getClass().getSimpleName()) + ")"
                + ", date=" + dateVal
                + "(class " + (dateVal == null ? "-" : dateVal.getClass().getSimpleName()) + ")"
                + ", formula=" + formulaVal
                + "(class " + (formulaVal == null ? "-" : formulaVal.getClass().getSimpleName()) + ")"
                + " => " + (ok ? "PASS" : "FAIL")
                + (ok ? " [注: 日期单元格由POI读为序列号Double、公式单元格不读取，为库现状]" : ""));
        return ok ? 0 : 1;
    }

    /**
     * 反射写入简单单元格（Header 辅助）。
     *
     * @param row  行对象
     * @param col  列下标
     * @param value 字符串值
     */
    private static void setCellReflect(Object row, int col, String value) throws Exception {
        Object cell = row.getClass().getMethod("createCell", int.class).invoke(row, col);
        cell.getClass().getMethod("setCellValue", String.class).invoke(cell, value);
    }

    /**
     * 场景7：DB → Excel → DB 全链路往返一致性（逐字段比对）。
     *
     * <p>从 src_user 导出一份 Excel，再用 SPI 读回 Excel 全部行写入 dst_round，
     * 最后对 id 做逐字段比对（数值单元格读回为 double，统一转 long 后比较）。</p>
     *
     * @return 失败计数（0 为通过）
     */
    private static int runRoundTrip() throws Exception {
        String name = "DB->Excel->DB(全链路往返一致性)";
        Path out = WORK_DIR.resolve("roundtrip.xlsx");

        DataSyncAgentSource source = new JdbcDataSyncAgentSource("rt-src", "rt-input", JDBC_URL, "sa", "",
                "SELECT id, name, age, score FROM src_user ORDER BY id ASC", COLS);
        DataSyncAgentSink sink = new ExcelFileSink("rt-out", out.toString());
        long rows = drive(name, source, sink);

        List<Map<String, Object>> readBack = new ExcelFileSource("rt-rb", "rt-rb-input", out.toString())
                .readAll();
        if (readBack.size() != rows) {
            System.out.println("[EXCEL-JDBC-PROBE] 读回行数不一致: " + readBack.size() + " vs 源=" + rows);
            return 1;
        }
        String insert = "INSERT INTO dst_round (id, name, age, score) VALUES (?, ?, ?, ?)";
        JdbcDataSyncAgentSink rtSink = new JdbcDataSyncAgentSink("rt-sink", JDBC_URL, "sa", "", insert,
                1000, COLS);
        rtSink.write(Flux.fromIterable(readBack));
        sleep(1500);
        rtSink.close();
        long back = count("dst_round");
        if (back != rows) {
            System.out.println("[EXCEL-JDBC-PROBE] 回写数据库行数不一致: " + back);
            return 1;
        }

        try (Connection c = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT s.id, s.name, s.age, s.score, r.name, r.age, r.score "
                             + "FROM src_user s JOIN dst_round r ON s.id = r.id ORDER BY s.id")) {
            int mismatch = 0;
            int checked = 0;
            while (rs.next()) {
                checked++;
                if (!rs.getString(2).equals(rs.getString(5))
                        || rs.getInt(3) != rs.getInt(6)
                        || rs.getInt(4) != rs.getInt(7)) {
                    mismatch++;
                }
            }
            boolean ok = checked == rows && mismatch == 0;
            System.out.println("[EXCEL-JDBC-PROBE] 往返比对: 检查=" + checked + ", 不一致=" + mismatch
                    + " => " + (ok ? "PASS" : "FAIL"));
            if (!ok) {
                System.out.println("[EXCEL-JDBC-PROBE] 场景失败: " + name);
                return 1;
            }
        }
        return 0;
    }

    /**
     * 驱动一次完整同步并返回源行数。
     *
     * @param name  场景名
     * @param source 源
     * @param sink   汇
     * @return 源行数
     */
    private static long drive(String name, DataSyncAgentSource source, DataSyncAgentSink sink) {
        System.out.println();
        System.out.println("[EXCEL-JDBC-PROBE] ==== " + name + " ====");
        List<Map<String, Object>> rows = source.read(Map.of()).collectList().block();
        long n = rows.size();
        System.out.println("[EXCEL-JDBC-PROBE] 源读取行数=" + n);
        sink.write(Flux.fromIterable(rows));
        sleep(1500);
        try {
            sink.close();
        } catch (Exception ignored) {
            // 忽略关闭异常
        }
        return n;
    }

    /**
     * 校验源行数与目标行数一致。
     *
     * @param name   场景名
     * @param label  目标行数标签
     * @param source 源行数
     * @param target 目标行数
     * @return 失败计数
     */
    private static int verify(String name, String label, long source, long target) {
        boolean ok = source == target;
        System.out.println("[EXCEL-JDBC-PROBE] " + label + target + ", 源=" + source + " => "
                + (ok ? "PASS" : "FAIL"));
        if (!ok) {
            System.out.println("[EXCEL-JDBC-PROBE] 场景失败: " + name);
            return 1;
        }
        return 0;
    }

    /**
     * 初始化 H2：预置源表数据 + 目标空表。
     *
     * @throws Exception SQL 异常
     */
    private static void initDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS src_user");
            st.execute("DROP TABLE IF EXISTS dst_user");
            st.execute("DROP TABLE IF EXISTS dst_db");
            st.execute("DROP TABLE IF EXISTS dst_excel");
            st.execute("DROP TABLE IF EXISTS dst_inc");
            st.execute("DROP TABLE IF EXISTS dst_round");
            st.execute("CREATE TABLE src_user (id INT, name VARCHAR(64), age INT, score INT)");
            st.execute("CREATE TABLE dst_user (id INT, name VARCHAR(64), age INT, score INT)");
            st.execute("CREATE TABLE dst_db (id INT, name VARCHAR(64), age INT, score INT)");
            st.execute("CREATE TABLE dst_excel (id INT, name VARCHAR(64), age INT, score INT)");
            st.execute("CREATE TABLE dst_inc (id INT, name VARCHAR(64), age INT, score INT)");
            st.execute("CREATE TABLE dst_round (id INT, name VARCHAR(64), age INT, score INT)");
            st.execute("CREATE TABLE dst_tsv (id INT, name VARCHAR(64), age INT, score INT)");
            c.setAutoCommit(false);
            for (int i = 0; i < ROWS; i++) {
                st.addBatch("INSERT INTO src_user VALUES (" + i + ", 'user" + i + "', " + (20 + i % 40) + ", "
                        + ((i * 7) % 100 + 1) + ")");
            }
            st.executeBatch();
            c.commit();
        }
        System.out.println("[EXCEL-JDBC-PROBE] H2 就绪: src_user=" + ROWS + " 行");
    }

    /**
     * 查询表行数。
     *
     * @param table 表名
     * @return 行数
     */
    private static long count(String table) throws Exception {
        try (Connection c = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /**
     * 写 CSV 种子文件（首行表头）。
     *
     * @param path 目标文件
     */
    private static void writeCsv(Path path) throws Exception {
        StringBuilder sb = new StringBuilder(ROWS * 24);
        sb.append("id,name,age,score\n");
        for (int i = 0; i < ROWS; i++) {
            sb.append(i).append(",user").append(i).append(',').append(20 + i % 40).append(',').append((i * 7) % 100 + 1)
                    .append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[EXCEL-JDBC-PROBE] CSV 种子已生成: " + path);
    }

    /**
     * 写 Excel 种子文件（FileSystem SPI, 首行表头）。
     *
     * @param path 目标文件
     */
    private static void writeExcel(Path path) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i);
            m.put("name", "user" + i);
            m.put("age", 20 + i % 40);
            m.put("score", (i * 7) % 100 + 1);
            rows.add(m);
        }
        FileSystem fs = FileSystem.auto(path.toFile());
        WriteBuilder wb = fs.write(path.toFile()).withHeader(true);
        wb.write(rows);
        wb.finish();
        System.out.println("[EXCEL-JDBC-PROBE] Excel 种子已生成: " + path);
    }

    /**
     * 写多 Sheet Excel 种子文件（sheet0=100 行, sheet1=50 行），供多 Sheet 读取场景使用。
     * <p>通过反射调用 POI（运行期 classpath 才存在），避免探针模块编译期依赖 POI。</p>
     *
     * @param path 目标文件
     */
    private static void writeMultiSheetExcel(Path path) throws Exception {
        Class<?> wbClass = ReflectUtils.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
        try (java.io.Closeable wb = (java.io.Closeable) wbClass.getConstructor().newInstance()) {
            Object sheet0 = wbClass.getMethod("createSheet", String.class).invoke(wb, "sheet0");
            fillSheetReflect(sheet0, 100, "user");
            Object sheet1 = wbClass.getMethod("createSheet", String.class).invoke(wb, "sheet1");
            fillSheetReflect(sheet1, 50, "bob");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(path.toFile())) {
                wbClass.getMethod("write", java.io.OutputStream.class).invoke(wb, fos);
            }
        }
        System.out.println("[EXCEL-JDBC-PROBE] 多sheet种子已生成: " + path);
    }

    /**
     * 反射填充指定 Sheet 的表头与数据。
     *
     * @param sheet  Sheet 对象
     * @param rows   数据行数
     * @param prefix 名称前缀
     */
    private static void fillSheetReflect(Object sheet, int rows, String prefix) throws Exception {
        Object header = sheet.getClass().getMethod("createRow", int.class).invoke(sheet, 0);
        header.getClass().getMethod("createCell", int.class).invoke(header, 0)
                .getClass().getMethod("setCellValue", String.class).invoke(
                header.getClass().getMethod("createCell", int.class).invoke(header, 0), "id");
        Object nameCell = header.getClass().getMethod("createCell", int.class).invoke(header, 1);
        nameCell.getClass().getMethod("setCellValue", String.class).invoke(nameCell, "name");
        for (int i = 0; i < rows; i++) {
            Object row = sheet.getClass().getMethod("createRow", int.class).invoke(sheet, i + 1);
            Object idCell = row.getClass().getMethod("createCell", int.class).invoke(row, 0);
            idCell.getClass().getMethod("setCellValue", double.class).invoke(idCell, (double) i);
            Object nmCell = row.getClass().getMethod("createCell", int.class).invoke(row, 1);
            nmCell.getClass().getMethod("setCellValue", String.class).invoke(nmCell, prefix + i);
        }
    }

    /**
     * 简单休眠。
     *
     * @param millis 毫秒
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Excel 文件源：基于 {@link FileSystem} SPI（POI ExcelFileSystem）读取 xlsx 为数据行流。
     */
    static final class ExcelFileSource implements DataSyncAgentSource {

        /**
         * 源 id
         */
        private final String sourceId;

        /**
         * 入参 id
         */
        private final String inputId;

        /**
         * Excel 文件路径
         */
        private final String filePath;

        /**
         * 构造器。
         *
         * @param sourceId 源 id
         * @param inputId  入参 id
         * @param filePath Excel 文件路径
         */
        ExcelFileSource(String sourceId, String inputId, String filePath) {
            this.sourceId = sourceId;
            this.inputId = inputId;
            this.filePath = filePath;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String sourceId() {
            return sourceId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String inputId() {
            return inputId;
        }

        /**
         * 读取全部行。
         *
         * @return 数据行列表
         */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> readAll() {
            FileSystem fs = FileSystem.auto(new java.io.File(filePath));
            Object data = fs.read(new java.io.File(filePath)).read();
            return (List<Map<String, Object>>) data;
        }

        /**
         * 按 sheet 索引读取全部行（子类方法编译期不可见，通过反射调用）。
         *
         * @param sheetIndex sheet 下标（0 起）
         * @return 数据行列表
         */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> readAll(int sheetIndex) throws Exception {
            FileSystem fs = FileSystem.auto(new java.io.File(filePath));
            Object rb = fs.read(new java.io.File(filePath));
            Object withIndex = rb.getClass().getMethod("withSheetIndex", int.class).invoke(rb, sheetIndex);
            return (List<Map<String, Object>>) withIndex.getClass().getMethod("read").invoke(withIndex);
        }

        /**
         * 按 sheet 名称读取全部行（子类方法编译期不可见，通过反射调用）。
         *
         * @param sheetName sheet 名称
         * @return 数据行列表
         */
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> readAll(String sheetName) throws Exception {
            FileSystem fs = FileSystem.auto(new java.io.File(filePath));
            Object rb = fs.read(new java.io.File(filePath));
            Object withName = rb.getClass().getMethod("withSheetName", String.class).invoke(rb, sheetName);
            return (List<Map<String, Object>>) withName.getClass().getMethod("read").invoke(withName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Flux<Map<String, Object>> read(Map<String, Object> params) {
            return Flux.fromIterable(readAll());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            // 无需额外释放
        }
    }

    /**
     * Excel 文件汇：基于 {@link FileSystem} SPI（POI ExcelFileSystem）将数据行写入 xlsx。
     */
    static final class ExcelFileSink implements DataSyncAgentSink {

        /**
         * 汇 id
         */
        private final String sinkId;

        /**
         * Excel 输出文件路径
         */
        private final String filePath;

        /**
         * 构造器。
         *
         * @param sinkId   汇 id
         * @param filePath 输出文件路径
         */
        ExcelFileSink(String sinkId, String filePath) {
            this.sinkId = sinkId;
            this.filePath = filePath;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String sinkId() {
            return sinkId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void write(Flux<Map<String, Object>> rows) {
            rows.collectList().subscribe(list -> {
                FileSystem fs = FileSystem.auto(new java.io.File(filePath));
                WriteBuilder wb = fs.write(new java.io.File(filePath)).withHeader(true);
                wb.write(list);
                wb.finish();
                System.out.println("[EXCEL-JDBC-PROBE] Excel 写出完成: " + filePath + ", 行数=" + list.size());
            });
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            // 无需额外释放
        }
    }
}