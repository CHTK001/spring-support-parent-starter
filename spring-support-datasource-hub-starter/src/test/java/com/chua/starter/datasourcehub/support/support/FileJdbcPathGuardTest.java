package com.chua.starter.datasourcehub.support.support;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileJdbcPathGuard} 单元测试：文件库 JDBC URL 路径白名单与穿越拦截。
 *
 * <p>只断言「错误文案里说了什么」和「谁被放行」，不断言具体根目录字符串 ——
 * 根目录依赖运行环境（user.dir / tmpdir / home），写死会把测试变成
 * 「只能在我机器上过」的谎绿。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class FileJdbcPathGuardTest {

    /**
     * 穿越段：任何形态的 .. 都直接拒绝，不管路径落在哪个根目录下。
     */
    @Test
    void rejectsDotDotTraversal() {
        assertNotNull(FileJdbcPathGuard.pathError("jdbc:sqlite:/tmp/x/../../../etc/cron.d/x", List.of()));
        assertNotNull(FileJdbcPathGuard.pathError("jdbc:h2:file:..\\..\\secret", List.of()));
        assertNotNull(FileJdbcPathGuard.pathError("jdbc:derby:../other/db", List.of()));
        // ../.. 出现在中间段也拦
        assertNotNull(FileJdbcPathGuard.pathError("jdbc:h2:/var/tmp/a/../../b", List.of()));
    }

    /**
     * 白名单外的绝对路径：没有 ..，但规范化后仍在允许目录之外 —— 必须被第二道闸拦下。
     */
    @Test
    void rejectsAbsolutePathOutsideWhitelist() {
        // /etc 不在默认白名单（工作目录/临时目录/主目录）内
        String error = FileJdbcPathGuard.pathError("jdbc:sqlite:/etc/passwd", List.of());
        assertNotNull(error);
        assertTrue(error.contains("越界"), "错误文案应说明越界，实际: " + error);
        assertTrue(error.contains("file-jdbc-allowed-dirs"), "错误文案应给出放行办法，实际: " + error);
    }

    /**
     * 白名单内的绝对路径放行：临时目录是「先试一把」的主用法。
     */
    @Test
    void allowsPathInsideTempDir() {
        String tmp = System.getProperty("java.io.tmpdir");
        String error = FileJdbcPathGuard.pathError("jdbc:sqlite:" + new File(tmp, "demo.db"), List.of());
        assertNull(error, "临时目录内应放行，实际: " + error);
        String h2 = FileJdbcPathGuard.pathError(
                "jdbc:h2:file:" + new File(tmp, "demo") + ";AUTO_SERVER=FALSE", List.of());
        assertNull(h2, "H2 带参数段应放行，实际: " + h2);
    }

    /**
     * 配置追加的目录也要放行 —— 这是「/data/dbs 这类自定义目录」的出口。
     */
    @Test
    void allowsConfiguredExtraDir() throws Exception {
        Path extra = Files.createTempDirectory("fjguard-extra");
        String error = FileJdbcPathGuard.pathError(
                "jdbc:sqlite:" + extra.resolve("a.db"), List.of(extra.toAbsolutePath().toString()));
        assertNull(error, "配置追加目录内应放行，实际: " + error);
    }

    /**
     * 工作目录的相对路径放行（相对路径按工作目录规范化，落进白名单）。
     */
    @Test
    void allowsRelativePathUnderWorkingDir() {
        String error = FileJdbcPathGuard.pathError("jdbc:sqlite:data/demo.db", List.of());
        assertNull(error, "工作目录下的相对路径应放行，实际: " + error);
    }

    /**
     * 不碰本地磁盘的形态一律放行：这些 URL 里抠不出「文件路径」。
     */
    @Test
    void ignoresNonFileForms() {
        assertNull(FileJdbcPathGuard.pathError("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", List.of()));
        assertNull(FileJdbcPathGuard.pathError("jdbc:h2:tcp://db-host:9092/demo", List.of()));
        assertNull(FileJdbcPathGuard.pathError("jdbc:sqlite::memory:", List.of()));
        assertNull(FileJdbcPathGuard.pathError("jdbc:derby:memory:demo", List.of()));
        assertNull(FileJdbcPathGuard.pathError("jdbc:hsqldb:hsql://host:9001/demo", List.of()));
        assertNull(FileJdbcPathGuard.pathError("jdbc:hsqldb:mem:demo", List.of()));
        // 网络型 JDBC URL 与文件族无关，不归本守卫管
        assertNull(FileJdbcPathGuard.pathError("jdbc:mysql://127.0.0.1:3306/demo", List.of()));
        assertNull(FileJdbcPathGuard.pathError(null, List.of()));
    }

    /**
     * 参数段要先切掉再看路径：H2 的 {@code ;AUTO_SERVER=FALSE} 不能混进路径比较，
     * 否则「路径在白名单内」会被参数尾巴带偏。
     */
    @Test
    void stripsParameterSegmentsBeforePathCheck() {
        String tmp = System.getProperty("java.io.tmpdir");
        String error = FileJdbcPathGuard.pathError(
                "jdbc:sqlite:" + new File(tmp, "demo.db") + "?mode=ro", List.of());
        assertNull(error, "SQLite URI 参数段应被剥离，实际: " + error);
        // INIT 等参数尾巴在切参后不参与路径判断：路径 /tmp/db 本身合法就不该误报
        assertNull(FileJdbcPathGuard.pathError(
                "jdbc:h2:" + new File(tmp, "db") + ";INIT=RUNSCRIPT FROM 'x.sql'", List.of()));
    }
}
