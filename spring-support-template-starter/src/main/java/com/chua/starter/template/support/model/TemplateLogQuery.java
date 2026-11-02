package com.chua.starter.template.support.model;

import lombok.Data;

/**
 * 模板调用日志查询条件
 *
 * @since 4.0.0.80
 * @author CH
 */
@Data
public class TemplateLogQuery {

    /** 页码，从 1 开始 */
    private long page = 1;

    /** 每页条数，最大 200 */
    private long size = 20;

    /** 模板 ID（精确） */
    private String templateId;

    /** 模板名称（模糊） */
    private String templateName;

    /** 操作类型：preview / design / print / sync */
    private String action;

    /** 调用方 IP */
    private String ip;

    /**
     * 规范化分页参数，避免前端传入 0 或超大页
     *
     * @return 当前对象
     */
    public TemplateLogQuery normalize() {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 20;
        }
        if (size > 200) {
            size = 200;
        }
        return this;
    }
}
