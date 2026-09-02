package com.chua.starter.ai.training.support.service.impl;

import com.chua.starter.ai.training.support.config.TrainProperties;
import com.chua.starter.ai.training.support.service.DataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DataServiceImpl} 数据服务单元测试：ZIP 魔数校验、文件名净化、
 * 解压落盘与 Zip-Slip 越界拦截。
 *
 * <p>构造层面直接把 {@link TrainProperties} 的上传/数据根目录指向临时目录，
 * 不触碰默认盘符路径（Windows 默认 C:\aias\file）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class DataServiceTest {

    /** ZIP 本地文件头魔数（合法）。 */
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4, 0, 0};

    @TempDir
    Path tempDir;

    /** 组装服务：上传目录/数据根目录指向临时目录。 */
    private DataService newService() {
        TrainProperties properties = new TrainProperties();
        properties.setUploadPath(tempDir.resolve("upload").toString());
        properties.setDataRootPath(tempDir.resolve("data").toString());
        return new DataServiceImpl(properties);
    }

    private Path uploadDir() {
        return tempDir.resolve("upload");
    }

    private Path dataRoot() {
        return tempDir.resolve("data");
    }

    // ------------------------------------------------------------- 魔数校验

    @Test
    @DisplayName("非 ZIP 内容被魔数校验拦截")
    void saveRejectsNonZipMagic() {
        DataService service = newService();
        assertThatThrownBy(() -> service.saveUploadedFile(null, "a.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是有效的 ZIP");
        assertThatThrownBy(() -> service.saveUploadedFile(new byte[]{1, 2, 3}, "a.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是有效的 ZIP");
        assertThatThrownBy(() -> service.saveUploadedFile(
                "#!/bin/sh\necho hi".getBytes(StandardCharsets.UTF_8), "a.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是有效的 ZIP");
    }

    @Test
    @DisplayName("合法 ZIP 魔数保存成功，文件落盘并带回绝对路径")
    void saveStoresValidZip() throws Exception {
        DataService service = newService();
        String saved = service.saveUploadedFile(ZIP_MAGIC, "cats.v1.zip");

        Path savedPath = Path.of(saved);
        assertThat(savedPath).exists();
        assertThat(savedPath.getParent()).isEqualTo(uploadDir().toAbsolutePath());
        String fileName = savedPath.getFileName().toString();
        // UUID(36) + "_" + 净化后文件名
        assertThat(fileName).matches("[0-9a-f-]{36}_cats\\.v1\\.zip");
        assertThat(Files.readAllBytes(savedPath)).isEqualTo(ZIP_MAGIC);
    }

    // ------------------------------------------------------------- 文件名净化

    @Test
    @DisplayName("sanitizeFileName 剥离路径只留基名")
    void sanitizeKeepsBasenameOnly() {
        assertThat(DataServiceImpl.sanitizeFileName("..\\..\\evil.zip")).isEqualTo("evil.zip");
        assertThat(DataServiceImpl.sanitizeFileName("C:\\fakepath\\train.zip")).isEqualTo("train.zip");
        assertThat(DataServiceImpl.sanitizeFileName("a/b/c/data.zip")).isEqualTo("data.zip");
    }

    @Test
    @DisplayName("sanitizeFileName 剔除非法字符与空值回退")
    void sanitizeStripsIllegalCharsAndFallsBack() {
        // Windows 非法字符 \ / : * ? " < > | 与控制字符全部剔除
        assertThat(DataServiceImpl.sanitizeFileName("a:b<c>d|e?.zip")).isEqualTo("abcde.zip");
        assertThat(DataServiceImpl.sanitizeFileName(null)).isEqualTo(DataServiceImpl.DEFAULT_FILE_NAME);
        assertThat(DataServiceImpl.sanitizeFileName("///\\\\")).isEqualTo(DataServiceImpl.DEFAULT_FILE_NAME);
        assertThat(DataServiceImpl.sanitizeFileName("   ")).isEqualTo(DataServiceImpl.DEFAULT_FILE_NAME);
    }

    @Test
    @DisplayName("带路径的原始文件名保存时不会逃逸上传目录")
    void saveDoesNotEscapeUploadDir() throws Exception {
        DataService service = newService();
        // 恶意 originalFilename：若直接拼 Path.resolve 将解析到上级目录（需中间目录巧合存在才落盘，
        // 净化后必然落在上传目录内）
        String saved = service.saveUploadedFile(ZIP_MAGIC, "..\\..\\escape.zip");
        Path savedPath = Path.of(saved);
        assertThat(savedPath.getFileName().toString()).endsWith("_escape.zip");
        assertThat(savedPath.getParent()).isEqualTo(uploadDir().toAbsolutePath());
    }

    // ------------------------------------------------------------- 解压与越界拦截

    @Test
    @DisplayName("ZIP 解压到数据根目录 UUID 子目录且内容完整")
    void unzipExtractsContentToUuidDir() throws Exception {
        DataService service = newService();
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("images/a.jpg", "IMG");
        entries.put("labels/a.txt", "0 cat");
        entries.put("data.yaml", "path: dataset\nnames:\n  0: cat\n");
        Path zip = buildZip("dataset.zip", entries);

        String dest = service.unzip(zip.toString());

        Path destDir = Path.of(dest);
        assertThat(destDir).exists();
        assertThat(destDir.getParent()).isEqualTo(dataRoot().toAbsolutePath());
        // UUID 命名的子目录
        assertThat(destDir.getFileName().toString())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(destDir.resolve("images/a.jpg")).exists();
        assertThat(destDir.resolve("images/a.jpg")).hasContent("IMG");
        assertThat(destDir.resolve("labels/a.txt")).hasContent("0 cat");
        assertThat(destDir.resolve("data.yaml")).hasContent(entries.get("data.yaml"));
    }

    @Test
    @DisplayName("Zip-Slip 越界条目被拒绝且不产生文件")
    void unzipRejectsPathTraversalEntry() throws Exception {
        DataService service = newService();
        Path zip = buildZip("evil.zip", Map.of("../escape.txt", "pwned"));

        assertThatThrownBy(() -> service.unzip(zip.toString()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("路径越界");
        // 逃逸目标不得被创建
        assertThat(tempDir.resolve("escape.txt")).doesNotExist();
    }

    // ------------------------------------------------------------- 辅助

    /** 用 java.util.zip 构造最小可用 ZIP（commons-compress 读取兼容）。 */
    private Path buildZip(String fileName, Map<String, String> entries) throws IOException {
        Path zip = tempDir.resolve(fileName);
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zip;
    }
}
