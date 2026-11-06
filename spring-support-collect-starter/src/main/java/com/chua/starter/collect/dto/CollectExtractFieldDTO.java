package com.chua.starter.collect.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 抽取字段声明。
 *
 * <p>抽取节点的每个输出字段一条，描述"从哪个来源、用什么解析器、取哪一段"。</p>
 *
 * @param name         字段名，结果 payload 的 key
 * @param parserType   解析器类型：CSS/REGEX/JSON/TEXT/HTML，留空取节点默认
 * @param expression   表达式：CSS 选择器、正则、JSON 路径或文本模板
 * @param attribute    取值方式：text/attr/html/outer，仅 CSS 生效
 * @param attrName     当 attribute 为 attr 时的属性名
 * @param group        正则捕获组序号，0 表示整段匹配
 * @param defaultValue 未命中时的默认值，支持 ${} 表达式
 * @author CH
 * @since 4.0.0.42
 */
@Schema(description = "抽取字段声明")
public record CollectExtractFieldDTO(
        @Schema(description = "字段名")
        String name,
        @Schema(description = "解析器类型 CSS/REGEX/JSON/TEXT/HTML")
        String parserType,
        @Schema(description = "表达式")
        String expression,
        @Schema(description = "取值方式 text/attr/html/outer")
        String attribute,
        @Schema(description = "属性名")
        String attrName,
        @Schema(description = "正则捕获组序号")
        Integer group,
        @Schema(description = "默认值")
        String defaultValue) {
}
