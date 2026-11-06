package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目操作日志实体。
 *
 * <p>发布管理里每一次项目操作（创建/修改/删除/启动/停止/重启/升级/备份/回滚/
 * Git 发布/服务挂载等）都落一条记录，包含触发人、开始结束时间、耗时与失败原因，
 * 供「日志管理」按项目/操作/状态检索与追溯。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Data
@TableName("ms_server_project_op_log")
public class MsServerProjectOpLog {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 项目标识（发布/软件等非项目操作可为空）
     */
    private Long projectId;

    /**
     * 项目名称（冗余保留，项目删除后日志仍可读）
     */
    private String projectName;

    /**
     * 发布标识
     */
    private Long releaseId;

    /**
     * 操作类型：create/update/delete/start/stop/restart/upgrade/backup/rollback/deploy/install-service 等
     */
    private String operation;

    /**
     * 操作中文名（展示用）
     */
    private String operationLabel;

    /**
     * 状态：RUNNING / SUCCESS / FAILED
     */
    private String status;

    /**
     * 结果说明或失败原因
     */
    private String message;

    /**
     * 异步任务标识（同步操作为空）
     */
    private String taskId;

    /**
     * 触发人（取请求头或后端登录态，取不到为空）
     */
    private String operatorName;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间（RUNNING 时为空）
     */
    private LocalDateTime endTime;

    /**
     * 耗时（毫秒，RUNNING 时为空）
     */
    private Long durationMs;
}
