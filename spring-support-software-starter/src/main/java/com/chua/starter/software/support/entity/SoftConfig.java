package com.chua.starter.software.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * soft_配置 软件配置表
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@TableName("soft_config")
public class SoftConfig implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /** 配置_标识 - 主键标识 */
    @TableId(type = IdType.ASSIGN_ID)
    /** 配置标识 */
    private String configId;

    /** 配置_software_标识 - 软件标识 */
    private String configSoftwareId;

    /** 配置_用户_标识 - 用户标识 */
    private String configUserId;

    /** 配置_device_标识 - 设备标识 */
    private String configDeviceId;

    /** 配置_Key - 配置键 */
    private String configKey;

    /** 配置_值 - 配置值 */
    private String configValue;

    /** 配置_类型 - 配置类型: 字符串/数字/布尔值/json/array */
    private String configType;

    /** 配置_标签 - 配置标签 */
    private String configLabel;

    /** 配置_description - 配置描述 */
    private String configDescription;

    /** 配置_群体 - 配置分组 */
    private String configGroup;

    /** 配置_排序 - 排序 */
    private Integer configSort;

    /** 配置_是否_visible - 是否可见 */
    private Boolean configIsVisible;

    /** 配置_是否_editable - 是否可编辑 */
    private Boolean configIsEditable;

    /** 配置_默认_值 - 默认值 */
    private String configDefaultValue;

    /** 配置_范围 - 取值范围JSON */
    private String configRange;

    /** 配置_extra - 扩展信息JSON */
    private String configExtra;

    /** 配置_创建_时间 - 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    /** 配置创建时间 */
    private LocalDateTime configCreateTime;

    /** 配置_更新_时间 - 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 配置更新时间 */
    private LocalDateTime configUpdateTime;

    /** 配置_创建_by - 创建人 */
    private String configCreateBy;

    /** 配置_更新_by - 更新人 */
    private String configUpdateBy;

    /**
     * 获取配置标识。
     * @return 获取配置id的结果
     */
    public String getConfigId() {
        return configId;
    }

    /**
     * 设置配置标识。
     * @param configId 配置标识
     */
    public void setConfigId(String configId) {
        this.configId = configId;
    }

    /**
     * 获取配置softwareid。
     * @return 获取配置softwareid的结果
     */
    public String getConfigSoftwareId() {
        return configSoftwareId;
    }

    /**
     * 设置配置softwareid。
     * @param configSoftwareId 配置softwareid
     */
    public void setConfigSoftwareId(String configSoftwareId) {
        this.configSoftwareId = configSoftwareId;
    }

    /**
     * 获取配置用户标识。
     * @return 获取配置用户id的结果
     */
    public String getConfigUserId() {
        return configUserId;
    }

    /**
     * 设置配置用户标识。
     * @param configUserId 配置用户标识
     */
    public void setConfigUserId(String configUserId) {
        this.configUserId = configUserId;
    }

    /**
     * 获取配置deviceid。
     * @return 获取配置deviceid的结果
     */
    public String getConfigDeviceId() {
        return configDeviceId;
    }

    /**
     * 设置配置deviceid。
     * @param configDeviceId 配置deviceid
     */
    public void setConfigDeviceId(String configDeviceId) {
        this.configDeviceId = configDeviceId;
    }

    /**
     * 获取配置Key。
     * @return 获取配置Key的结果
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * 设置配置Key。
     * @param configKey 配置Key
     */
    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    /**
     * 获取配置值。
     * @return 获取配置值的结果
     */
    public String getConfigValue() {
        return configValue;
    }

    /**
     * 设置配置值。
     * @param configValue 配置值
     */
    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    /**
     * 获取配置类型。
     * @return 获取配置类型的结果
     */
    public String getConfigType() {
        return configType;
    }

    /**
     * 设置配置类型。
     * @param configType 配置类型
     */
    public void setConfigType(String configType) {
        this.configType = configType;
    }

    /**
     * 获取配置标签。
     * @return 获取配置标签的结果
     */
    public String getConfigLabel() {
        return configLabel;
    }

    /**
     * 设置配置标签。
     * @param configLabel 配置标签
     */
    public void setConfigLabel(String configLabel) {
        this.configLabel = configLabel;
    }

    /**
     * 获取配置description。
     * @return 获取配置description的结果
     */
    public String getConfigDescription() {
        return configDescription;
    }

    /**
     * 设置配置description。
     * @param configDescription 配置description
     */
    public void setConfigDescription(String configDescription) {
        this.configDescription = configDescription;
    }

    /**
     * 获取配置群体。
     * @return 获取配置群体的结果
     */
    public String getConfigGroup() {
        return configGroup;
    }

    /**
     * 设置配置群体。
     * @param configGroup 配置群体
     */
    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    /**
     * 获取配置排序。
     * @return 获取配置排序的结果
     */
    public Integer getConfigSort() {
        return configSort;
    }

    /**
     * 设置配置排序。
     * @param configSort 配置排序
     */
    public void setConfigSort(Integer configSort) {
        this.configSort = configSort;
    }

    /**
     * 获取配置是否visible。
     * @return 获取配置是否visible的结果
     */
    public Boolean getConfigIsVisible() {
        return configIsVisible;
    }

    /**
     * 设置配置是否visible。
     * @param configIsVisible 配置是否visible
     */
    public void setConfigIsVisible(Boolean configIsVisible) {
        this.configIsVisible = configIsVisible;
    }

    /**
     * 获取配置是否editable。
     * @return 获取配置是否editable的结果
     */
    public Boolean getConfigIsEditable() {
        return configIsEditable;
    }

    /**
     * 设置配置是否editable。
     * @param configIsEditable 配置是否editable
     */
    public void setConfigIsEditable(Boolean configIsEditable) {
        this.configIsEditable = configIsEditable;
    }

    /**
     * 获取配置默认值。
     * @return 获取配置默认值的结果
     */
    public String getConfigDefaultValue() {
        return configDefaultValue;
    }

    /**
     * 设置配置默认值。
     * @param configDefaultValue 配置默认值
     */
    public void setConfigDefaultValue(String configDefaultValue) {
        this.configDefaultValue = configDefaultValue;
    }

    /**
     * 获取配置范围。
     * @return 获取配置范围的结果
     */
    public String getConfigRange() {
        return configRange;
    }

    /**
     * 设置配置范围。
     * @param configRange 配置范围
     */
    public void setConfigRange(String configRange) {
        this.configRange = configRange;
    }

    /**
     * 获取配置extra。
     * @return 获取配置extra的结果
     */
    public String getConfigExtra() {
        return configExtra;
    }

    /**
     * 设置配置extra。
     * @param configExtra 配置extra
     */
    public void setConfigExtra(String configExtra) {
        this.configExtra = configExtra;
    }

    /**
     * 获取配置创建时间。
     * @return 获取配置创建时间的结果
     */
    public LocalDateTime getConfigCreateTime() {
        return configCreateTime;
    }

    /**
     * 设置配置创建时间。
     * @param configCreateTime 配置创建时间
     */
    public void setConfigCreateTime(LocalDateTime configCreateTime) {
        this.configCreateTime = configCreateTime;
    }

    /**
     * 获取配置更新时间。
     * @return 获取配置更新时间的结果
     */
    public LocalDateTime getConfigUpdateTime() {
        return configUpdateTime;
    }

    /**
     * 设置配置更新时间。
     * @param configUpdateTime 配置更新时间
     */
    public void setConfigUpdateTime(LocalDateTime configUpdateTime) {
        this.configUpdateTime = configUpdateTime;
    }

    /**
     * 获取配置创建by。
     * @return 获取配置创建by的结果
     */
    public String getConfigCreateBy() {
        return configCreateBy;
    }

    /**
     * 设置配置创建by。
     * @param configCreateBy 配置创建by
     */
    public void setConfigCreateBy(String configCreateBy) {
        this.configCreateBy = configCreateBy;
    }

    /**
     * 获取配置更新by。
     * @return 获取配置更新by的结果
     */
    public String getConfigUpdateBy() {
        return configUpdateBy;
    }

    /**
     * 设置配置更新by。
     * @param configUpdateBy 配置更新by
     */
    public void setConfigUpdateBy(String configUpdateBy) {
        this.configUpdateBy = configUpdateBy;
    }
}
