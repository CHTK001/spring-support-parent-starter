package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 终端会话实体，对应表 {@code sys_ai_terminal_session}。
 *
 * <p>终端进程运行在服务端内存中（pty4j），本实体只持久化「账号 + 聊天会话」维度的
 * 会话元信息：shell、初始工作目录、运行状态、退出码、尺寸与累计输出字节，
 * 用于页面刷新、服务重启后判断「这个会话曾经有一个终端」并展示历史状态。</p>
 *
 * <p>终端输出正文不落库，按账号写到 {@code <workspaceBaseDir>/<账号>/terminal/*.log}。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Data
@TableName("sys_ai_terminal_session")
public class SysAiTerminalSession {

    /**
     * 主键标识
     */
    @TableId(value = "sys_ai_terminal_session_id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属账号标识
     */
    @TableField("sys_user_id")
    private String userId;

    /**
     * 聊天会话标识
     */
    @TableField("chat_session_id")
    private String chatSessionId;

    /**
     * 终端 shell（pwsh/cmd/bash/sh）
     */
    @TableField("terminal_shell")
    private String shell;

    /**
     * 终端初始工作目录
     */
    @TableField("terminal_cwd")
    private String cwd;

    /**
     * 会话状态：RUNNING 运行中 / EXITED 已退出 / STOPPED 已终止
     */
    @TableField("terminal_status")
    private String status;

    /**
     * 进程退出码，未结束时为空
     */
    @TableField("terminal_exit_code")
    private Integer exitCode;

    /**
     * 终端列数
     */
    @TableField("terminal_cols")
    private Integer columns;

    /**
     * 终端行数
     */
    @TableField("terminal_rows")
    private Integer rows;

    /**
     * 累计输出字节数
     */
    @TableField("terminal_output_bytes")
    private Long outputBytes;

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
