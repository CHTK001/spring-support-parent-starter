package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * 创意工坊资产实体，对应 {@code workshop_asset} 表。
 *
 * <p>存储图片、视频、文本三类创意素材，含元数据、存储路径、来源信息。</p>
 *
 * <p>JSON 序列化使用 snake_case（与前端 {@code WorkshopAsset} 契约一致，
 * 前端 {@code parseAssetId(asset.asset_id)} 依赖 {@code asset_id} 字段）。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@TableName("workshop_asset")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WorkshopAsset {

    /**
     * 资产主键 标识（uuidv7）
     */
    @TableId
    @TableField("asset_id")
    private String assetId; // assetid

    /**
     * 资产类型（镜像 / 视频 / 文本）
     */
    @TableField("asset_kind")
    private String kind; // 种类

    /**
     * 资产标题
     */
    @TableField("asset_title")
    private String title; // title

    /**
     * 所属集合名称（空 表示未分组）
     */
    @TableField("asset_collection")
    private String collection; // 集合

    /**
     * 标签 JSON 数组
     */
    @TableField("asset_tags")
    private String tags; // 标签

    /**
     * MIME 类型
     */
    @TableField("asset_mime")
    private String mime; // mime

    /**
     * 图片/视频宽度（像素）
     */
    @TableField("asset_width")
    private Integer width; // width

    /**
     * 图片/视频高度（像素）
     */
    @TableField("asset_height")
    private Integer height; // height

    /**
     * 文件大小（字节）
     */
    @TableField("asset_bytes")
    private Long bytes; // bytes

    /**
     * 是否在资产库中（0=已移除，1=在库）
     */
    @TableField("asset_in_library")
    private Boolean inLibrary; // 入图书馆

    /**
     * 文本资产正文（种类=文本 时有效）
     */
    @TableField("asset_text_content")
    private String textContent; // 文本内容

    /**
     * 来源信息 JSON（提示符/模型/提供者_标识/Canvas_标识/节点_标识/创建_任务_标识）
     */
    @TableField("asset_origin")
    private String origin; // origin

    /**
     * 物理文件路径（相对于 storage 根目录）
     */
    @TableField("asset_file_path")
    private String filePath; // 文件路径

    /**
     * 缩略图路径（空 表示无缩略图）
     */
    @TableField("asset_thumb_path")
    private String thumbPath; // thumb路径

    /**
     * 创建时间（毫秒时间戳）
     */
    @TableField("asset_created_at")
    private Long createdAt; // 创建at

    /**
     * 最后更新时间（毫秒时间戳）
     */
    @TableField("asset_updated_at")
    private Long updatedAt; // 更新at

    /**
     * 归属用户标识，由服务端按当前登录用户写入，客户端传值一律忽略
     */
    @TableField("asset_user_id")
    private String userId; // 用户id
}
