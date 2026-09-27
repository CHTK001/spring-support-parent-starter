package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 酒馆群成员实体，对应表 {@code sys_ai_tavern_member}。
 *
 * <p>一个群挂多个 AI 角色成员。成员可单独覆盖模型（model_override 选填），
 * 模型优先级：成员 model_override &gt; 角色 model &gt; 全局默认。</p>
 *
 * @author CH
 */
@Data
@TableName("sys_ai_tavern_member")
public class SysAiTavernMember {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 群ID */
    @TableField("group_id")
    private Long groupId;

    /** 角色ID（sys_ai_role 主键） */
    @TableField("role_id")
    private Integer roleId;

    /** 成员模型覆盖（选填） */
    @TableField("model_override")
    private String modelOverride;

    /** 是否启用随机发言（参考连接器，默认关闭） */
    @TableField("random_reply_enabled")
    private Boolean randomReplyEnabled;

    /** 随机发言命中概率百分比，取值 [0,100]，0=从不 */
    @TableField("random_probability")
    private Double randomProbability;

    /** 喂给该成员的上下文消息条数 */
    @TableField("random_context_count")
    private Integer randomContextCount;

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
