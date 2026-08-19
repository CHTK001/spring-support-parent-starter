package com.chua.starter.software.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * soft_config 软件配置表
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@TableName("soft_config")
public class SoftConfig implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /** config_id - 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    /** 配置ID */
    private String configId;

    /** config_software_id - 软件ID */
    private String configSoftwareId;

    /** config_user_id - 用户ID */
    private String configUserId;

    /** config_device_id - 设备ID */
    private String configDeviceId;

    /** config_key - 配置键 */
    private String configKey;

    /** config_value - 配置值 */
    private String configValue;

    /** config_type - 配置类型: string/number/boolean/json/array */
    private String configType;

    /** config_label - 配置标签 */
    private String configLabel;

    /** config_description - 配置描述 */
    private String configDescription;

    /** config_group - 配置分组 */
    private String configGroup;

    /** config_sort - 排序 */
    private Integer configSort;

    /** config_is_visible - 是否可见 */
    private Boolean configIsVisible;

    /** config_is_editable - 是否可编辑 */
    private Boolean configIsEditable;

    /** config_default_value - 默认值 */
    private String configDefaultValue;

    /** config_range - 取值范围JSON */
    private String configRange;

    /** config_extra - 扩展信息JSON */
    private String configExtra;

    /** config_create_time - 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    /** 配置create时间 */
    private LocalDateTime configCreateTime;

    /** config_update_time - 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 配置update时间 */
    private LocalDateTime configUpdateTime;

    /** config_create_by - 创建人 */
    private String configCreateBy;

    /** config_update_by - 更新人 */
    private String configUpdateBy;

    /** 获取ConfigId */
    public String getConfigId() {
        return configId;
    }

    /** 设置ConfigId */
    public void setConfigId(String configId) {
        this.configId = configId;
    }

    /** 获取ConfigSoftwareId */
    public String getConfigSoftwareId() {
        return configSoftwareId;
    }

    /** 设置ConfigSoftwareId */
    public void setConfigSoftwareId(String configSoftwareId) {
        this.configSoftwareId = configSoftwareId;
    }

    /** 获取ConfigUserId */
    public String getConfigUserId() {
        return configUserId;
    }

    /** 设置ConfigUserId */
    public void setConfigUserId(String configUserId) {
        this.configUserId = configUserId;
    }

    /** 获取ConfigDeviceId */
    public String getConfigDeviceId() {
        return configDeviceId;
    }

    /** 设置ConfigDeviceId */
    public void setConfigDeviceId(String configDeviceId) {
        this.configDeviceId = configDeviceId;
    }

    /** 获取ConfigKey */
    public String getConfigKey() {
        return configKey;
    }

    /** 设置ConfigKey */
    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    /** 获取ConfigValue */
    public String getConfigValue() {
        return configValue;
    }

    /** 设置ConfigValue */
    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    /** 获取ConfigType */
    public String getConfigType() {
        return configType;
    }

    /** 设置ConfigType */
    public void setConfigType(String configType) {
        this.configType = configType;
    }

    /** 获取ConfigLabel */
    public String getConfigLabel() {
        return configLabel;
    }

    /** 设置ConfigLabel */
    public void setConfigLabel(String configLabel) {
        this.configLabel = configLabel;
    }

    /** 获取ConfigDescription */
    public String getConfigDescription() {
        return configDescription;
    }

    /** 设置ConfigDescription */
    public void setConfigDescription(String configDescription) {
        this.configDescription = configDescription;
    }

    /** 获取Config分组 */
    public String getConfigGroup() {
        return configGroup;
    }

    /** 设置Config分组 */
    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    /** 获取Config排序 */
    public Integer getConfigSort() {
        return configSort;
    }

    /** 设置Config排序 */
    public void setConfigSort(Integer configSort) {
        this.configSort = configSort;
    }

    /** 获取Config是否Visible */
    public Boolean getConfigIsVisible() {
        return configIsVisible;
    }

    /** 设置Config是否Visible */
    public void setConfigIsVisible(Boolean configIsVisible) {
        this.configIsVisible = configIsVisible;
    }

    /** 获取Config是否Editable */
    public Boolean getConfigIsEditable() {
        return configIsEditable;
    }

    /** 设置Config是否Editable */
    public void setConfigIsEditable(Boolean configIsEditable) {
        this.configIsEditable = configIsEditable;
    }

    /** 获取ConfigDefaultValue */
    public String getConfigDefaultValue() {
        return configDefaultValue;
    }

    /** 设置ConfigDefaultValue */
    public void setConfigDefaultValue(String configDefaultValue) {
        this.configDefaultValue = configDefaultValue;
    }

    /** 获取ConfigRange */
    public String getConfigRange() {
        return configRange;
    }

    /** 设置ConfigRange */
    public void setConfigRange(String configRange) {
        this.configRange = configRange;
    }

    /** 获取ConfigExtra */
    public String getConfigExtra() {
        return configExtra;
    }

    /** 设置ConfigExtra */
    public void setConfigExtra(String configExtra) {
        this.configExtra = configExtra;
    }

    /** 获取Config创建Time */
    public LocalDateTime getConfigCreateTime() {
        return configCreateTime;
    }

    /** 设置Config创建Time */
    public void setConfigCreateTime(LocalDateTime configCreateTime) {
        this.configCreateTime = configCreateTime;
    }

    /** 获取Config更新Time */
    public LocalDateTime getConfigUpdateTime() {
        return configUpdateTime;
    }

    /** 设置Config更新Time */
    public void setConfigUpdateTime(LocalDateTime configUpdateTime) {
        this.configUpdateTime = configUpdateTime;
    }

    /** 获取Config创建By */
    public String getConfigCreateBy() {
        return configCreateBy;
    }

    /** 设置Config创建By */
    public void setConfigCreateBy(String configCreateBy) {
        this.configCreateBy = configCreateBy;
    }

    /** 获取Config更新By */
    public String getConfigUpdateBy() {
        return configUpdateBy;
    }

    /** 设置Config更新By */
    public void setConfigUpdateBy(String configUpdateBy) {
        this.configUpdateBy = configUpdateBy;
    }
}
