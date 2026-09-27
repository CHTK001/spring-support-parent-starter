package com.chua.starter.monitor.support.model;

/**
 * 环境变量写入入参。
 *
 * @param name  变量名，必须为合法标识符（{@code ^[A-Za-z_][A-Za-z0-9_]*$}）
 * @param value 变量值，可为空串
 * @author CH
 * @since 4.0.0.42
 */
public record EnvVarInputVO(String name, String value) {
}
