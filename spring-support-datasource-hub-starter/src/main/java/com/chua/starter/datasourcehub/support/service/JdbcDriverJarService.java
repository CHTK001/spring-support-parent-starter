package com.chua.starter.datasourcehub.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.datasource.support.middleware.MiddlewareClientProvider;
import com.chua.starter.datasourcehub.support.config.DatasourceHubProperties;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.entity.MsJdbcDriverJar;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceMapper;
import com.chua.starter.datasourcehub.support.mapper.MsJdbcDriverJarMapper;
import com.chua.starter.datasourcehub.support.model.JdbcDriverJarVO;
import com.chua.starter.datasourcehub.support.support.JdbcDriverSupport;
import com.chua.starter.datasourcehub.support.support.MiddlewareDriverSupport;
import jakarta.annotation.PostConstruct;
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
     * <p><b>两类 jar 共用一条通道</b>：{@code kind=jdbc} 时入口类必须是
     * {@link java.sql.Driver}；{@code kind=middleware} 时入口类必须是
     * {@code MiddlewareClientProvider}，且它的 {@code name()} 决定数据源类型名。
     * 两种情形都在上传当下实例化一次，失败就把文件与台账一起回滚 ——
     * 不留「传上去了、一用就 ClassNotFound」的半成品。</p>
     *
     * @param file            上传文件
     * @param driverClassName 入口类全名
     * @param remark          备注
     * @param kind            jar 类别，空按 {@code jdbc}
     * @return 落库后的驱动 jar
     */
    public ReturnResult<JdbcDriverJarVO> upload(MultipartFile file, String driverClassName, String remark, String kind) {
        String jarKind = kind == null || kind.isBlank()
                ? JdbcDriverJarVO.KIND_JDBC
                : kind.trim().toLowerCase(java.util.Locale.ROOT);
        if (!JdbcDriverJarVO.KIND_JDBC.equals(jarKind) && !JdbcDriverJarVO.KIND_MIDDLEWARE.equals(jarKind)) {
            return ReturnResult.error("不支持的 jar 类别: " + kind + "（只接受 jdbc / middleware）");
        }
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
        boolean preexisting;
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
            // 记下「这份内容之前是否已经在库里」：同一份 jar 传第二次时不能当成新文件，
            // 更不能在后续校验失败时把已登记的那份删掉
            preexisting = Files.exists(target);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return ReturnResult.error("驱动 jar 落盘失败: " + e.getMessage());
        }

        MsJdbcDriverJar row = new MsJdbcDriverJar();
        row.setJarName(original);
        row.setStoredName(target.getFileName().toString());
        row.setDriverClassName(driverClass);
        row.setJarKind(jarKind);
        row.setJarSize(file.getSize());
        try {
            row.setJarSha256(JdbcDriverSupport.sha256(target.toFile()));
        } catch (IOException e) {
            row.setJarSha256("");
        }
        row.setJarRemark(remark == null ? "" : remark.trim());
        row.setCreateTime(LocalDateTime.now());

        // 立刻验证一次：现在失败比用户配好数据源后连不上再排查便宜得多
        try {
            if (JdbcDriverJarVO.KIND_MIDDLEWARE.equals(jarKind)) {
                MiddlewareClientProvider provider =
                        MiddlewareDriverSupport.providerOf(target.toFile(), driverClass);
                // 类型名由实现自己说了算，回填进台账——让用户手填必然出现对不上的情况
                row.setProviderName(provider.name().toLowerCase(java.util.Locale.ROOT));
            } else {
                JdbcDriverSupport.driverOf(target.toFile(), driverClass);
                row.setProviderName("");
            }
        } catch (Exception e) {
            // 校验不通过就把刚传的删掉，不留一个用不了的 jar 在库里。
            // 但如果是「同一份内容以前已登记过」，文件不是这次新建的，删了会连累老登记
            JdbcDriverSupport.evict(target.toFile());
            MiddlewareDriverSupport.evict(target.toFile());
            if (!preexisting) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException deleteError) {
                    // 文件删不掉不该盖掉真正的失败原因（入口类加载失败），只记日志
                    log.warn("[datasource-hub] 校验失败后删除 jar 文件失败: {} -> {}", target, deleteError.getMessage());
                }
            }
            String what = JdbcDriverJarVO.KIND_MIDDLEWARE.equals(jarKind) ? "中间件客户端" : "驱动类";
            return ReturnResult.error("jar 里找不到或无法实例化" + what + " " + driverClass + ": " + e.getMessage());
        }
        // 同一份内容只登记一次（stored_name 上有唯一键）：重复上传直接复用老登记，
        // 既不会插出重复行，也不会让老数据源引用的记录消失
        MsJdbcDriverJar existing = driverJarMapper.selectOne(new LambdaQueryWrapper<MsJdbcDriverJar>()
                .eq(MsJdbcDriverJar::getStoredName, target.getFileName().toString()));
        if (existing != null) {
            if (existing.getDriverClassName() != null
                    && existing.getDriverClassName().equals(driverClass)
                    && jarKind.equalsIgnoreCase(existing.getJarKind() == null ? JdbcDriverJarVO.KIND_JDBC : existing.getJarKind())) {
                log.info("[datasource-hub] 驱动 jar 已存在，直接复用登记: id={} file={}", existing.getId(), existing.getStoredName());
                return ReturnResult.ok(toVO(existing));
            }
            return ReturnResult.error("这份 jar 已登记为「" + existing.getDriverClassName() + "」，"
                    + "同一份文件不能重复登记成另一个入口类；请先删除原登记再上传");
        }
        driverJarMapper.insert(row);
        log.info("[datasource-hub] 已上传驱动 jar: {} ({} bytes) kind={} entry={}",
                original, file.getSize(), jarKind, driverClass);
        return ReturnResult.ok(toVO(row));
    }

    /**
     * 应用启动时把已上传的中间件 jar 重新装配一遍。
     *
     * <p>类加载器在内存里，进程一重启就没了；台账还在，所以启动时必须按台账重放，
     * 否则用户看到的是「jar 明明在库里，功能却未开放」。单个 jar 加载失败只告警，
     * 不能拖垮整个应用启动。</p>
     */
    @PostConstruct
    public void warmUpMiddlewareJars() {
        List<MsJdbcDriverJar> rows;
        try {
            rows = driverJarMapper.selectList(new LambdaQueryWrapper<MsJdbcDriverJar>()
                    .eq(MsJdbcDriverJar::getJarKind, JdbcDriverJarVO.KIND_MIDDLEWARE));
        } catch (RuntimeException e) {
            log.warn("[datasource-hub] 读取中间件 jar 台账失败，跳过启动装配: {}", e.getMessage());
            return;
        }
        for (MsJdbcDriverJar row : rows) {
            try {
                java.io.File jar = rootDir().resolve(row.getStoredName()).toFile();
                if (!jar.isFile()) {
                    log.warn("[datasource-hub] 中间件 jar 文件缺失，跳过: {}", row.getStoredName());
                    continue;
                }
                MiddlewareDriverSupport.providerOf(jar, row.getDriverClassName());
            } catch (Exception e) {
                log.warn("[datasource-hub] 启动装配中间件 jar 失败: {} -> {}",
                        row.getJarName(), e.getMessage());
            }
        }
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
            MiddlewareDriverSupport.evict(file.toFile());
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
            // 中间件 jar 的 driver_class_name 是 SPI 实现类，不能当 JDBC 驱动类用
            if (row != null && !JdbcDriverJarVO.KIND_MIDDLEWARE.equalsIgnoreCase(row.getJarKind())
                    && row.getDriverClassName() != null && !row.getDriverClassName().isBlank()) {
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
                row.getCreateTime() == null ? null : row.getCreateTime().toString(),
                row.getJarKind() == null || row.getJarKind().isBlank()
                        ? JdbcDriverJarVO.KIND_JDBC : row.getJarKind(),
                row.getProviderName() == null ? "" : row.getProviderName());
    }
}