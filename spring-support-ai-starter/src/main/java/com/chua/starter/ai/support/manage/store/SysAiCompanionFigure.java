package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 桌面伙伴形象（Companion Figure）实体 —— 可复用的共享形象库。
 *
 * <p>持久化到 {@code sys_ai_companion_figure} 表，与伙伴解耦（共享库），
 * 前端契约见 {@code IFigureMeta}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_companion_figure", autoResultMap = true)
public class SysAiCompanionFigure implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 形象主键（uuidv7 字符串）
     */
    @TableId(value = "sys_ai_companion_figure_id", type = IdType.INPUT)
    private String figureId; // figureid

    /**
     * 形象名
     */
    @TableField("sys_ai_companion_figure_name")
    private String name; // 名称

    /**
     * 宽高比
     */
    @TableField("sys_ai_companion_figure_aspect")
    private Double aspect; // aspect

    /**
     * head_box JSON（x/y/w/h）
     */
    @TableField(value = "sys_ai_companion_figure_head_box", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> headBox; // headbox

    /**
     * 尺寸档：s/m/l
     */
    @TableField("sys_ai_companion_figure_size_tier")
    private String sizeTier; // 大小tier

    /**
     * 源图片路径（两阶段上传的临时路径）
     */
    @TableField("sys_ai_companion_figure_source_path")
    private String sourcePath; // 源路径

    /**
     * 创建时间（轮次 毫秒）
     */
    @TableField("sys_ai_companion_figure_created_at")
    private Long createdAt; // 创建at

    /**
     * 归属用户标识（为空表示内置共享形象，所有人可见）
     */
    @TableField("sys_ai_companion_figure_user_id")
    private String userId; // 归属用户
}
