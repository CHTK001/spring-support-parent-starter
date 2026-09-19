package com.chua.starter.software.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * soft_源 软件源表
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@TableName("soft_source")
public class SoftSource implements Serializable {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 源_标识 - 主键标识
    */
    @TableId(type = IdType.ASSIGN_ID)
    /**
     * 来源标识
    */
    private String sourceId;

    /**
     * 源_名称 - 源名称
    */
    private String sourceName;

    /**
     * 源_编码 - 源编码
    */
    private String sourceCode;

    /**
     * 源_类型 - 源类型: 0官方 1第三方 2自建 3本地
    */
    private Integer sourceType;

    /**
     * 源_url - 源地址
    */
    private String sourceUrl;

    /**
     * 源_api_url - API地址
    */
    private String sourceApiUrl;

    /**
     * 源_用户名 - 用户名
    */
    private String sourceUsername;

    /**
     * 源_密码 - 密码(加密)
    */
    private String sourcePassword;

    /**
     * 源_icon - 源图标
    */
    private String sourceIcon;

    /**
     * 源_description - 源描述
    */
    private String sourceDescription;

    /**
     * 源_priority - 优先级
    */
    private Integer sourcePriority;

    /**
     * 源_是否_已启用 - 是否启用
    */
    private Boolean sourceIsEnabled;

    /**
     * 源_是否_默认 - 是否默认源
    */
    private Boolean sourceIsDefault;

    /**
     * 源_auto_同步 - 是否自动同步
    */
    private Boolean sourceAutoSync;

    /**
     * 源_同步_间隔 - 同步间隔(分钟)
    */
    private Integer sourceSyncInterval;

    /**
     * 源_最后一个_同步_时间 - 最后同步时间
    */
    private LocalDateTime sourceLastSyncTime;

    /**
     * 源_同步_状态 - 同步状态
    */
    private Integer sourceSyncStatus;

    /**
     * 源_software_数量 - 软件数量
    */
    private Integer sourceSoftwareCount;

    /**
     * 源_extra - 扩展信息JSON
    */
    private String sourceExtra;

    /**
     * 源_状态 - 状态: 0正常 1已删除 2已禁用
    */
    private Integer sourceStatus;

    /**
     * 源_排序 - 排序
    */
    private Integer sourceSort;

    /**
     * 源_创建_时间 - 创建时间
    */
    @TableField(fill = FieldFill.INSERT)
    /**
     * 来源创建时间
    */
    private LocalDateTime sourceCreateTime;

    /**
     * 源_更新_时间 - 更新时间
    */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /**
     * 来源更新时间
    */
    private LocalDateTime sourceUpdateTime;

    /**
     * 源_创建_by - 创建人
    */
    private String sourceCreateBy;

    /**
     * 源_更新_by - 更新人
    */
    private String sourceUpdateBy;
}
