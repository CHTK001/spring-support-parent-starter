package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话执行轨迹实体，对应表 {@code sys_ai_session_trace}。
 *
 * <p>一行 = 一条 {@code AgentTrace} 点事件。当该事件属于某个区间时，
 * {@link #spanId} 指向该区间，且区间结束的那条额外置 {@link #spanEnd}=1
 * 并写入 {@link #spanEndTime}；前端据此把开始/结束配成一条带时长的横条。
 * 没有配对结束事件的保持 {@code spanEnd=0}，前端退化为点事件渲染。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@TableName("sys_ai_session_trace")
public class SysAiSessionTrace {

    /**
     * 轨迹主键标识
     */
    @TableId(value = "sys_ai_session_trace_id", type = IdType.AUTO)
    private Long id;

    /**
     * 轨迹记录UID
     */
    @TableField("sys_ai_session_trace_uid")
    private String uid;

    /**
     * 所属会话ID
     */
    @TableField("sys_ai_session_id")
    private Long sessionId;

    /**
     * 本次运行的请求ID：把一次运行的所有轨迹归为一组
     */
    @TableField("chat_request_id")
    private String chatRequestId;

    /**
     * 轨迹类别
     */
    @TableField("kind")
    private String kind;

    /**
     * Agent 标识
     */
    @TableField("agent_id")
    private String agentId;

    /**
     * 所属区间标识；为空表示这是一条独立的点事件
     */
    @TableField("span_id")
    private String spanId;

    /**
     * 是否区间结束标记：1 是，0 否
     */
    @TableField("span_end")
    private Boolean spanEnd;

    /**
     * 工具名称
     */
    @TableField("tool_name")
    private String toolName;

    /**
     * 轨迹文本
     */
    @TableField("text")
    private String text;

    /**
     * 事件发生时间戳（毫秒）
     */
    @TableField("event_time")
    private Long eventTime;

    /**
     * 区间结束时间戳（毫秒）；0 表示点事件
     */
    @TableField("span_end_time")
    private Long spanEndTime;

    /**
     * 同一次运行内的递增序号：保证同一毫秒内的多条事件顺序稳定
     */
    @TableField("seq")
    private Integer seq;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
