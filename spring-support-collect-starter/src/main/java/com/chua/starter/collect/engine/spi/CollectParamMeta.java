package com.chua.starter.collect.engine.spi;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点参数元数据。
 *
 * <p>可视化设计器的属性面板完全由这份元数据渲染，新增节点类型不需要改前端代码。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "节点参数元数据")
public class CollectParamMeta {

    /**
     * 参数名，写入节点 params 的 name。
     */
    @Schema(description = "参数名")
    private String name;

    /**
     * 界面显示标题。
     */
    @Schema(description = "显示标题")
    private String label;

    /**
     * 控件类型。
     *
     * <p>基础类型：{@code text} {@code textarea} {@code number} {@code switch} {@code select}
     * {@code pairs} {@code fields} {@code flow}。
     * 数据源联动类型：{@code datasource} 数据源下拉，候选项由数据源选项接口给出；
     * {@code table} 表下拉，候选项由 {@link #dependsOn} 指向的数据源实时读出；
     * {@code mapping} 字段映射表，左列取自 {@link #columnsFrom} 指向的表，右列填表达式。
     * 三者都只换控件、不换存储格式，已存流程无需迁移。</p>
     */
    @Schema(description = "控件类型 text/textarea/number/switch/select/pairs/fields/flow/datasource/table/mapping")
    private String type = "text";

    /**
     * 级联来源参数名。
     *
     * <p>{@code type=table} 时有效，声明候选项要从哪个参数对应的数据源里读。
     * 链路是 {@code table.dependsOn=datasource}；{@code mapping} 再经
     * {@link #columnsFrom} 指向 table，形成两跳级联。</p>
     */
    @Schema(description = "级联来源参数名")
    private String dependsOn;

    /**
     * 取列来源参数名，{@code type=mapping} 时有效，声明左列候选项取自哪个表参数。
     */
    @Schema(description = "取列来源参数名")
    private String columnsFrom;

    /**
     * 下拉候选项，type=select 时有效。
     */
    @Schema(description = "下拉候选项")
    private List<String> options = new ArrayList<>();

    /**
     * 默认值。
     */
    @Schema(description = "默认值")
    private String defaultValue;

    /**
     * 是否必填。
     */
    @Schema(description = "是否必填")
    private boolean required;

    /**
     * 填写提示，说明表达式写法。
     */
    @Schema(description = "填写提示")
    private String tip;

    /**
     * 构造参数元数据。
     *
     * @param name  参数名
     * @param label 显示标题
     * @param type  控件类型
     * @return 参数元数据
     */
    public static CollectParamMeta of(String name, String label, String type) {
        CollectParamMeta meta = new CollectParamMeta();
        meta.setName(name);
        meta.setLabel(label);
        meta.setType(type);
        return meta;
    }

    /**
     * 构造下拉参数元数据。
     *
     * @param name    参数名
     * @param label   显示标题
     * @param options 候选项
     * @return 参数元数据
     */
    public static CollectParamMeta select(String name, String label, String... options) {
        CollectParamMeta meta = of(name, label, "select");
        meta.setOptions(List.of(options));
        return meta;
    }
}
