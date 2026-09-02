package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

/**
 * 创意工坊资产实体，对应表 {@code workshop_asset}。
 *
 * <p>存储图片、视频、文本三种类型的创作素材，包含元数据、存储路径和来源信息。</p>
 *
 * @author CH
 */
@Data
@TableName("workshop_asset")
public class WorkshopAsset {

    /**
     * 资产主键 ID（UUIDv7）
     */
    @TableId
    @TableField("asset_id")
    private String assetId;

    /**
     * 资产类型（image / video / text）
     */
    @TableField("asset_kind")
    private String kind;

    /**
     * 资产标题
     */
    @TableField("asset_title")
    private String title;

    /**
     * 所属集合名称（null 表示未分组）
     */
    @TableField("asset_collection")
    private String collection;

    /**
     * 标签 JSON 数组
     */
    @TableField("asset_tags")
    private String tags;

    /**
     * MIME 类型
     */
    @TableField("asset_mime")
    private String mime;

    /**
     * 图片/视频宽度（像素）
     */
    @TableField("asset_width")
    private Integer width;

    /**
     * 图片/视频高度（像素）
     */
    @TableField("asset_height")
    private Integer height;

    /**
     * 文件大小（字节）
     */
    @TableField("asset_bytes")
    private Long bytes;

    /**
     * 是否在资产库中（0=已移除，1=在库）
     */
    @TableField("asset_in_library")
    private Boolean inLibrary;

    /**
     * 文本资产正文（kind=text 时有效）
     */
    @TableField("asset_text_content")
    private String textContent;

    /**
     * 来源信息 JSON（prompt/model/provider_id/canvas_id/node_id/creation_task_id）
     */
    @TableField("asset_origin")
    private String origin;

    /**
     * 物理文件路径（相对于 storage 根目录）
     */
    @TableField("asset_file_path")
    private String filePath;

    /**
     * 缩略图路径（null 表示无缩略图）
     */
    @TableField("asset_thumb_path")
    private String thumbPath;

    /**
     * 创建时间（毫秒时间戳）
     */
    @TableField("asset_created_at")
    private Long createdAt;

    /**
     * 最后更新时间（毫秒时间戳）
     */
    @TableField("asset_updated_at")
    private Long updatedAt;
}
