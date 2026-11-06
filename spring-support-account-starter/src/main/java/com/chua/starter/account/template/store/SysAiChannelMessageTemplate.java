package com.chua.starter.account.template.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 短信 / 邮件消息模板持久化实体。
 *
 * <p>模板用于把渠道收到的原始消息渲染成渠道管理页可读的展示文本（{@code display}），
 * 并支持在页面里自定义与选择：</p>
 * <ul>
 *   <li>{@code kind}：模板种类（{@code SMS} / {@code EMAIL}），一个种类可有多套模板；</li>
 *   <li>{@code code}：稳定编码，同一种类内唯一，便于脚本与前端引用；</li>
 *   <li>{@code content} / {@code subject}：正文与邮件主题，变量占位写作 {@code {{变量名}}}；</li>
 *   <li>{@code variables}：模板声明的变量名清单，供前端提示与缺省校验；</li>
 *   <li>{@code defaultTemplate}：同一种类的默认模板标记（有且仅有一条生效）。</li>
 * </ul>
 *
 * <p>列名统一以表名 {@code sys_ai_channel_message_template_} 作前缀，与渠道模块
 * 其它实体保持一致。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName(value = "sys_ai_channel_message_template", autoResultMap = true)
public class SysAiChannelMessageTemplate implements Serializable {

    /**
     * 串行版本
     */
    private static final long serialVersionUID = 1L;

    /**
     * 模板标识（UUID，新增时后端生成）
     */
    @TableId(value = "sys_ai_channel_message_template_id", type = IdType.INPUT)
    /** 字段 */
    private String templateId;

    /**
     * 模板种类（SMS / EMAIL）
     */
    @TableField("sys_ai_channel_message_template_kind")
    /** templateKind */
    private String templateKind;

    /**
     * 模板名称（展示用）
     */
    @TableField("sys_ai_channel_message_template_name")
    /** template名称 */
    private String templateName;

    /**
     * 模板编码（同一种类内唯一）
     */
    @TableField("sys_ai_channel_message_template_code")
    /** template编码 */
    private String templateCode;

    /**
     * 邮件主题（仅 EMAIL 使用，可含变量）
     */
    @TableField("sys_ai_channel_message_template_subject")
    /** templateSubject */
    private String templateSubject;

    /**
     * 模板正文（可含 {{变量}} 占位）
     */
    @TableField("sys_ai_channel_message_template_content")
    /** template内容 */
    private String templateContent;

    /**
     * 模板声明的变量名清单（JSON 数组）
     */
    @TableField(value = "sys_ai_channel_message_template_variables", typeHandler = JacksonTypeHandler.class)
    private List<String> templateVariables;

    /**
     * 是否为该种类的默认模板
     */
    @TableField("sys_ai_channel_message_template_default")
    private Boolean templateDefault;

    /**
     * 是否启用
     */
    @TableField("sys_ai_channel_message_template_enabled")
    private Boolean templateEnabled;

    /**
     * 排序号（升序展示）
     */
    @TableField("sys_ai_channel_message_template_sort_order")
    private Integer templateSortOrder;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_ai_channel_message_template_created_at")
    private Long templateCreatedAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_ai_channel_message_template_updated_at")
    private Long templateUpdatedAt;
}
