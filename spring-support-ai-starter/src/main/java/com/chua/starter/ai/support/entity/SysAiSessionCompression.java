package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话压缩上下文实体，对应表 {@code sys_ai_session_compression}。
 *
 * <p>保存会话历史被压缩后的摘要上下文。聊天时优先使用本表上下文，未命中时回退历史对话记录。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_session_compression")
public class SysAiSessionCompression {

    /**
     * 压缩上下文主键 标识
     */
    @TableId(value = "sys_ai_session_compression_id", type = IdType.AUTO)
    /** SYSAI会话compressionid */
    private Integer id;

    /**
    * 会话 标识
    */
    @TableField("sys_ai_session_id")
    /** SYSAI会话标识 */
    private Long sysAiSessionId;

    /**
    * 厂家编码
    */
    @TableField("sys_ai_manufacturer_code")
    /** sysaimanufacturer代码 */
    private String sysAiManufacturerCode;

    /**
    * 模型编码
    */
    @TableField("sys_ai_module_code")
    /** sysaimodule代码 */
    private String sysAiModuleCode;

    /**
    * 压缩时消息数量
    */
    @TableField("sys_ai_compression_message_count")
    /** sysaicompression消息数量 */
    private Long sysAiCompressionMessageCount;

    /**
    * 压缩基准消息 标识
    */
    @TableField("sys_ai_compression_from_message_id")
    /** sysaicompressionfrom消息标识 */
    private Integer sysAiCompressionFromMessageId;

    /**
    * 压缩后的上下文（摘要文本或 JSON）
    */
    @TableField("sys_ai_compression_context")
    /** sysaicompression上下文 */
    private String sysAiCompressionContext;

    /**
    * 创建时间
    */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    /** 创建时间 */
    private LocalDateTime createTime;

    /**
    * 更新时间
    */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    /** 更新时间 */
    private LocalDateTime updateTime;
}
