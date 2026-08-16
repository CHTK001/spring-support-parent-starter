package com.chua.starter.datasync.dbprobe;

import com.chua.common.support.file.FileSystem;
import com.chua.common.support.file.builder.WriteBuilder;
import com.chua.datasync.agent.support.DataSyncAgentSink;
import com.chua.datasync.agent.support.DataSyncAgentSource;
import com.chua.datasync.agent.support.sink.CsvDataSyncAgentSink;
import com.chua.datasync.agent.support.sink.JdbcDataSyncAgentSink;
import com.chua.datasync.agent.support.source.DelimitedDataSyncAgentSource;
import com.chua.datasync.agent.support.source.JdbcDataSyncAgentSource;
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
 * @author CH
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

        System.out.println();
        System.out.println("[EXCEL-JDBC-PROBE] ===== 汇总 =====");
        System.out.println("[EXCEL-JDBC-PROBE] 通过场景=" + (5 - failed) + "/5, 失败场景=" + failed);
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
            st.execute("CREATE TABLE src_user (id INT, name VARCHAR(64), age INT, score INT)");
            st.execute("CREATE TABLE dst_user (id INT, name VARCHAR(64), age INT, score INT)");
            st.execute("CREATE TABLE dst_db (id INT, name VARCHAR(64), age INT, score INT)");
            st.execute("CREATE TABLE dst_excel (id INT, name VARCHAR(64), age INT, score INT)");
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