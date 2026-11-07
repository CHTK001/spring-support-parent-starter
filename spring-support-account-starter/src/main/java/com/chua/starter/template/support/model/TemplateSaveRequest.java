package com.chua.starter.template.support.model;

import com.chua.starter.template.support.entity.TemplateInfo;

import java.time.LocalDateTime;

/**
 * 模板新增/编辑请求
 *
 * <p>包含模板基础信息与设计器产物（content = 模板 JSON 描述），
 * 由 {@link com.chua.starter.template.support.service.TemplateService}
 * 转为 {@link com.chua.starter.template.support.entity.TemplateInfo} 落库。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
public record TemplateSaveRequest(

    /** 模板 ID（编辑时必填） */
    String id,

    /** 模板名称 */
    String name,

    /** 模板副标题 */
    String subtitle,

    /** 模板语言：html / vue / jsx / json */
    String language,

    /** 模板类型：document / sms / email / label / other */
    String type,

    /** 模板状态：draft / online / offline */
    String status,

    /** 备注 */
    String remark,

    /** 模板来源：hiprint / message / paper（创建时默认 hiprint） */
    String source,

    /** 模板 JSON 描述（设计器产物） */
    String content,

    /** 创建者 */
    String creator,

    /** 最后修改者 */
    String modifier) {

    /**
     * 来源默认值：创建时未指定来源则回落为 hiprint
     */
    public TemplateSaveRequest {
        if (source == null) {
            source = "hiprint";
        }
    }

    /**
     * 构造模板实体
     *
     * @return 模板信息实体
     */
    public TemplateInfo toEntity(String id) {
        TemplateInfo template = new TemplateInfo();
        template.setTemplateInfoId(id);
        template.setTemplateInfoName(name());
        template.setTemplateInfoSubtitle(subtitle());
        template.setTemplateInfoLanguage(language());
        template.setTemplateInfoType(type());
        template.setTemplateInfoStatus(status());
        template.setTemplateInfoRemark(remark());
        template.setTemplateInfoContent(content());
        template.setTemplateInfoSource(source());
        template.setTemplateInfoDeleted(0);
        template.setTemplateInfoUpdatedAt(LocalDateTime.now());
        return template;
    }
}
