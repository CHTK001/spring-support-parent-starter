package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* AI 用量同步日志实体。
*
* <p>每次用量同步/聚合执行（定时、手动触发、数据源 parser）都会落一条日志，
* 供前端「用量同步日志」页持久化查询，避免仅依赖内存缓冲重启丢失。</p>
*
* @author CH
* @since 4.0.0.42
 */
@Data
@TableName("sys_ai_usage_sync_log")
@Schema(name = "AI 用量同步日志")
public class SysAiUsageSyncLog implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
    * 日志主键
    */
    @TableId(value = "sys_ai_usage_sync_log_id", type = IdType.AUTO)
    @Schema(description = "日志主键")
    /** 日志标识 */
    private Long id;

    /**
    * 日志级别：信息 / WARN / 错误
    */
    @TableField("sys_ai_usage_sync_log_level")
    @Schema(description = "日志级别：INFO / WARN / ERROR")
    /** 日志级别 */
    private String level;

    /**
    * 日志来源：调度 / manual / parser
    */
    @TableField("sys_ai_usage_sync_log_source")
    @Schema(description = "日志来源：schedule / manual / parser")
    /** 日志来源 */
    private String source;

    /**
    * 日志内容
    */
    @TableField("sys_ai_usage_sync_log_message")
    @Schema(description = "日志内容")
    /** 日志内容 */
    private String message;

    /**
    * 创建时间
    */
    @TableField("create_time")
    @Schema(description = "创建时间")
    /** 创建时间 */
    private LocalDateTime createTime;
}
