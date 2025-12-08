package com.chua.starter.common.support.api.feature;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * API 功能开关信息
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
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
}
