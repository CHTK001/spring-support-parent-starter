package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图像底库条目，对应表 {@code sys_ai_image_library}（以图搜图）。
 *
 * <p>与 {@link SysAiFaceLibrary} 同构：<b>向量不在本表</b>，靠
 * {@link #vectorId} 关联 jvector 中的同名条目；本表只存元数据。</p>
 *
 * @since 4.0.0.44
 * @author CH
 */
@Data
@TableName("sys_ai_image_library")
public class SysAiImageLibrary {

    /**
     * 主键
     */
    @TableId(value = "sys_ai_image_library_id", type = IdType.AUTO)
    private Integer id;

    /**
     * 展示名
     */
    @TableField("sys_ai_image_library_name")
    private String name;

    /**
     * 向量库中的条目标识，与 jvector 里的 key 同名 —— 这是两者的关联键。
     */
    @TableField("sys_ai_image_library_vector_id")
    private String vectorId;

    /**
     * 缩略图（base64）
     */
    @TableField("sys_ai_image_library_thumbnail")
    private String thumbnail;

    /**
     * 原图位置。
     *
     * <p>存的是相对文件名而非完整路径：底库目录随配置（{@code face.store.dir}）变化，
     * 存绝对路径等于把一次配置变更变成一次全量数据损坏。</p>
     */
    @TableField("sys_ai_image_library_url")
    private String url;

    /**
     * 标签（逗号分隔）
     */
    @TableField("sys_ai_image_library_tags")
    private String tags;

    /**
     * 其余元数据（JSON 字符串）：特征模型名、入库时间、原图字节数等。
     */
    @TableField("sys_ai_image_library_meta")
    private String meta;

    /**
     * 图片宽（像素）
     */
    @TableField("sys_ai_image_library_width")
    private Integer width;

    /**
     * 图片高（像素）
     */
    @TableField("sys_ai_image_library_height")
    private Integer height;

    /**
     * 归属账号。
     */
    @TableField("sys_ai_image_library_user_id")
    private String userId;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_image_library_status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
