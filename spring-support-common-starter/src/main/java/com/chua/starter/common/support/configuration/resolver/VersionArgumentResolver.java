package com.chua.starter.common.support.configuration.resolver;

/**
 * 版本参数解析器接口
 * <p>
 * 用于解析和提供API版本信息。
 * </p>
 *
 * @author CH
 * @since 2024/8/26
 * @version 1.0.0
 */
public interface VersionArgumentResolver {

    /**
     * 获取当前版本
     *
     * @return 版本字符串
     */
    String version();
}

