package com.chua.starter.template.support.model;

import com.chua.starter.template.support.entity.TemplateInfo;
import lombok.Data;

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
@Data
public class TemplateSaveRequest {

    /** 模板 ID（编辑时必填） */
    private String id;

    /** 模板名称 */
    private String name;

    /** 模板副标题 */
    private String subtitle;

    /** 模板语言：html / vue / jsx / json */
    private String language;

    /** 模板类型：document / sms / email / label / other */
    private String type;

    /** 模板状态：draft / online / offline */
    private String status;

    /** 备注 */
    private String remark;

    /** 模板来源：hiprint / message / paper（创建时默认 hiprint） */
    private String source = "hiprint";

    /** 模板 JSON 描述（设计器产物） */
    private String content;

    /** 创建者 */
    private String creator;

    /** 最后修改者 */
    private String modifier;

    /**
     * 构造模板实体
     *
     * @return 模板信息实体
     */
    public TemplateInfo toEntity(String id) {
        TemplateInfo template = new TemplateInfo();
        template.setTemplateInfoId(id);
        template.setTemplateInfoName(name);
        template.setTemplateInfoSubtitle(subtitle);
        template.setTemplateInfoLanguage(language);
        template.setTemplateInfoType(type);
        template.setTemplateInfoStatus(status);
        template.setTemplateInfoRemark(remark);
        template.setTemplateInfoContent(content);
        template.setTemplateInfoSource(source);
        template.setTemplateInfoDeleted(0);
        template.setTemplateInfoUpdatedAt(LocalDateTime.now());
        return template;
    }
}
