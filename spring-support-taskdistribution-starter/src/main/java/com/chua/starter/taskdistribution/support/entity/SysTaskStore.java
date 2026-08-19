package com.chua.starter.taskdistribution.support.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务存储实体。
 *
 * <p>将任务内容和结果以 JSON 形式存储，避免字段映射的复杂性。
 * 以 taskId 为主键，支持 upsert 语义。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_task_store")
public class SysTaskStore {

    /**
     * 任务 ID（主键）
     */
    @TableId
    /** 任务ID */
    private String taskId;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 任务状态
     */
    private String taskStatus;

    /**
     * 任务 JSON（含 payload）
     */
    private String taskJson;

    /**
     * 结果 JSON
     */
    private String resultJson;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}