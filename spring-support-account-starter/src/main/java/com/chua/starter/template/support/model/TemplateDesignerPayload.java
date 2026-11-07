package com.chua.starter.template.support.model;

import com.chua.starter.template.support.entity.TemplateInfo;
import lombok.Data;

/**
 * 设计器数据（info + 模板 JSON 描述 + 操作模式）
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
public class TemplateDesignerPayload {

    /** 模板 ID（新建时为空） */
    private String id;

    /** 模板基础信息 */
    private TemplateInfo info;

    /** 模板设计描述（hiprint JSON） */
    private String content;

    /** 操作模式：design / preview / print */
    private Mode mode;

    /** 操作模式 */
    public enum Mode {
        design, preview, print
    }
}
