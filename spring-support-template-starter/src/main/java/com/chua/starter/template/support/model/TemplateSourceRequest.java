package com.chua.starter.template.support.model;

import com.chua.starter.template.support.entity.TemplateInfo;
import lombok.Data;

/**
 * 接入来源模板的注册请求
 *
 * <p>由 external module（消息模块 / 教育模块）调 tmpl_service。registerTemplate() 使用，
 * 把来源模块的模板行“复制”到统一模板库（template_info）中。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
public class TemplateSourceRequest {

    /** 来源：hiprint / message / paper */
    private String source;

    /** 来源行 ID（短信/组卷模板的主键 ID，String） */
    private String sourceId;

    /** 来源提供方（消息提供商 ID / 组卷归属用户 ID） */
    private String providerId;

    /** 模板名称 */
    private String name;

    /** 模板副标题 / 说明 */
    private String subtitle;

    /** 模板语言：html / vue / jsx / json */
    private String language;

    /** 模板类型：document / sms / email / label / other */
    private String type;

    /** 模板状态：draft / online / offline */
    private String status;

    /** 备注 */
    private String remark;

    /** 模板 JSON 描述（设计器产物 / 原来源内容） */
    private String content;

    /**
     * 构造模板实体
     *
     * @param creator 创建者
     * @return 模板信息实体
     */
    public TemplateInfo toEntity(String creator) {
        TemplateInfo template = new TemplateInfo();
        template.setTemplateInfoName(name);
        template.setTemplateInfoSubtitle(subtitle);
        template.setTemplateInfoLanguage(language);
        template.setTemplateInfoType(type);
        template.setTemplateInfoStatus(status);
        template.setTemplateInfoRemark(remark);
        template.setTemplateInfoContent(content);
        template.setTemplateInfoSource(source);
        template.setTemplateInfoSourceId(sourceId);
        template.setTemplateInfoProvider(providerId);
        template.setTemplateInfoCreator(creator);
        template.setTemplateInfoModifier(creator);
        template.setTemplateInfoDeleted(0);
        return template;
    }
}
