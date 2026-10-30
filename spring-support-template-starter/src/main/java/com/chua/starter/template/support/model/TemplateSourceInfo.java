package com.chua.starter.template.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 来源同步器信息（同步器是否上线 / 库中该来源的模板数）
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
public class TemplateSourceInfo {

    /** 来源标识：message / paper */
    @Schema(description = "来源标识：message / paper")
    private String source;

    /** 库中该来源的模板数 */
    @Schema(description = "库中该来源的模板数")
    private Integer count;

    /** 来源同步器是否上线（1 已接入，0 未接入） */
    @Schema(description = "来源同步器是否上线（1 已接入，0 未接入）")
    private Long registered;

    /**
     * 构造
     *
     * @param source    来源标识
     * @param count     库中该来源的模板数
     * @param registered 来源同步器是否上线
     */
    public TemplateSourceInfo(String source, Integer count, Long registered) {
        this.source = source;
        this.count = count;
        this.registered = registered;
    }
}
