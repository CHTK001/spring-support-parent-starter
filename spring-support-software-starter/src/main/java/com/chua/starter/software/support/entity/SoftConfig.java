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
    /** 配置softwareID */
    private String configSoftwareId;

    /** config_user_id - 用户ID */
    /** 配置用户ID */
    private String configUserId;

    /** config_device_id - 设备ID */
    /** 配置deviceID */
    private String configDeviceId;

    /** config_key - 配置键 */
    /** 配置密钥 */
    private String configKey;

    /** config_value - 配置值 */
    /** 配置值 */
    private String configValue;

    /** config_type - 配置类型: string/number/boolean/json/array */
    /** 配置类型 */
    private String configType;

    /** config_label - 配置标签 */
    /** 配置标签 */
    private String configLabel;

    /** config_description - 配置描述 */
    /** 配置描述 */
    private String configDescription;

    /** config_group - 配置分组 */
    /** 配置分组 */
    private String configGroup;

    /** config_sort - 排序 */
    /** 配置sort */
    private Integer configSort;

    /** config_is_visible - 是否可见 */
    /** 配置ISvisible */
    private Boolean configIsVisible;

    /** config_is_editable - 是否可编辑 */
    /** 配置ISeditable */
    private Boolean configIsEditable;

    /** config_default_value - 默认值 */
    /** 配置默认值 */
    private String configDefaultValue;

    /** config_range - 取值范围JSON */
    /** 配置range */
    private String configRange;

    /** config_extra - 扩展信息JSON */
    /** 配置extra */
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
    /** 配置createBY */
    private String configCreateBy;

    /** config_update_by - 更新人 */
    /** 配置updateBY */
    private String configUpdateBy;

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getConfigSoftwareId() {
        return configSoftwareId;
    }

    public void setConfigSoftwareId(String configSoftwareId) {
        this.configSoftwareId = configSoftwareId;
    }

    public String getConfigUserId() {
        return configUserId;
    }

    public void setConfigUserId(String configUserId) {
        this.configUserId = configUserId;
    }

    public String getConfigDeviceId() {
        return configDeviceId;
    }

    public void setConfigDeviceId(String configDeviceId) {
        this.configDeviceId = configDeviceId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public String getConfigLabel() {
        return configLabel;
    }

    public void setConfigLabel(String configLabel) {
        this.configLabel = configLabel;
    }

    public String getConfigDescription() {
        return configDescription;
    }

    public void setConfigDescription(String configDescription) {
        this.configDescription = configDescription;
    }

    public String getConfigGroup() {
        return configGroup;
    }

    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    public Integer getConfigSort() {
        return configSort;
    }

    public void setConfigSort(Integer configSort) {
        this.configSort = configSort;
    }

    public Boolean getConfigIsVisible() {
        return configIsVisible;
    }

    public void setConfigIsVisible(Boolean configIsVisible) {
        this.configIsVisible = configIsVisible;
    }

    public Boolean getConfigIsEditable() {
        return configIsEditable;
    }

    public void setConfigIsEditable(Boolean configIsEditable) {
        this.configIsEditable = configIsEditable;
    }

    public String getConfigDefaultValue() {
        return configDefaultValue;
    }

    public void setConfigDefaultValue(String configDefaultValue) {
        this.configDefaultValue = configDefaultValue;
    }

    public String getConfigRange() {
        return configRange;
    }

    public void setConfigRange(String configRange) {
        this.configRange = configRange;
    }

    public String getConfigExtra() {
        return configExtra;
    }

    public void setConfigExtra(String configExtra) {
        this.configExtra = configExtra;
    }

    public LocalDateTime getConfigCreateTime() {
        return configCreateTime;
    }

    public void setConfigCreateTime(LocalDateTime configCreateTime) {
        this.configCreateTime = configCreateTime;
    }

    public LocalDateTime getConfigUpdateTime() {
        return configUpdateTime;
    }

    public void setConfigUpdateTime(LocalDateTime configUpdateTime) {
        this.configUpdateTime = configUpdateTime;
    }

    public String getConfigCreateBy() {
        return configCreateBy;
    }

    public void setConfigCreateBy(String configCreateBy) {
        this.configCreateBy = configCreateBy;
    }

    public String getConfigUpdateBy() {
        return configUpdateBy;
    }

    public void setConfigUpdateBy(String configUpdateBy) {
        this.configUpdateBy = configUpdateBy;
    }
}
