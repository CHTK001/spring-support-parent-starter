package com.chua.starter.channel.tempaccount.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 临时身份收到的消息持久化实体（短信 / 邮件增量）。
 *
 * <p>按 {@code (identity_id, fingerprint)} 建唯一索引做去重：轮询每次抓取到的
 * 全量消息与已落库指纹比对，只有新指纹才写入，天然避免重复。</p>
 *
 * <p>数据库列以表名 {@code sys_ai_channel_temp_message_} 作为前缀，
 * 实体字段保持简短语义名，通过 {@link TableField} 显式映射。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName(value = "sys_ai_channel_temp_message", autoResultMap = true)
public class SysAiChannelTempMessage implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 消息标识
     */
    @TableId(value = "sys_ai_channel_temp_message_id", type = IdType.INPUT)
    private String messageId; // 消息标识

    /**
     * 所属身份标识
     */
    @TableField("sys_ai_channel_temp_message_identity_id")
    private String identityId; // 身份标识

    /**
     * 消息种类（SMS / EMAIL）
     */
    @TableField("sys_ai_channel_temp_message_kind")
    private String kind; // 种类

    /**
     * 去重指纹
     */
    @TableField("sys_ai_channel_temp_message_fingerprint")
    private String fingerprint; // 指纹

    /**
     * 消息载荷 JSON
     */
    @TableField(value = "sys_ai_channel_temp_message_payload", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload; // 载荷

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_ai_channel_temp_message_created_at")
    private Long createdAt; // 创建at
}
