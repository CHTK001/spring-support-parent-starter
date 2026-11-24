package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话待办（镜像 opencode {@code todowrite} 工具写入的待办列表）。
 *
 * <p>Agent 运行时（opencode）在执行中调用 {@code todowrite} 时，入参是**全量**待办
 * 列表（整表替换语义）；本表按会话镜像该列表，供前端「待办」面板在刷新 /
 * 跨标签页后恢复，并经会话事件总线广播 {@code todo_updated} 实时同步。
 * 原始待办状态仍以 Agent 运行时为准，本表是只读镜像（前端无写入口）。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
@TableName("sys_ai_session_todo")
public class SysAiSessionTodo {

    /**
     * 待办记录主键
     */
    @TableId(value = "sys_ai_session_todo_id", type = IdType.AUTO)
    private Long id;

    /**
     * 主会话标识，对应 {@code sys_ai_session} 主键
     */
    @TableField("sys_ai_session_id")
    private Long sessionId;

    /**
     * Agent 运行时（opencode）待办项标识，可为空（运行时未给 id 时按列表顺序展示）
     */
    @TableField("todo_id")
    private String todoId;

    /**
     * 待办内容
     */
    @TableField("content")
    private String content;

    /**
     * 状态：pending（待办）/ in_progress（进行中）/ completed（已完成）/ cancelled（已取消）
     */
    @TableField("status")
    private String status;

    /**
     * 优先级：high / medium / low（运行时未给时为空）
     */
    @TableField("priority")
    private String priority;

    /**
     * 排序号（Agent 下发的列表顺序，从 0 起）
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 归属用户标识（与主会话归属人一致，用于鉴权与隔离）
     */
    @TableField("sys_user_id")
    private String sysUserId;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
