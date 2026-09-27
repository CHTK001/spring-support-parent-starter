package com.chua.starter.eduai.model.vo;

import lombok.Data;

/**
 * 采集脚本示例（GROOVY 来源类型的内置样例，供前端一键插入）
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class CollectScriptSampleVO {

    /**
     * 脚本编码（资源文件名，不含扩展名）
     */
    private String code;

    /**
     * 脚本名称（取脚本首部 {@code // 名称:} 注释）
     */
    private String name;

    /**
     * 建议学科
     */
    private String subject;

    /**
     * 脚本说明
     */
    private String description;

    /**
     * 来源配置示例
     */
    private String configHint;

    /**
     * 脚本源码
     */
    private String content;
}
