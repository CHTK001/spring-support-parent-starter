package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识图谱实体（节点），对应表 {@code sys_ai_knowledge_graph_entity}。
 *
 * <p>同一「图谱库 + 名称 + 类型」的实体在库内唯一，跨知识点复用同一个节点，
 * 否则「关羽」会按知识点重复成多个孤立节点，图谱失去连通性。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_knowledge_graph_entity")
public class SysAiKnowledgeGraphEntity {

    /**
     * 主键
     */
    @TableId(value = "sys_ai_knowledge_graph_entity_id", type = IdType.AUTO)
    private Integer id;

    /**
     * 实体唯一标识（UUID），关系表通过它引用实体
     */
    @TableField("sys_ai_knowledge_graph_entity_uid")
    private String uid;

    /**
     * 所属图谱库ID，对应 {@code sys_ai_knowledge_library.sys_ai_knowledge_library_id}
     */
    @TableField("sys_ai_knowledge_graph_library_id")
    private Integer libraryId;

    /**
     * 实体名称，如「关羽」
     */
    @TableField("sys_ai_knowledge_graph_entity_name")
    private String name;

    /**
     * 实体类型，如 person / location / event / organization
     */
    @TableField("sys_ai_knowledge_graph_entity_type")
    private String type;

    /**
     * 扩展属性（JSON）
     */
    @TableField("sys_ai_knowledge_graph_entity_properties")
    private String properties;

    /**
     * 来源：manual 手工 / llm_extracted 大模型抽取 / imported 导入
     */
    @TableField("sys_ai_knowledge_graph_entity_source")
    private String source;

    /**
     * 权重（被提及次数越多权重越高）
     */
    @TableField("sys_ai_knowledge_graph_entity_weight")
    private Double weight;

    /**
     * 图标或 Logo 地址
     */
    @TableField("sys_ai_knowledge_graph_entity_logo")
    private String logo;

    /**
     * 状态：1 有效，0 停用
     */
    @TableField("sys_ai_knowledge_graph_entity_status")
    private Integer status;

    /**
     * 创建人
     */
    @TableField("create_name")
    private String createName;

    /**
     * 创建人ID
     */
    @TableField("create_by")
    private Integer createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.DEFAULT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.DEFAULT)
    private LocalDateTime updateTime;

    /**
     * 更新人
     */
    @TableField("update_name")
    private String updateName;

    /**
     * 更新人ID
     */
    @TableField("update_by")
    private Integer updateBy;
}
