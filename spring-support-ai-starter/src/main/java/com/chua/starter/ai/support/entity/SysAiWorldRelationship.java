package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟人生-NPC关系实体，对应表 {@code sys_ai_world_relationship}。
 *
 * <p>NPC 两两之间的好感度（affinity）与关系标签（好友/恋人/同事/敌对…）。
 * 互动后由后端自动增减好感度并落库；同一对角色在同一个世界内唯一。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_world_relationship")
public class SysAiWorldRelationship {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 世界ID */
    @TableField("world_id")
    private Long worldId;

    /** 关系发起方 NPC ID（sys_ai_world_npc.id） */
    @TableField("from_npc_id")
    private Long fromNpcId;

    /** 关系对象 NPC ID（sys_ai_world_npc.id） */
    @TableField("to_npc_id")
    private Long toNpcId;

    /** 好感度 [-100,100]，互动后自动增减 */
    @TableField("affinity")
    private Integer affinity;

    /** 关系标签：好友/恋人/同事/敌对… */
    @TableField("relation_tag")
    private String relationTag;

    /** 备注 */
    @TableField("memo")
    private String memo;

    /** 归属用户 */
    @TableField("user_id")
    private String userId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}