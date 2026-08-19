package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 监控全局配置实体
 *
 * @since 2026/8/4
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控全局配置")
@TableName("ms_monitor_config")
public class MsMonitorConfig extends SysBase {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    /** ID */
    private Long id;

    /**
     * 配置项 key
     */
    @TableField("ms_monitor_config_key")
    @Schema(description = "配置项 key", example = "monitor.data.retention.days")
    /** Monitor配置密钥 */
    private String monitorConfigKey;

    /**
     * 配置项 value
     */
    @TableField("ms_monitor_config_value")
    @Schema(description = "配置项 value", example = "30")
    /** Monitor配置值 */
    private String monitorConfigValue;

    /**
     * 配置项描述
     */
    @TableField("ms_monitor_config_desc")
    @Schema(description = "配置项描述", example = "历史数据保留天数")
    /** Monitor配置desc */
    private String monitorConfigDesc;
}
