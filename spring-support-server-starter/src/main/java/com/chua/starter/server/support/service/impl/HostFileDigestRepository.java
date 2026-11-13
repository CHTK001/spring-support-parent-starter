// [P3C A20 豁免] A31: 含 SQL 语句字面量，属规范允许的数据库访问场景。
package com.chua.starter.server.support.service.impl;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主机文件 MD5 台账的持久化仓库。
 *
 * <p>为什么用 {@link JdbcTemplate} 直写而不是建实体：这张表只有「key → 指纹」一个用途，
 * 不参与任何业务关联，加一套 ORM 映射只会多一处需要同步的样板。写法与 filesystem-starter
 * 的 {@code UploadTaskRepository} 保持一致。</p>
 *
 * <p><b>降级</b>：没有 {@code JdbcTemplate}（未配数据源）或建表失败时，所有方法静默跳过 ——
 * 台账是增强能力，绝不能因为它把上传或浏览搞挂。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Component
public class HostFileDigestRepository {

    /**
     * 建表语句：主键是「存储提供者 + 文件 key」，同名文件换存储不串账
     */
    private static final String DDL = """
            create table if not exists ms_host_file_digest (
              bucket varchar(64) not null,
              file_key varchar(1024) not null,
              md5 char(32) not null,
              size bigint not null default 0,
              source varchar(16) not null default 'UPLOAD',
              updated_at bigint not null,
              primary key (bucket, file_key)
            )""";

    /**
     * 数据源句柄：未配库时为 null
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造。
     *
     * @param provider JdbcTemplate 提供者（可为空）
     */
    public HostFileDigestRepository(ObjectProvider<JdbcTemplate> provider) {
        this.jdbcTemplate = provider.getIfAvailable();
    }

    /**
     * 建表（幂等）。
     *
     * <p>失败只忽略：生产库可能没有建表权限，此时退化为不记账。</p>
     */
    @PostConstruct
    public void init() {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.execute(DDL);
        } catch (Exception ignored) {
            // 没有建表权限 / 非关系型数据源：降级为不记账
        }
    }

    /**
     * 写入 / 更新一条指纹。
     *
     * @param bucket 存储提供者名
     * @param key    文件 key
     * @param md5    小写 hex
     * @param size   字节数
     * @param source 来源：UPLOAD（上传时声明）/ SERVER（服务端按需计算）
     */
    public void save(String bucket, String key, String md5, long size, String source) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    insert into ms_host_file_digest (bucket, file_key, md5, size, source, updated_at)
                    values (?, ?, ?, ?, ?, ?)
                    on duplicate key update md5 = values(md5), size = values(size),
                      source = values(source), updated_at = values(updated_at)
                    """,
                    bucket, key, md5, size, source, System.currentTimeMillis());
        } catch (Exception ignored) {
            // 记账失败不影响上传主流程
        }
    }

    /**
     * 批量查指纹。
     *
     * @param bucket 存储提供者名
     * @param keys   文件 key
     * @return key → md5；无记录的 key 不出现
     */
    public Map<String, String> find(String bucket, Collection<String> keys) {
        Map<String, String> result = new HashMap<>();
        if (jdbcTemplate == null || keys == null || keys.isEmpty()) {
            return result;
        }
        // 占位符按实际条数拼：IN 列表不能用单参数展开，但 key 全部来自服务端归一化后的路径，
        // 仍然走预编译参数绑定，不做字符串拼接
        String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
        String sql = "select file_key, md5 from ms_host_file_digest where bucket = ? and file_key in ("
                + placeholders + ")";
        List<Object> args = new java.util.ArrayList<>(keys.size() + 1);
        args.add(bucket);
        args.addAll(keys);
        try {
            jdbcTemplate.query(sql, rs -> {
                result.put(rs.getString("file_key"), rs.getString("md5"));
            }, args.toArray());
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }

    /**
     * 删除一条指纹（文件被删后不再需要）。
     *
     * @param bucket 存储提供者名
     * @param key    文件 key
     */
    public void delete(String bucket, String key) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update("delete from ms_host_file_digest where bucket = ? and file_key = ?", bucket, key);
        } catch (Exception ignored) {
            // 同上
        }
    }

    /**
     * 文件改名 / 移动后把指纹跟过去。
     *
     * <p>不跟的话，同一个字节流换了 key 就查不到指纹，列表里会凭空多出「未知」，
     * 投放前对包又得重算一次。</p>
     *
     * @param bucket 存储提供者名
     * @param from   原 key
     * @param to     新 key
     */
    public void relocate(String bucket, String from, String to) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "update ms_host_file_digest set file_key = ? where bucket = ? and file_key = ?",
                    to, bucket, from);
        } catch (Exception ignored) {
            // 同上
        }
    }
}
