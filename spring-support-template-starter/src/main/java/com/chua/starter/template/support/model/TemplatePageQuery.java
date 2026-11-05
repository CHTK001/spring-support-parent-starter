// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.template.support.model;

import lombok.Data;

/**
 * 模板分页查询条件
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
public class TemplatePageQuery {

    /** 页码，从 1 开始 */
    private long page = 1;

    /** 每页条数，最大 200 */
    private long size = 20;

    /** 关键字：名称/副标题/备注 */
    private String keyword;

    /** 模板语言：html / vue / jsx / json */
    private String language;

    /** 模板类型：document / sms / email / label / other */
    private String type;

    /** 模板状态：draft / online / offline */
    private String status;

    /** 排序字段：updatedAt/createdAt/name */
    private String sortBy = "updatedAt";

    /** 排序方向：asc/desc */
    private String sortOrder = "desc";

    /**
     * 模板来源：hiprint / message / paper
     */
    private String source;

    /**
     * 规范化分页参数，避免前端传入 0 或超大页
     *
     * @return 当前对象
     */
    public TemplatePageQuery normalize() {
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