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

    private static final long serialVersionUID = 1L;

    /** 数据源 ID（UUIDv7） */
    @TableId(value = "sys_ai_data_source_id", type = IdType.INPUT)
    private String id;

    /** 数据源名称 */
    @TableField("sys_ai_data_source_name")
    private String name;

    /**
     * 类型标识，对应 {@link com.chua.starter.ai.support.manage.store.datasource.IRequirementDataSource#type()}。
     * 如 "lark"、"generic"。
     */
    @TableField("sys_ai_data_source_type")
    private String type;

    /** 连接配置（JSON，含 app_id、app_secret、api_url 等，按需存储） */
    @TableField(value = "sys_ai_data_source_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    /** 是否启用 */
    @TableField("sys_ai_data_source_enabled")
    private Boolean enabled;

    /** 同步 cron 表达式，null 表示不自动同步 */
    @TableField("sys_ai_data_source_sync_cron")
    private String syncCron;

    /** 上次同步时间戳 */
    @TableField("sys_ai_data_source_last_sync_at")
    private Long lastSyncAt;

    /** 上次同步状态：success / failed / running */
    @TableField("sys_ai_data_source_last_sync_status")
    private String lastSyncStatus;

    /** 上次同步错误信息 */
    @TableField("sys_ai_data_source_last_sync_error")
    private String lastSyncError;

    @TableField("create_name")
    private String createName;

    @TableField("create_by")
    private Integer createBy;

    @TableField("create_time")
    private Long createTime;

    @TableField("update_name")
    private String updateName;

    @TableField("update_by")
    private Integer updateBy;

    @TableField("update_time")
    private Long updateTime;
}
