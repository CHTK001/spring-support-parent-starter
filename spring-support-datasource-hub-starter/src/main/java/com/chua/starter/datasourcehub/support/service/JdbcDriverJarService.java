package com.chua.starter.datasourcehub.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.config.DatasourceHubProperties;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.entity.MsJdbcDriverJar;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceMapper;
import com.chua.starter.datasourcehub.support.mapper.MsJdbcDriverJarMapper;
import com.chua.starter.datasourcehub.support.model.JdbcDriverJarVO;
import com.chua.starter.datasourcehub.support.support.JdbcDriverSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC 驱动 jar 库服务：上传、列举、删除、按 id 取可连接的驱动文件。
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JdbcDriverJarService {

    /**
     * 台账
     */
    private final MsJdbcDriverJarMapper driverJarMapper;

    /**
     * 数据源台账（删除 jar 前要确认没人引用）
     */
    private final MsDatasourceMapper datasourceMapper;

    /**
     * 配置
     */
    private final DatasourceHubProperties properties;

    /**
     * jar 落盘根目录（首次使用时创建）。
     *
     * @return 根目录
     * @throws IOException 建目录失败
     */
    private Path rootDir() throws IOException {
        Path root = Paths.get(properties.getDriverJarDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    /**
     * 列出全部驱动 jar。
     *
     * @return 驱动 jar 列表
     */
    public ReturnResult<List<JdbcDriverJarVO>> list() {
        List<JdbcDriverJarVO> out = new ArrayList<>();
        for (MsJdbcDriverJar row : driverJarMapper.selectList(
                new LambdaQueryWrapper<MsJdbcDriverJar>().orderByDesc(MsJdbcDriverJar::getId))) {
            out.add(toVO(row));
        }
        return ReturnResult.ok(out);
    }

    /**
     * 上传一个驱动 jar。
     *
     * <p><b>不信任上传的文件名</b>：只取其扩展名做基本校验，落盘名由
     * 「内容指纹 + 净化后的原名」拼成。原名里可能带 {@code ../}，
     * 直接用原名落盘就是目录穿越。</p>
     *
     * @param file             上传文件
     * @param driverClassName  驱动类全名
     * @param remark           备注
     * @return 落库后的驱动 jar
     */
    public ReturnResult<JdbcDriverJarVO> upload(MultipartFile file, String driverClassName, String remark) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("驱动 jar 文件为空");
        }
        String driverClass = driverClassName == null ? "" : driverClassName.trim();
        if (driverClass.isEmpty()) {
            return ReturnResult.error("驱动类名不能为空");
        }
        if (file.getSize() > properties.getDriverJarMaxBytes()) {
            return ReturnResult.error("驱动 jar 超过大小上限 "
                    + (properties.getDriverJarMaxBytes() / 1024 / 1024) + "MB");
        }
        String original = sanitize(file.getOriginalFilename());
        if (!original.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            return ReturnResult.error("只接受 .jar 文件");
        }
        Path root;
        Path target;
        try {
            root = rootDir();
            // 先写临时文件再算指纹，避免把整包读进内存
            Path tmp = Files.createTempFile(root, "upload-", ".part");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            String sha = JdbcDriverSupport.sha256(tmp.toFile());
            String stored = sha.substring(0, 16) + "-" + original;
            target = root.resolve(stored);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return ReturnResult.error("驱动 jar 落盘失败: " + e.getMessage());
        }

        MsJdbcDriverJar row = new MsJdbcDriverJar();
        row.setJarName(original);
        row.setStoredName(target.getFileName().toString());
        row.setDriverClassName(driverClass);
        row.setJarSize(file.getSize());
        try {
            row.setJarSha256(JdbcDriverSupport.sha256(target.toFile()));
        } catch (IOException e) {
            row.setJarSha256("");
        }
        row.setJarRemark(remark == null ? "" : remark.trim());
        row.setCreateTime(LocalDateTime.now());
        driverJarMapper.insert(row);

        // 立刻验证一次：现在失败比用户配好数据源后连不上再排查便宜得多
        try {
            JdbcDriverSupport.driverOf(target.toFile(), driverClass);
        } catch (Exception e) {
            // 校验不通过就把刚传的删掉，不留一个用不了的 jar 在库里
            JdbcDriverSupport.evict(target.toFile());
            driverJarMapper.deleteById(row.getId());
            try {
                Files.deleteIfExists(target);
            } catch (IOException deleteError) {
                // 文件删不掉不该盖掉真正的失败原因（驱动类加载失败），只记日志
                log.warn("[datasource-hub] 校验失败后删除 jar 文件失败: {} -> {}", target, deleteError.getMessage());
            }
            return ReturnResult.error("jar 里找不到或无法实例化驱动类 " + driverClass + ": " + e.getMessage());
        }
        log.info("[datasource-hub] 已上传 JDBC 驱动: {} ({} bytes) driver={}",
                original, file.getSize(), driverClass);
        return ReturnResult.ok(toVO(row));
    }

    /**
     * 删除驱动 jar；仍被数据源引用时拒绝。
     *
     * @param id 驱动 jar ID
     * @return 是否删除成功
     */
    public ReturnResult<Boolean> delete(Long id) {
        MsJdbcDriverJar row = id == null ? null : driverJarMapper.selectById(id);
        if (row == null) {
            return ReturnResult.error("驱动 jar 不存在: " + id);
        }
        Long used = datasourceMapper.selectCount(new LambdaQueryWrapper<MsDatasource>()
                .eq(MsDatasource::getDatasourceDriverJarId, id));
        if (used != null && used > 0) {
            // 直接删会让那些数据源变成「配了驱动、jar 没了」的悬空状态，
            // 报错还指向不到真正原因，所以明确拦下来并报出占用方数量
            return ReturnResult.error("该驱动 jar 仍被 " + used + " 个数据源引用，先改用别的驱动再删");
        }
        driverJarMapper.deleteById(id);
        try {
            Path file = rootDir().resolve(row.getStoredName());
            JdbcDriverSupport.evict(file.toFile());
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("[datasource-hub] 删除 jar 文件失败（台账已删）: {}", e.getMessage());
        }
        return ReturnResult.ok(true);
    }

    /**
     * 取某个驱动 jar 对应的 jar 文件；没配或记录不在时返回 {@code null}。
     *
     * @param driverJarId 驱动 jar ID
     * @return jar 文件；无则 null
     * @throws IOException 定位文件失败
     */
    public java.io.File jarFileOf(Long driverJarId) throws IOException {
        if (driverJarId == null) {
            return null;
        }
        MsJdbcDriverJar row = driverJarMapper.selectById(driverJarId);
        if (row == null) {
            return null;
        }
        Path file = rootDir().resolve(row.getStoredName());
        return Files.isRegularFile(file) ? file.toFile() : null;
    }

    /**
     * 取驱动类名（优先用 jar 台账里登记的，其次用数据源上填的）。
     *
     * @param driverJarId    驱动 jar ID
     * @param fallbackOnRow  数据源上填的驱动类名
     * @return 驱动类名；无法确定时返回 null
     */
    public String driverClassNameOf(Long driverJarId, String fallbackOnRow) {
        if (driverJarId != null) {
            MsJdbcDriverJar row = driverJarMapper.selectById(driverJarId);
            if (row != null && row.getDriverClassName() != null && !row.getDriverClassName().isBlank()) {
                return row.getDriverClassName().trim();
            }
        }
        return fallbackOnRow == null || fallbackOnRow.isBlank() ? null : fallbackOnRow.trim();
    }

    /**
     * 净化上传文件名：只保留基名，去掉路径分隔符与控制字符。
     *
     * @param name 原始文件名
     * @return 净化后的文件名
     */
    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "driver.jar";
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder(base.length());
        for (char c : base.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') {
                sb.append(c);
            }
        }
        String cleaned = sb.toString();
        return cleaned.isEmpty() ? "driver.jar" : cleaned;
    }

    /**
     * 实体转视图。
     *
     * @param row 实体
     * @return 视图
     */
    private JdbcDriverJarVO toVO(MsJdbcDriverJar row) {
        return new JdbcDriverJarVO(row.getId(), row.getJarName(), row.getStoredName(),
                row.getDriverClassName(), row.getJarSize(), row.getJarSha256(),
                row.getJarRemark() == null ? "" : row.getJarRemark(),
                row.getCreateTime() == null ? null : row.getCreateTime().toString());
    }
}