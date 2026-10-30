package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟-世界实体，对应表 {@code sys_ai_world}。
 *
 * <p>世界是模拟沙盒的顶层容器：含世界观/时代/天气/时间标签等共同状态，
 * 下面挂场景（scene）、NPC（npc）与事件时间线（event）。按当前登录用户隔离。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_world")
public class SysAiWorld {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 世界名称 */
    @TableField("name")
    private String name;

    /** 世界观设定（背景/时代/氛围） */
    @TableField("description")
    private String description;

    /** 时代背景：现代/古代/未来/奇幻/武侠… */
    @TableField("era")
    private String era;

    /** 当前天气：晴/雨/雪/阴/多云… */
    @TableField("weather")
    private String weather;

    /** 世界当前时间标签（推进时更新） */
    @TableField("time_label")
    private String timeLabel;

    /** 累计推进次数 */
    @TableField("tick_count")
    private Integer tickCount;

    /** 状态：1 运行中 0 暂停 */
    @TableField("status")
    private Integer status;

    /** 归属用户（按账号隔离） */
    @TableField("user_id")
    private String userId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}