package com.chua.starter.software.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * soft_source 软件源表
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@TableName("soft_source")
public class SoftSource implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /** source_id - 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    /** 来源ID */
    private String sourceId;

    /** source_name - 源名称 */
    /** 来源名称 */
    private String sourceName;

    /** source_code - 源编码 */
    /** 来源代码 */
    private String sourceCode;

    /** source_type - 源类型: 0官方 1第三方 2自建 3本地 */
    /** 来源类型 */
    private Integer sourceType;

    /** source_url - 源地址 */
    /** 来源URL */
    private String sourceUrl;

    /** source_api_url - API地址 */
    /** 来源APIURL */
    private String sourceApiUrl;

    /** source_username - 用户名 */
    /** 来源username */
    private String sourceUsername;

    /** source_password - 密码(加密) */
    /** 来源密码 */
    private String sourcePassword;

    /** source_icon - 源图标 */
    /** 来源icon */
    private String sourceIcon;

    /** source_description - 源描述 */
    /** 来源描述 */
    private String sourceDescription;

    /** source_priority - 优先级 */
    /** 来源优先级 */
    private Integer sourcePriority;

    /** source_is_enabled - 是否启用 */
    /** 来源IS是否启用 */
    private Boolean sourceIsEnabled;

    /** source_is_default - 是否默认源 */
    /** 来源IS默认 */
    private Boolean sourceIsDefault;

    /** source_auto_sync - 是否自动同步 */
    /** 来源auto同步 */
    private Boolean sourceAutoSync;

    /** source_sync_interval - 同步间隔(分钟) */
    /** 来源同步间隔 */
    private Integer sourceSyncInterval;

    /** source_last_sync_time - 最后同步时间 */
    /** 来源最后同步时间 */
    private LocalDateTime sourceLastSyncTime;

    /** source_sync_status - 同步状态 */
    /** 来源同步状态 */
    private Integer sourceSyncStatus;

    /** source_software_count - 软件数量 */
    /** 来源software数量 */
    private Integer sourceSoftwareCount;

    /** source_extra - 扩展信息JSON */
    /** 来源extra */
    private String sourceExtra;

    /** source_status - 状态: 0正常 1已删除 2已禁用 */
    /** 来源状态 */
    private Integer sourceStatus;

    /** source_sort - 排序 */
    /** 来源sort */
    private Integer sourceSort;

    /** source_create_time - 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    /** 来源create时间 */
    private LocalDateTime sourceCreateTime;

    /** source_update_time - 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 来源update时间 */
    private LocalDateTime sourceUpdateTime;

    /** source_create_by - 创建人 */
    /** 来源createBY */
    private String sourceCreateBy;

    /** source_update_by - 更新人 */
    /** 来源updateBY */
    private String sourceUpdateBy;
}
