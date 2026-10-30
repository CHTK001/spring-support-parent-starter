package com.chua.starter.common.support.api.feature;

import com.chua.common.support.lang.version.Version;

import java.io.Serializable;
import java.util.Set;

/**
 * API 功能开关信息
 *
 * @param featureId        功能标识
 * @param description      功能描述
 * @param group           功能分组
 * @param defaultEnabled  默认是否启用
 * @param enabled         当前是否启用
 * @param disabledMessage 关闭时的响应消息
 * @param disabledStatus  关闭时的响应状态码
 * @param className       所属类名
 * @param methodName      方法名
 * @param patterns        接口路径
 * @since 2024/12/08
 * @author CH
 */
public record ApiFeatureInfo(
        String featureId,
        String description,
        String group,
        boolean defaultEnabled,
        boolean enabled,
        String disabledMessage,
        int disabledStatus,
        String className,
        String methodName,
        Set<String> patterns) implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 创建构建器
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建 转为构建器
     *
     * @return Builder 实例
     */
    public Builder toBuilder() {
        return new Builder()
                .featureId(featureId)
                .description(description)
                .group(group)
                .defaultEnabled(defaultEnabled)
                .enabled(enabled)
                .disabledMessage(disabledMessage)
                .disabledStatus(disabledStatus)
                .className(className)
                .methodName(methodName)
                .patterns(patterns);
    }

    /**
     * 构建器类
     *
     * @author CH
     * @since 4.0.0
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

        /**
         * feature Id
         * @param featureId 字符串
         * @return 返回值
        */
        public Builder featureId(String featureId) {
            this.featureId = featureId;
            return this;
        }

        /**
         * description
         * @param description 字符串
         * @return 返回值
        */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * group
         * @param group 字符串
         * @return 返回值
        */
        public Builder group(String group) {
            this.group = group;
            return this;
        }

        /**
         * default Enabled
         * @param defaultEnabled 布尔值
         * @return 返回值
        */
        public Builder defaultEnabled(boolean defaultEnabled) {
            this.defaultEnabled = defaultEnabled;
            return this;
        }

        /**
         * enabled
         * @param enabled 布尔值
         * @return 返回值
        */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * disabled Message
         * @param disabledMessage 字符串
         * @return 返回值
        */
        public Builder disabledMessage(String disabledMessage) {
            this.disabledMessage = disabledMessage;
            return this;
        }

        /**
         * disabled Status
         * @param disabledStatus 整数
         * @return 返回值
        */
        public Builder disabledStatus(int disabledStatus) {
            this.disabledStatus = disabledStatus;
            return this;
        }

        /**
         * class Name
         * @param className 字符串
         * @return 返回值
        */
        public Builder className(String className) {
            this.className = className;
            return this;
        }

        /**
         * method Name
         * @param methodName 字符串
         * @return 返回值
        */
        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        /**
         * patterns
         * @param patterns 参数
         * @return 返回值
        */
        public Builder patterns(Set<String> patterns) {
            this.patterns = patterns;
            return this;
        }

        /**
         * 构建
         * @return 返回值
        */
        public ApiFeatureInfo build() {
            return new ApiFeatureInfo(featureId, description, group, defaultEnabled, enabled,
                    disabledMessage, disabledStatus, className, methodName, patterns);
        }
    }
}
