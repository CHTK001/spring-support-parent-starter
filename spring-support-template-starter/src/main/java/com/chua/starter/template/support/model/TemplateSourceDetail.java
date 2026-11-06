package com.chua.starter.template.support.model;

import java.util.List;

/**
 * 模板详情（按模板 ID 取信息）
 *
 * <p>统一模板库里既有本系统设计的自定义模板，也有从三方同步进来的固定模板。
 * 前者内容就在库内，后者内容归三方，本系统只保存「三方模板 ID + 基础信息」。
 * 本模型把两种形态收敛成同一份「拿到模板之后需要什么」的答案：</p>
 *
 * <ul>
 *   <li>自定义模板：{@code content} 是设计器产物，{@code channel} / {@code sourceCode} 为空；</li>
 *   <li>固定模板：{@code sourceCode} 是三方模板 ID，{@code providerId} / {@code providerName} 指出
 *       归属账号（短信 / 邮件账号），{@code channel} 是推送渠道，{@code params} 是内容里的占位符。</li>
 * </ul>
 *
 * <p>字段形态对齐 {@code com.chua.common.support.task.message.MessageRequest} 发送一条模板消息
 * 所需的东西（templateId / contentType / content / templateParams），调用方不必再去理解
 * 消息模块的内部结构。</p>
 *
 * @since 4.0.0.81
 * @author CH
 */
public record TemplateSourceDetail(

    /** 统一模板库主键（template_info_id） */
    String id,

    /** 模板名称 */
    String name,

    /** 来源：hiprint（自定义）/ message / paper（后两者为固定模板） */
    String source,

    /**
     * 是否固定模板
     *
     * <p>{@code true} = 三方同步，内容不可改；{@code false} = 本系统自建，可设计可编辑。</p>
     */
    boolean fixed,

    /** 来源行 ID（如短信模板的 sys_message_template_id） */
    String sourceId,

    /** 三方模板 ID（固定模板才有；对齐 MessageRequest.templateId） */
    String sourceCode,

    /** 归属账号 / 提供商 ID（消息模板为 sys_message_provider_id） */
    String providerId,

    /** 归属账号 / 提供商名称 */
    String providerName,

    /** 推送渠道标识（MessagePush.create 用，如 aliyun / email / sms） */
    String channel,

    /** 内容类型（对齐 MessageRequest.contentType，如 sms / email / html） */
    String contentType,

    /** 模板内容（自定义模板为设计器 JSON；固定模板为来源模块留存的内容） */
    String content,

    /** 内容里的占位符参数名（对齐 MessageRequest.templateParams 的键） */
    List<String> params,

    /** 备注 / 说明 */
    String remark) {
}
