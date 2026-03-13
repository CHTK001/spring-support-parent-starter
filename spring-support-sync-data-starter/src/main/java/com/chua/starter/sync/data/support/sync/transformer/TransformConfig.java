package com.chua.starter.sync.data.support.sync.transformer;

import lombok.Data;

import java.util.Map;

/**
 * 转换配置类
 *
 * @author System
 * @since 2026/03/09
 */
@Data
public class TransformConfig {

    /**
     * 转换类型
     */
    private String transformType;

    /**
     * 类型（用于判断转换器类型）
     */
    private String type;

    /**
     * 字段映射配置
     */
    private Map<String, String> fieldMappings;

    /**
     * 过滤规则
     */
    private FilterRule filterRule;

    /**
     * 脱敏规则
     */
    private Map<String, MaskingRule> maskingRules;

    /**
     * 脚本类型（groovy/javascript）
     */
    private String scriptType;

    /**
     * 脚本内容
     */
    private String script;

    /**
     * 过滤错误时是否保留数据
     */
    private Boolean keepOnFilterError;

    /**
     * 是否保留未映射的字段
     */
    private Boolean keepUnmappedFields;

    /**
     * 脚本错误时是否保留数据
     */
    private Boolean keepOnScriptError;

    /**
     * 过滤规则
     */
    @Data
    public static class FilterRule {
        private FilterType type;
        private String expression;
        
        /**
         * 字段名
         */
        private String field;
        
        /**
         * 字段值
         */
        private String value;
    }

    /**
     * 过滤类型
     */
    public enum FilterType {
        EXPRESSION,
        SCRIPT
    }

    /**
     * 脱敏规则
     */
    @Data
    public static class MaskingRule {
        private MaskingType type;
        private String pattern;
        private String replacement;
    }

    /**
     * 脱敏类型
     */
    public enum MaskingType {
        PHONE,
        EMAIL,
        ID_CARD,
        CUSTOM
    }
}
