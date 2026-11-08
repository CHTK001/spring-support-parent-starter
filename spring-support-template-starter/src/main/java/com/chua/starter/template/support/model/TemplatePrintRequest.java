package com.chua.starter.template.support.model;

import lombok.Data;

/**
 * 打印请求
 *
 * <p>由前端在用户确认打印后发起：{@code printer} 为打印机设备名，
 * {@code copies} 为份数，{@code params} 为模板变量（透传打印引擎）。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Data
public class TemplatePrintRequest {

    /** 打印机设备名 */
    private String printer;

    /** 份数，默认 1 */
    private Integer copies = 1;

    /** 模板变量（透传给打印引擎） */
    private java.util.Map<String, Object> params;
}
