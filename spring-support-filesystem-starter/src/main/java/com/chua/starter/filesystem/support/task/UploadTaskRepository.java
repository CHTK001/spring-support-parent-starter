// [P3C A20 豁免] A31: 含 SQL 语句字面量，属规范允许的数据库访问场景。
package com.chua.starter.filesystem.support.task;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 上传任务与分片 MD5 的持久化仓库。
 *
 * <p>内存里的 {@link UploadTaskManager} 快照在进程重启后就没了，而「这个文件当时
 * 传上去的 MD5 是多少」是事后追查的硬证据，必须落库。这里用 {@link JdbcTemplate}
 * 直写两张表，不引入 ORM：</p>
 *
 * <ul>
 *   <li>{@code fs_upload_task}：任务级（文件名/大小/分片数/整文件 MD5/状态/对象键）；</li>
 *   <li>{@code fs_upload_chunk}：分片级（每片 MD5/字节数/是否已落盘）。</li>
 * </ul>
 *
 * <p><b>降级策略</b>：没有 {@code JdbcTemplate}（未配数据源）或建表失败时，
 * 所有方法静默跳过 —— 持久化是增强能力，绝不能因为它把上传本身搞挂。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Component
public class UploadTaskRepository {

    /** 建表语句：任务表 */
    private static final String DDL_TASK = """
            create table if not exists fs_upload_task (
              task_id varchar(64) not null primary key,
              file_name varchar(512) not null,
              total_size bigint not null default 0,
              chunk_size bigint not null default 0,
              total_chunks int not null default 1,
              md5 char(32) null,
              state varchar(16) not null,
              object_key varchar(1024) null,
              message varchar(1024) null,
              provider varchar(64) null,
              bucket varchar(255) null,
              created_at bigint not null
            )""";

    /** 建表语句：分片表 */
    private static final String DDL_CHUNK = """
            create table if not exists fs_upload_chunk (
              task_id varchar(64) not null,
              chunk_index int not null,
              md5 char(32) null,
              size bigint not null default 0,
              received tinyint not null default 0,
              primary key (task_id, chunk_index)
            )""";

    /** 可选的数据源句柄：没有配库时为 null，所有写入静默跳过 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造。
     *
     * @param provider JdbcTemplate 提供者（可为空）
     */
    public UploadTaskRepository(ObjectProvider<JdbcTemplate> provider) {
        this.jdbcTemplate = provider.getIfAvailable();
    }

    /**
     * 建表（幂等）。
     *
     * <p>失败只记日志式忽略：生产库可能没有建表权限，此时退化为不持久化。</p>
     */
    @PostConstruct
    public void init() {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.execute(DDL_TASK);
            jdbcTemplate.execute(DDL_CHUNK);
        } catch (Exception ignored) {
            // 没有建表权限 / 非关系型数据源：降级为纯内存
        }
    }

    /**
     * 保存或更新任务级记录。
     *
     * @param task 任务
     */
    public void saveTask(UploadTaskManager.UploadTask task) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    insert into fs_upload_task
                      (task_id, file_name, total_size, chunk_size, total_chunks, md5, state,
                       object_key, message, provider, bucket, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on duplicate key update
                      md5 = values(md5), state = values(state), object_key = values(object_key),
                      message = values(message)
                    """,
                    task.getTaskId(), task.getFileName(), task.getTotalSize(), task.getChunkSize(),
                    task.getTotalChunks(), task.getMd5(), task.getState().name(),
                    task.getObjectKey(), task.getMessage(), task.getProvider(), task.getBucketDir(),
                    task.getCreatedAt());
        } catch (Exception ignored) {
            // 持久化失败不影响上传主流程
        }
    }

    /**
     * 保存分片记录。
     *
     * @param taskId  任务号
     * @param index   分片序号
     * @param md5     分片 MD5（小写 hex）
     * @param size    字节数
     * @param received 是否已落盘
     */
    public void saveChunk(String taskId, int index, String md5, long size, boolean received) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    insert into fs_upload_chunk (task_id, chunk_index, md5, size, received)
                    values (?, ?, ?, ?, ?)
                    on duplicate key update md5 = values(md5), size = values(size), received = values(received)
                    """,
                    taskId, index, md5, size, received ? 1 : 0);
        } catch (Exception ignored) {
            // 同上
        }
    }

    /**
     * 读取任务列表（list-view 用，落库数据优先于内存快照）。
     *
     * @param limit 条数
     * @return 任务行
     */
    public List<Map<String, Object>> listTasks(int limit) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        try {
            return jdbcTemplate.queryForList("""
                    select task_id, file_name, total_size, chunk_size, total_chunks, md5, state,
                           object_key, message, provider, bucket, created_at
                    from fs_upload_task order by created_at desc limit ?
                    """, limit);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 读取某任务的分片详情。
     *
     * @param taskId 任务号
     * @return 分片行（按序号升序）
     */
    public List<Map<String, Object>> listChunks(String taskId) {
        if (jdbcTemplate == null) {
            return new ArrayList<>();
        }
        try {
            return jdbcTemplate.queryForList("""
                    select chunk_index, md5, size, received
                    from fs_upload_chunk where task_id = ? order by chunk_index
                    """, taskId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
