package com.chua.starter.spider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程编排实体。
 *
 * <p>对应 {@code spider_flow} 表，保存前端 ReFlow 可视化画布导出的
 * 节点 JSON（节点_json）与连线 JSON（edges_json），一个爬虫定义对应一条流程。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("spider_flow")
@Schema(name = "流程编排")
public class SpiderFlow extends SysBase {

    /**
     * 主键 标识（自增）
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键 ID")
    /**
     * 标识
    */
    private Long id;

    /**
     * 关联爬虫定义 标识
     */
    @TableField("definition_id")
    @Schema(description = "关联爬虫定义 ID")
    /**
     * definitionid
    */
    private Long definitionId;

    /**
     * 流程编码（前端 流编码）
     */
    @TableField("code")
    @Schema(description = "流程编码（前端 flowCode）")
    /**
     * 代码
    */
    private String code;

    /**
     * 是否启用（1 启用 / 0 停用）
     */
    @TableField("enabled")
    @Schema(description = "是否启用")
    /**
     * 是否启用
    */
    private Integer enabled;

    /**
     * 流程名称
     */
    @TableField("name")
    @Schema(description = "流程名称")
    /**
     * 名称
    */
    private String name;

    /**
     * 节点 JSON（re流 导出）
     */
    @TableField("nodes_json")
    @Schema(description = "节点 JSON")
    /**
     * 节点json
    */
    private String nodesJson;

    /**
     * 连线 JSON（re流 导出）
     */
    @TableField("edges_json")
    @Schema(description = "连线 JSON")
    /**
     * edgesjson
    */
    private String edgesJson;

    /**
     * 逻辑删除标记（0 未删除 / 1 已删除）
     */
    @TableField("sys_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    /**
     * SYS删除标记
    */
    private Integer sysDeleted;
}
