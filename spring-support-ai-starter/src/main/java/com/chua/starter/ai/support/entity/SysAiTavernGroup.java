package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 酒馆群实体，对应表 {@code sys_ai_tavern_group}。
 *
 * <p>建群者即群主（真人，登录用户本人）；群成员是拉进来的 AI 角色。
 * 未 @ 时：开了自动路由就路由挑成员，否则回退到 {@code defaultMemberId}。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName("sys_ai_tavern_group")
public class SysAiTavernGroup {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 群名称 */
    @TableField("name")
    private String name;

    /** 群头像 */
    @TableField("avatar")
    private String avatar;

    /** 默认回复成员ID（sys_ai_tavern_member.id） */
    @TableField("default_member_id")
    private Long defaultMemberId;

    /** 未@时是否自动路由挑选回复者 0否1是 */
    @TableField("auto_route")
    private Integer autoRoute;

    /** 群主/归属用户（登录用户名） */
    @TableField("user_id")
    private String userId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
