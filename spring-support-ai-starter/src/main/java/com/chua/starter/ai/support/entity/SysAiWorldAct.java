package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟-剧本幕次实体，对应表 {@code sys_ai_world_act}。
 *
 * <p>幕次是剧本模拟的导演单元：一世界多幕（actNo 递增），每幕经历
 * <b>编剧书写 → 道具服装就绪 → 电影/话剧表演</b> 三个阶段（stage）。
 * 完整剧本（场景/台词/NPC 动画/道具/过场/媒体句柄）不入库，
 * 写服务器目录 {@code {story_base_path}/{worldId}/acts/act{N}.json}；
 * 本表只保留可视化的元数据（标题/状态/进度），与表 {@code sys_ai_world} 隔离。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@TableName("sys_ai_world_act")
public class SysAiWorldAct {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 世界ID */
    @TableField("world_id")
    private Long worldId;

    /** 幕次编号（从 1 开始连续递增） */
    @TableField("act_no")
    private Integer actNo;

    /** 幕标题 */
    @TableField("title")
    private String title;

    /** 本幕概要（演员表/场景/悬念） */
    @TableField("description")
    private String description;

    /**
     * 状态：writing（编剧中）/ props（道具就绪中）/ playing（演出中）/
     * replay（重播中）/ finished（已完结）/ aborted（已废弃）
     */
    @TableField("status")
    private String status;

    /**
     * 进度阶段：writing → props → playing → replay
     */
    @TableField("stage")
    private String stage;

    /** 演出进度（当前场景编号，0 = 未开始） */
    @TableField("progress")
    private Integer progress;

    /** 是否已大结局 */
    @TableField("ended")
    private Integer ended;

    /** 创建时间 */
    @TableField("created_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("updated_time")
    private LocalDateTime updateTime;
}
