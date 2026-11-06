package com.chua.starter.monitor.support.model;

/**
 * 系统配置修改请求。
 *
 * @param key   配置键（服务端按白名单校验）
 * @param value 新值（按配置项自身格式校验）
 * @author CH
 * @since 4.0.0.51
 */
public record SystemConfigApplyVO(String key, String value) {
}
