// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.config.DatasourceHubProperties;
import com.chua.starter.datasourcehub.support.support.JdbcDriverSupport;
import com.chua.starter.datasourcehub.support.model.FileDbUploadVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

/**
 * 文件库（SQLite / DuckDB / H2）库文件上传。
 *
 * <p><strong>为什么不做成 pages/filesystem 那套分片上传</strong>：那套的落点是
 * 对象存储（provider / bucket / 上传任务 / SSE），而单文件库要的是「服务器上
 * 一个可写的绝对路径」—— 传到 bucket 之后还得再搬一次到本地目录，反而多一层，
 * 也绕开了 {@code fileJdbcAllowedDirs} 的路径白名单。这里直接落到受管目录，
 * 返回的路径本身就在白名单内，填进数据源就能建连。</p>
 *
 * <p><strong>只收小文件</strong>：默认上限 64MB（{@code plugin.datasource-hub.file-db-max-bytes}）。
 * 单文件库正常在几 MB；上限的意义是防止误传一个几百 MB 的数据仓库文件把磁盘写满。</p>
 *
 * <p><strong>不信任上传的文件名</strong>：只取扩展名做校验，落盘名由「内容指纹 +
 * 净化后的原名」拼成 —— 原名里的 {@code ../} 直接落盘就是目录穿越。</p>
 *
 * @author CH
 * @since 4.0.0.46
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDbUploadService {

    /**
     * 允许的库文件扩展名。
     *
     * <p>覆盖 SQLite（.db/.sqlite/.sqlite3/.db3）、DuckDB（.duckdb）、
     * H2（.mv.db/.h2.db/.sdb）。不做内容嗅探：这几种格式的魔数各不相同，
     * 而「扩展名对、内容不对」时内核建连会直接报错，比在这里猜更准。</p>
     */
    private static final Set<String> ALLOWED_SUFFIXES = Set.of(
            ".db", ".db3", ".sqlite", ".sqlite3", ".duckdb", ".mv.db", ".h2.db", ".sdb");

    /**
     * 数据源中心配置
     */
    private final DatasourceHubProperties properties;

    /**
     * 上传一个库文件。
     *
     * @param file 上传文件
     * @return 落盘后的绝对路径与大小
     */
    public ReturnResult<FileDbUploadVO> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("库文件为空");
        }
        long maxBytes = properties.getFileDbMaxBytes();
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            return ReturnResult.error("库文件超过大小上限 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String original = sanitize(file.getOriginalFilename());
        if (!hasAllowedSuffix(original)) {
            return ReturnResult.error("只接受库文件（db / db3 / sqlite / sqlite3 / duckdb / mv.db / h2.db / sdb）");
        }
        Path root;
        Path target;
        try {
            root = rootDir();
            // 先落临时文件再算指纹：不把整包读进内存
            Path tmp = Files.createTempFile(root, "upload-", ".part");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            String sha = JdbcDriverSupport.sha256(tmp.toFile());
            target = root.resolve(sha.substring(0, 12) + "-" + original);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return ReturnResult.error("库文件落盘失败: " + e.getMessage());
        }
        log.info("[datasource-hub] 文件库上传 name={} size={} path={}", original, file.getSize(), target);
        return ReturnResult.ok(new FileDbUploadVO(original, file.getSize(), target.toString()));
    }

    /**
     * 落盘目录（不存在则创建）。
     *
     * @return 绝对路径目录
     * @throws IOException 创建失败
     */
    private Path rootDir() throws IOException {
        Path dir = new java.io.File(properties.getFileDbUploadDir()).toPath().toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    /**
     * 扩展名是否在白名单内（大小写不敏感，支持 .mv.db 这种双段后缀）。
     *
     * @param name 文件名
     * @return true=允许
     */
    private boolean hasAllowedSuffix(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return ALLOWED_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    /**
     * 净化文件名：只保留字母数字与 . _ -，其余替换成下划线。
     *
     * @param name 原始文件名
     * @return 净化后的文件名
     */
    private String sanitize(String name) {
        String base = name == null || name.isBlank() ? "filedb" : name.trim();
        base = base.replaceAll("[\\\\/]", "_").replaceAll("[^A-Za-z0-9._-]", "_");
        base = base.replace(" ", "_");
        return base;
    }}