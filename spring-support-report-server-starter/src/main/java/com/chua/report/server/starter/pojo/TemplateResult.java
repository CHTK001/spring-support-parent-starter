package com.chua.report.server.starter.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板结果
 *
 * @author CH
 * @since 2023/09/25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TemplateResult {


    /**
     * 名称
     */
    private String name;
    /**
     * 路径
     */
    private String path;
    /**
     * 类型
     */
    private String type;

    /**
     * 内容
     */
    private String content;
}
