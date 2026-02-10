package com.chua.starter.sync.pojo.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SPI 参数定义
 *
 * @author CH
 * @since 2024/12/19
 */
@Data
@Schema(description = "SPI参数定义")
public class SpiParameter {

    /**
     * 参数名称
     */
    @Schema(description = "参数名称")
    private String name;

    /**
     * 显示名称
     */
    @Schema(description = "显示名称")
    private String label;

    /**
     * 参数描述
     */
    @Schema(description = "参数描述")
    private String description;

    /**
     * 参数类型: string/number/boolean/select/password/textarea/json
     */
    @Schema(description = "参数类型: string/number/boolean/select/password/textarea/json")
    private String type;

    /**
     * 默认值
     */
    @Schema(description = "默认值")
    private Object defaultValue;

    /**
     * 是否必填
     */
    @Schema(description = "是否必填")
    private Boolean required = false;

    /**
     * 是否为敏感信息(如密码)
     */
    @Schema(description = "是否为敏感信息")
    private Boolean sensitive = false;

    /**
     * 占位符文本
     */
    @Schema(description = "占位符文本")
    private String placeholder;

    /**
     * 可选值列表(type=select时使用)
     */
    @Schema(description = "可选值列表")
    private List<Map<String, Object>> options;

    /**
     * 验证规则
     */
    @Schema(description = "验证规则")
    private String validation;

    /**
     * 参数分组
     */
    @Schema(description = "参数分组")
    private String group;

    /**
     * 排序
     */
    @Schema(description = "排序")
    private Integer order;

    /**
     * 依赖条件(当某个参数为特定值时显示)
     */
    @Schema(description = "依赖条件")
    private Map<String, Object> dependsOn;

    /**
     * 最小值(number类型)
     */
    @Schema(description = "最小值")
    private Number min;

    /**
     * 最大值(number类型)
     */
    @Schema(description = "最大值")
    private Number max;

    /**
     * 步长(number/slider类型)
     */
    @Schema(description = "步长")
    private Number step;

    /**
     * 精度/小数位数(number类型)
     */
    @Schema(description = "精度")
    private Integer precision;

    /**
     * 验证正则表达式
     */
    @Schema(description = "验证正则")
    private String pattern;

    /**
     * 验证失败提示信息
     */
    @Schema(description = "验证提示")
    private String patternMessage;

    /**
     * 开关激活时文本
     */
    @Schema(description = "激活文本")
    private String activeText;

    /**
     * 开关未激活时文本
     */
    @Schema(description = "未激活文本")
    private String inactiveText;

    /**
     * 是否显示输入框(slider类型)
     */
    @Schema(description = "是否显示输入框")
    private Boolean showInput;
}
