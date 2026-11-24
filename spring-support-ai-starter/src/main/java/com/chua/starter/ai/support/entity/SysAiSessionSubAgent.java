package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话子代理运行记录实体，对应表 {@code sys_ai_session_sub_agent}。
 *
 * <p>持久化「主会话 ↔ 子代理」的绑定与调用历史：主会话下每次 spawn / 手动 invoke
 * 一个子代理，都在本表落一行。前端据此在刷新 / 跨标签页后恢复子代理 Tab
 * （{@code GET /api/sessions/{id}/sub-sessions}），侧边栏据此展示「子会话 / 智能体」；
 * 生命周期变更（创建 / 关闭 / 状态流转）经 {@code AiSessionEventBroadcaster} 广播
 * {@code sub_agent_create} / {@code sub_agent_closed} / {@code sub_agent_status}。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
@TableName("sys_ai_session_sub_agent")
public class SysAiSessionSubAgent {

    /**
     * 子会话记录主键（即前端契约里的 subSessionId）
     */
    @TableId(value = "sys_ai_sub_session_id", type = IdType.AUTO)
    private Long id;

    /**
     * 主会话标识，对应 {@code sys_ai_session} 主键
     */
    @TableField("sys_ai_session_id")
    private Long sessionId;

    /**
     * 子 Agent 标识，对应 {@code sys_ai_agent} 主键
     */
    @TableField("agent_id")
    private String agentId;

    /**
     * 子 Agent 名称快照（建行时取，Agent 后改名不影响历史记录展示）
     */
    @TableField("agent_name")
    private String agentName;

    /**
     * 所属主 Agent 标识快照
     */
    @TableField("master_agent_id")
    private String masterAgentId;

    /**
     * 状态：RUNNING（运行中）/ COMPLETED（完成）/ FAILED（失败）/ CLOSED（关闭）
     */
    @TableField("status")
    private String status;

    /**
     * 调用输入
     */
    @TableField("input")
    private String input;

    /**
     * 调用输出（成功时回填）
     */
    @TableField("output")
    private String output;

    /**
     * 失败原因（失败 / 关闭原因说明）
     */
    @TableField("error")
    private String error;

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
