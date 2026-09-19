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
 * 需求数据源实体，持久化到 {@code sys_ai_data_source} 表。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_data_source", autoResultMap = true)
public class SysAiDataSource implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /** 数据源 标识（uuidv7） */
    @TableId(value = "sys_ai_data_source_id", type = IdType.INPUT)
    private String id; // 标识

    /** 数据源名称 */
    @TableField("sys_ai_data_source_name")
    private String name; // 名称

    /**
     * 类型标识，对应 {@link com.chua.starter.ai.support.manage.store.datasource.IRequirementDataSource#type()}。
     * 如 "lark"、"generic"。
     */
    @TableField("sys_ai_data_source_type")
    private String type; // 类型

    /** 连接配置（JSON，含 app_标识、app_secret、api_url 等，按需存储） */
    @TableField(value = "sys_ai_data_source_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config; // 配置

    /** 是否启用 */
    @TableField("sys_ai_data_source_enabled")
    private Boolean enabled; // 已启用

    /** 同步 cron 表达式，空 表示不自动同步 */
    @TableField("sys_ai_data_source_sync_cron")
    private String syncCron; // 同步cron

    /** 上次同步时间戳 */
    @TableField("sys_ai_data_source_last_sync_at")
    private Long lastSyncAt; // 最后一个同步at

    /** 上次同步状态：成功 / 失败 / running */
    @TableField("sys_ai_data_source_last_sync_status")
    private String lastSyncStatus; // 最后一个同步状态

    /** 上次同步错误信息 */
    @TableField("sys_ai_data_source_last_sync_error")
    private String lastSyncError; // 最后一个同步错误

    @TableField("create_name")
    private String createName; // 创建名称

    @TableField("create_by")
    private Integer createBy; // 创建by

    @TableField("create_time")
    private Long createTime; // 创建时间

    @TableField("update_name")
    private String updateName; // 更新名称

    @TableField("update_by")
    private Integer updateBy; // 更新by

    @TableField("update_time")
    private Long updateTime; // 更新时间
}
