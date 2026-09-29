package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识图谱关系（边），对应表 {@code sys_ai_knowledge_graph_relation}。
 *
 * <p>两端通过实体 uid 引用 {@link SysAiKnowledgeGraphEntity}，不直接存实体名，
 * 以便实体改名后关系仍然有效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_knowledge_graph_relation")
public class SysAiKnowledgeGraphRelation {

    /**
     * 主键
     */
    @TableId(value = "sys_ai_knowledge_graph_relation_id", type = IdType.AUTO)
    private Integer id;

    /**
     * 关系唯一标识（UUID）
     */
    @TableField("sys_ai_knowledge_graph_relation_uid")
    private String uid;

    /**
     * 所属图谱库ID
     */
    @TableField("sys_ai_knowledge_graph_relation_library_id")
    private Integer libraryId;

    /**
     * 源实体 uid
     */
    @TableField("sys_ai_knowledge_graph_relation_source_entity_uid")
    private String sourceEntityUid;

    /**
     * 目标实体 uid
     */
    @TableField("sys_ai_knowledge_graph_relation_target_entity_uid")
    private String targetEntityUid;

    /**
     * 关系类型，如「镇守」「结义」「被封为」
     */
    @TableField("sys_ai_knowledge_graph_relation_type")
    private String type;

    /**
     * 扩展属性（JSON）
     */
    @TableField("sys_ai_knowledge_graph_relation_properties")
    private String properties;

    /**
     * 权重
     */
    @TableField("sys_ai_knowledge_graph_relation_weight")
    private Double weight;

    /**
     * 来源：manual 手工 / llm_extracted 大模型抽取 / imported 导入
     */
    @TableField("sys_ai_knowledge_graph_relation_source")
    private String source;

    /**
     * 状态：1 有效，0 停用
     */
    @TableField("sys_ai_knowledge_graph_relation_status")
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
