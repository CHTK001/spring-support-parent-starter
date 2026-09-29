package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识图谱实体/关系与「知识点」的关联，对应表 {@code sys_ai_knowledge_graph_ref}。
 *
 * <p>实体与关系在库内唯一，一个知识点可以关联多个实体和关系，一个实体也可以被
 * 多个知识点提及，因此两者是多对多，用本表承载。删除知识点时按
 * {@code knowledge_base_id + doc_path} 清理关联，再回收不再被任何知识点引用的
 * 孤立实体。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_knowledge_graph_ref")
public class SysAiKnowledgeGraphRef {

    /**
     * 主键
     */
    @TableId(value = "sys_ai_knowledge_graph_ref_id", type = IdType.AUTO)
    private Integer id;

    /**
     * 关联记录唯一标识（UUID）
     */
    @TableField("sys_ai_knowledge_graph_ref_uid")
    private String uid;

    /**
     * 所属图谱库ID
     */
    @TableField("sys_ai_knowledge_graph_library_id")
    private Integer libraryId;

    /**
     * 关联目标类型：entity 实体 / relation 关系
     */
    @TableField("sys_ai_knowledge_graph_ref_target_type")
    private String targetType;

    /**
     * 实体 uid 或关系 uid
     */
    @TableField("sys_ai_knowledge_graph_ref_target_uid")
    private String targetUid;

    /**
     * RAG 知识库ID（knowledge_base_id）
     */
    @TableField("sys_ai_knowledge_graph_ref_knowledge_base_id")
    private String knowledgeBaseId;

    /**
     * 知识点在知识库内的相对路径
     */
    @TableField("sys_ai_knowledge_graph_ref_doc_path")
    private String docPath;

    /**
     * 状态：1 有效，0 失效
     */
    @TableField("sys_ai_knowledge_graph_ref_status")
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
