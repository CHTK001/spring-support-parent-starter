package com.chua.starter.collect.engine.spi;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点类型元数据。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "节点类型元数据")
public class CollectShapeMeta {

    /**
     * 节点类型标识，与执行器 supportShape 一致。
     */
    @Schema(description = "节点类型标识")
    private String shape;

    /**
     * 界面显示名称。
     */
    @Schema(description = "界面显示名称")
    private String label;

    /**
     * 分组：来源、解析、控制、输出。
     */
    @Schema(description = "节点分组")
    private String group;

    /**
     * 图标标识，前端按分组着色。
     */
    @Schema(description = "图标")
    private String icon;

    /**
     * 功能说明。
     */
    @Schema(description = "功能说明")
    private String description;

    /**
     * 出参变量名说明，例如 resp 表示结果写入 ${resp}。
     */
    @Schema(description = "出参变量")
    private String outputVariable;

    /**
     * 是否展示抽取字段编辑表格。
     */
    @Schema(description = "是否展示抽取字段表格")
    private boolean fieldEditor;

    /**
     * 已下线节点：不出现在调色板里，但存量流程仍能渲染与编辑。
     *
     * <p>不能直接把它从元数据列表里删掉——存量流程的 DSL 里还写着这个 shape，
     * 删了以后那些流程打开会认不出节点类型，节点变成一张没有图标没有名字的空卡片。
     * 所以是「不新增」而不是「不能存在」：调色板里不再给入口，老流程照跑。</p>
     */
    @Schema(description = "是否已下线（不在调色板展示）")
    private boolean hidden;

    /**
     * 参数元数据列表。
     */
    @Schema(description = "参数元数据列表")
    private List<CollectParamMeta> params = new ArrayList<>();
}
