package com.chua.starter.common.support.api.feature;

import java.io.Serializable;
import java.util.Set;

/**
 * API 功能开关信息
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
public class ApiFeatureInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 功能标识
     */
    private String featureId;

    /**
     * 功能描述
     */
    private String description;

    /**
     * 功能分组
     */
    private String group;

    /**
     * 默认是否启用
     */
    private boolean defaultEnabled;

    /**
     * 当前是否启用
     */
    private boolean enabled;

    /**
     * 关闭时的响应消息
     */
    private String disabledMessage;

    /**
     * 关闭时的响应状态码
     */
    private int disabledStatus;

    /**
     * 所属类名
     */
    private String className;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 接口路径
     */
    private Set<String> patterns;

    /**
     * 无参构造函数
     */
    public ApiFeatureInfo() {
    }

    /**
     * 构造函数
     *
     * @param featureId 功能标识
     * @param description 功能描述
     * @param group 功能分组
     * @param defaultEnabled 默认是否启用
     * @param enabled 当前是否启用
     * @param disabledMessage 关闭时的响应消息
     * @param disabledStatus 关闭时的响应状态码
     * @param className 所属类名
     * @param methodName 方法名
     * @param patterns 接口路径
     */
    public ApiFeatureInfo(String featureId, String description, String group, boolean defaultEnabled,
                         boolean enabled, String disabledMessage, int disabledStatus, String className,
                         String methodName, Set<String> patterns) {
        this.featureId = featureId;
        this.description = description;
        this.group = group;
        this.defaultEnabled = defaultEnabled;
        this.enabled = enabled;
        this.disabledMessage = disabledMessage;
        this.disabledStatus = disabledStatus;
        this.className = className;
        this.methodName = methodName;
        this.patterns = patterns;
    }

    /**
     * 创建 Builder
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建 toBuilder
     *
     * @return Builder 实例
     */
    public Builder toBuilder() {
        return new Builder()
                .featureId(this.featureId)
                .description(this.description)
                .group(this.group)
                .defaultEnabled(this.defaultEnabled)
                .enabled(this.enabled)
                .disabledMessage(this.disabledMessage)
                .disabledStatus(this.disabledStatus)
                .className(this.className)
                .methodName(this.methodName)
                .patterns(this.patterns);
    }

    /**
     * Builder 类
     */
    public static class Builder {
        private String featureId;
        private String description;
        private String group;
        private boolean defaultEnabled;
        private boolean enabled;
        private String disabledMessage;
        private int disabledStatus;
        private String className;
        private String methodName;
        private Set<String> patterns;

        public Builder featureId(String featureId) {
            this.featureId = featureId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public Builder defaultEnabled(boolean defaultEnabled) {
            this.defaultEnabled = defaultEnabled;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder disabledMessage(String disabledMessage) {
            this.disabledMessage = disabledMessage;
            return this;
        }

        public Builder disabledStatus(int disabledStatus) {
            this.disabledStatus = disabledStatus;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        public Builder patterns(Set<String> patterns) {
            this.patterns = patterns;
            return this;
        }

        public ApiFeatureInfo build() {
            return new ApiFeatureInfo(featureId, description, group, defaultEnabled, enabled,
                    disabledMessage, disabledStatus, className, methodName, patterns);
        }
    }

    /**
     * 获取 featureId
     *
     * @return featureId
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * 设置 featureId
     *
     * @param featureId featureId
     */
    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    /**
     * 获取 description
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置 description
     *
     * @param description description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取 group
     *
     * @return group
     */
    public String getGroup() {
        return group;
    }

    /**
     * 设置 group
     *
     * @param group group
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * 获取 defaultEnabled
     *
     * @return defaultEnabled
     */
    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    /**
     * 设置 defaultEnabled
     *
     * @param defaultEnabled defaultEnabled
     */
    public void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    /**
     * 获取 enabled
     *
     * @return enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 enabled
     *
     * @param enabled enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 disabledMessage
     *
     * @return disabledMessage
     */
    public String getDisabledMessage() {
        return disabledMessage;
    }

    /**
     * 设置 disabledMessage
     *
     * @param disabledMessage disabledMessage
     */
    public void setDisabledMessage(String disabledMessage) {
        this.disabledMessage = disabledMessage;
    }

    /**
     * 获取 disabledStatus
     *
     * @return disabledStatus
     */
    public int getDisabledStatus() {
        return disabledStatus;
    }

    /**
     * 设置 disabledStatus
     *
     * @param disabledStatus disabledStatus
     */
    public void setDisabledStatus(int disabledStatus) {
        this.disabledStatus = disabledStatus;
    }

    /**
     * 获取 className
     *
     * @return className
     */
    public String getClassName() {
        return className;
    }

    /**
     * 设置 className
     *
     * @param className className
     */
    public void setClassName(String className) {
        this.className = className;
    }

    /**
     * 获取 methodName
     *
     * @return methodName
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * 设置 methodName
     *
     * @param methodName methodName
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * 获取 patterns
     *
     * @return patterns
     */
    public Set<String> getPatterns() {
        return patterns;
    }

    /**
     * 设置 patterns
     *
     * @param patterns patterns
     */
    public void setPatterns(Set<String> patterns) {
        this.patterns = patterns;
    }
}
