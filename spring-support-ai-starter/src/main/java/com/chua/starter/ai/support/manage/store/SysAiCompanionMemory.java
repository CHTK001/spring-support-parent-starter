package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
* 桌面伙伴记忆（Companion 内存）实体。
*
* <p>持久化到 {@code sys_ai_companion_memory} 表。记忆严格按伙伴归属（companion_id），
* 前端契约见 {@code ICompanionMemory}。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName(value = "sys_ai_companion_memory", autoResultMap = true)
public class SysAiCompanionMemory implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
    * 记忆主键（uuidv7 字符串）
     */
    @TableId(value = "sys_ai_companion_memory_id", type = IdType.INPUT)
    private String memoryId; // 内存标识

    /**
    * 所属伙伴 标识（空=legacy legacy row）
     */
    @TableField("sys_ai_companion_memory_companion_id")
    private String companionId; // companionid

    /**
    * 记忆类型：配置文件/preference/知识/episode/任务/情感
     */
    @TableField("sys_ai_companion_memory_kind")
    private String kind; // 种类

    /**
    * 记忆内容
     */
    @TableField("sys_ai_companion_memory_content")
    private String content; // 内容

    /**
    * 标签（JSON 数组）
     */
    @TableField(value = "sys_ai_companion_memory_tags", typeHandler = JacksonTypeHandler.class)
    private List<String> tags; // 标签

    /**
    * 重要性
     */
    @TableField("sys_ai_companion_memory_importance")
    private Double importance; // importance

    /**
    * 强度
     */
    @TableField("sys_ai_companion_memory_strength")
    private Double strength; // strength

    /**
    * 是否置顶
     */
    @TableField("sys_ai_companion_memory_pinned")
    private Boolean pinned; // pinned

    /**
    * 来源
     */
    @TableField("sys_ai_companion_memory_source")
    private String source; // 源

    /**
    * 状态：活跃/archived
     */
    @TableField("sys_ai_companion_memory_status")
    private String status; // 状态

    /**
    * 创建时间（轮次 毫秒）
     */
    @TableField("sys_ai_companion_memory_created_at")
    private Long createdAt; // 创建at

    /**
    * 更新时间（轮次 毫秒）
     */
    @TableField("sys_ai_companion_memory_updated_at")
    private Long updatedAt; // 更新at

    /**
    * 最后强化时间（轮次 毫秒）
     */
    @TableField("sys_ai_companion_memory_last_reinforced_at")
    private Long lastReinforcedAt; // 最后一个reinforcedat
}