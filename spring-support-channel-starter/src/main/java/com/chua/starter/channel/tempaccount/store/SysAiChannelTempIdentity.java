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
 * 临时身份持久化实体。
 *
 * <p>临时身份是组合资源：一个短信渠道（可选）+ 一个邮件渠道（可选），
 * 渠道下各存一份「领取到的身份载荷」（不透明 JSON，短信为号码画像、邮件为邮箱地址）。
 * 落库用于进程重启后恢复身份与最近消息，避免重启即丢。</p>
 *
 * <p>数据库列以表名 {@code sys_ai_channel_temp_identity_} 作为前缀，
 * 实体字段保持简短语义名，通过 {@link TableField} 显式映射。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName(value = "sys_ai_channel_temp_identity", autoResultMap = true)
public class SysAiChannelTempIdentity implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 身份标识
     */
    @TableId(value = "sys_ai_channel_temp_identity_id", type = IdType.INPUT)
    private String identityId; // 身份标识

    /**
     * 短信渠道标识（如 sms-freephonenum），可空
     */
    @TableField("sys_ai_channel_temp_identity_sms_channel")
    private String smsChannel; // 短信渠道

    /**
     * 短信身份载荷 JSON（含 phone / provider），可空
     */
    @TableField(value = "sys_ai_channel_temp_identity_sms_identity", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> smsIdentity; // 短信身份

    /**
     * 邮件渠道标识（如 email-mail-tm），可空
     */
    @TableField("sys_ai_channel_temp_identity_email_channel")
    private String emailChannel; // 邮件渠道

    /**
     * 邮件身份载荷 JSON（含 email / provider），可空
     */
    @TableField(value = "sys_ai_channel_temp_identity_email_identity", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> emailIdentity; // 邮件身份

    /**
     * 归属用户（账号隔离记录用），可空
     */
    @TableField("sys_ai_channel_temp_identity_user_id")
    private String userId; // 归属用户

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_ai_channel_temp_identity_created_at")
    private Long createdAt; // 创建at
}
