package com.chua.starter.datasourcehub.support.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.config.DatasourceHubProperties;
import com.chua.starter.datasourcehub.support.model.FileDbUploadVO;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件库（SQLite / DuckDB / H2）上传的验收测试。
 *
 * <p>四项行为必须在没有服务端时就能钉死：大小上限、扩展名白名单、落盘目录、
 * 文件名净化（目录穿越防护）。这四样是「传上去的库能不能被数据源用」的边界，
 * 跑到界面才发现太贵。</p>
 *
 * @author CH
 * @since 4.0.0.46
 */
class FileDbUploadServiceTest {

    @TempDir
    Path uploadDir;

    private FileDbUploadService service;

    /**
     * 每次用独立目录，避免用例互相污染。
     */
    @BeforeEach
    void setUp() {
        DatasourceHubProperties properties = new DatasourceHubProperties();
        properties.setFileDbUploadDir(uploadDir.toString());
        properties.setFileDbMaxBytes(64L * 1024 * 1024);
        service = new FileDbUploadService(properties);
    }

    /**
     * 正常的库文件：落盘、给绝对路径、内容完整。
     */
    @Test
    @DisplayName("正常上传：落盘并回绝对路径")
    void uploadOk() throws Exception {
        byte[] content = "magic-bytes-actual-duckdb".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ReturnResult<FileDbUploadVO> result = service.upload(new StubFile("accept.duckdb", content));
        assertTrue(result.isOk(), () -> result.getMsg());
        FileDbUploadVO vo = result.getData();
        assertNotNull(vo);
        Path saved = Path.of(vo.path());
        assertTrue(saved.isAbsolute(), "应返回绝对路径");
        assertTrue(saved.startsWith(uploadDir.toAbsolutePath()), "必须落在受管目录内: " + vo.path());
        assertTrue(saved.getFileName().toString().endsWith("accept.duckdb"), "原名要保留: " + vo.path());
        // 内容指纹前缀：12 位十六进制 + "-"
        String name = saved.getFileName().toString();
        assertTrue(name.length() > "accept.duckdb".length() + 12, "缺少指纹前缀: " + name);
        assertArrayEqualsHex(name.substring(0, 12));
        assertEquals(content.length, (int) vo.size());
        assertEquals("accept.duckdb", vo.name());
        assertArrayEquals(content, Files.readAllBytes(saved));
    }

    /**
     * 超过大小上限（默认 64MB）必须拒绝。
     */
    @Test
    @DisplayName("超 64MB 拒绝")
    void oversizeRejected() {
        byte[] big = new byte[(int) (64L * 1024 * 1024) + 1];
        ReturnResult<FileDbUploadVO> result = service.upload(new StubFile("big.duckdb", big));
        assertFalse(result.isOk());
        assertTrue(result.getMsg().contains("64MB"), () -> result.getMsg());
    }

    /**
     * 扩展名不在白名单（.txt / 无扩展名）必须拒绝。
     */
    @Test
    @DisplayName("扩展名白名单")
    void extensionWhitelist() {
        assertTrue(service.upload(new StubFile("notes.txt", new byte[1])).getMsg().contains("只接受库文件"));
        assertTrue(service.upload(new StubFile("noext", new byte[1])).getMsg().contains("只接受库文件"));
        // 双段后缀与变体都要收
        assertTrue(service.upload(new StubFile("a.mv.db", new byte[1])).isOk());
        assertTrue(service.upload(new StubFile("B.SQLITE3", new byte[1])).isOk());
        assertTrue(service.upload(new StubFile("c.db3", new byte[1])).isOk());
    }

    /**
     * 文件名里带 {@code ../} 不会被当成路径（目录穿越防护）。
     */
    @Test
    @DisplayName("路径穿越被净化")
    void traversalNeutralized() {
        ReturnResult<FileDbUploadVO> result = service.upload(
                new StubFile("../../outside-evil.duckdb", new byte[2]));
        assertTrue(result.isOk(), () -> result.getMsg());
        String name = Path.of(result.getData().path()).getFileName().toString();
        assertFalse(name.contains("../"), "文件名里不应残留路径分隔: " + name);
        assertFalse(name.contains("\\"), "文件名里不应有反斜杠: " + name);
        assertTrue(name.contains(".._.._outside-evil.duckdb"), "净化后应是划线替换: " + name);
        // 文件确实落进受管目录
        assertTrue(Path.of(result.getData().path()).startsWith(uploadDir.toAbsolutePath()));
    }

    /**
     * 空文件与 null 必须拒绝。
     */
    @Test
    @DisplayName("空文件拒绝")
    void emptyRejected() {
        assertFalse(service.upload(new StubFile("x.duckdb", new byte[0])).isOk());
    }

    /**
     * 断言 12 位 ASCII 十六进制。
     */
    private static void assertArrayEqualsHex(String hex) {
        assertEquals(12, hex.length(), "指纹前缀应为 12 位");
        assertTrue(hex.matches("[0-9a-f]{12}"), "指纹前缀应是十六进制: " + hex);
    }

    /**
     * 极简内存桩 MultipartFile（不依赖 spring-test，纯 JUnit 可跑）。
     */
    private static final class StubFile implements MultipartFile {
        private final String original;
        private final byte[] bytes;

        private StubFile(String original, byte[] bytes) {
            this.original = original;
            this.bytes = bytes;
        }

        @Override
        public String getName() {
            return original;
        }

        @Override
        public String getOriginalFilename() {
            return original;
        }

        @Override
        public String getContentType() {
            return "application/octet-stream";
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), bytes);
        }
    }
}