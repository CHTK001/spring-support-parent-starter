package com.chua.starter.common.support.api.feature;
import com.chua.common.support.lang.version.Version;

import java.io.Serializable;
import java.util.Set;

/**
 * API 功能开关信息
 *
 * @since 2024/12/08
 * @版本 1.0.0
 * @author CH
 */
public class ApiFeatureInfo implements Serializable {

    /** 串行版本UID */
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
     * 创建 构建器
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        /**
         * 构建器。
         * @return 构建器的结果
         */
        return new Builder();
    }

    /**
     * 创建 转为构建器
     *
     * @return Builder 实例
     */
    public Builder toBuilder() {
        /**
         * 构建器。
         * @return 构建器的结果
         */
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
     * 构建器 类
     * @author CH
     * @since 4.0.0
     */
    public static class Builder {
        /** 特征标识 */
        private String featureId;
        /** 描述 */
        private String description;
        /** 分组 */
        private String group;
        /** 默认是否启用 */
        private boolean defaultEnabled;
        /** 是否启用 */
        private boolean enabled;
        /** 已禁用消息 */
        private String disabledMessage;
        /** 已禁用状态 */
        private int disabledStatus;
        /** 类名称 */
        private String className;
        /** 方法名称 */
        private String methodName;
        /** 模式 */
        private Set<String> patterns;

        /**
         * 特征标识
         *
         * @param featureId 特征标识
         * @return 特征id的结果
         */
        public Builder featureId(String featureId) {
            this.featureId = featureId;
            return this;
        }

        /**
         * Description
         *
         * @param description description
         * @return description的结果
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 分组
         *
         * @param group 群体
         * @return 群体的结果
         */
        public Builder group(String group) {
            this.group = group;
            return this;
        }

        /**
         * 默认已启用
         *
         * @param defaultEnabled 默认已启用
         * @return 默认已启用的结果
         */
        public Builder defaultEnabled(boolean defaultEnabled) {
            this.defaultEnabled = defaultEnabled;
            return this;
        }

        /**
         * 已启用
         *
         * @param enabled 已启用
         * @return 已启用的结果
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * 已禁用消息
         *
         * @param disabledMessage 已禁用消息
         * @return 已禁用消息的结果
         */
        public Builder disabledMessage(String disabledMessage) {
            this.disabledMessage = disabledMessage;
            return this;
        }

        /**
         * 已禁用状态
         *
         * @param disabledStatus 已禁用状态
         * @return 已禁用状态的结果
         */
        public Builder disabledStatus(int disabledStatus) {
            this.disabledStatus = disabledStatus;
            return this;
        }

        /**
         * 类名称
         *
         * @param className 类名称
         * @return 类名称的结果
         */
        public Builder className(String className) {
            this.className = className;
            return this;
        }

        /**
         * 方法名称
         *
         * @param methodName 方法名称
         * @return 方法名称的结果
         */
        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        /**
         * 模式
         *
         * @param patterns 模式
         * @return 模式的结果
         */
        public Builder patterns(Set<String> patterns) {
            this.patterns = patterns;
            return this;
        }

        /**
         * 构建
         *
         * @return 构建的结果
         */
        public ApiFeatureInfo build() {
            return new ApiFeatureInfo(featureId, description, group, defaultEnabled, enabled,
                    disabledMessage, disabledStatus, className, methodName, patterns);
        }
    }

    /**
     * 获取 特征标识
     *
     * @return featureId
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * 设置 特征标识
     *
     * @param featureId 特征标识
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
     * 获取 群体
     *
     * @return group
     */
    public String getGroup() {
        return group;
    }

    /**
     * 设置 群体
     *
     * @param group 群体
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * 获取 默认已启用
     *
     * @return defaultEnabled
     */
    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    /**
     * 设置 默认已启用
     *
     * @param defaultEnabled 默认已启用
     */
    public void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    /**
     * 获取 已启用
     *
     * @return enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 已启用
     *
     * @param enabled 已启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 已禁用消息
     *
     * @return disabledMessage
     */
    public String getDisabledMessage() {
        return disabledMessage;
    }

    /**
     * 设置 已禁用消息
     *
     * @param disabledMessage 已禁用消息
     */
    public void setDisabledMessage(String disabledMessage) {
        this.disabledMessage = disabledMessage;
    }

    /**
     * 获取 已禁用状态
     *
     * @return disabledStatus
     */
    public int getDisabledStatus() {
        return disabledStatus;
    }

    /**
     * 设置 已禁用状态
     *
     * @param disabledStatus 已禁用状态
     */
    public void setDisabledStatus(int disabledStatus) {
        this.disabledStatus = disabledStatus;
    }

    /**
     * 获取 类名称
     *
     * @return className
     */
    public String getClassName() {
        return className;
    }

    /**
     * 设置 类名称
     *
     * @param className 类名称
     */
    public void setClassName(String className) {
        this.className = className;
    }

    /**
     * 获取 方法名称
     *
     * @return methodName
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * 设置 方法名称
     *
     * @param methodName 方法名称
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * 获取 模式
     *
     * @return patterns
     */
    public Set<String> getPatterns() {
        return patterns;
    }

    /**
     * 设置 模式
     *
     * @param patterns 模式
     */
    public void setPatterns(Set<String> patterns) {
        this.patterns = patterns;
    }
}
