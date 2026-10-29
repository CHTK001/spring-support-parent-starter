package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟人生-世界场景实体，对应表 {@code sys_ai_world_scene}。
 *
 * <p>场景是世界内的地点（咖啡厅/街道/办公室…），NPC 分布在各个场景；
 * 「在哪个场景说话」决定哪些 NPC 在场、以什么环境上下文应答。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_world_scene")
public class SysAiWorldScene {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 世界ID */
    @TableField("world_id")
    private Long worldId;

    /** 场景名称 */
    @TableField("name")
    private String name;

    /** 场景描述（注入对话上下文） */
    @TableField("description")
    private String description;

    /** 排序 */
    @TableField("sort")
    private Integer sort;

    /** 归属用户 */
    @TableField("user_id")
    private String userId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}