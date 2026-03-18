package com.chua.spring.support.email.entity;

import lombok.Data;

import java.util.Date;
import java.util.Map;

/**
 * 邮件模板实体
 * 
 * @author CH
 */
@Data
public class EmailTemplate {

    /**
     * 模板ID
     */
    private String id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板描述
     */
    private String description;

    /**
     * 模板主题
     */
    private String subject;

    /**
     * 模板内容（支持变量占位符）
     */
    private String content;

    /**
     * 是否HTML格式
     */
    private Boolean isHtml;

    /**
     * 模板变量说明
     */
    private Map<String, String> variables;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
