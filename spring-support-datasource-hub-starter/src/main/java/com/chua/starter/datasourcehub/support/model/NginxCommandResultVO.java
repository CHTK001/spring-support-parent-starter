package com.chua.starter.datasourcehub.support.model;

/**
 * Nginx 命令执行结果（语法检查 / 重载 / 停止）。
 *
 * @param success  退出码为 0 时为 true
 * @param message  面向用户的结果摘要（成功或失败原因）
 * @param stdout   标准输出原文
 * @param stderr   标准错误原文（nginx -t 的提示通常在 stderr）
 * @author CH
 * @since 4.0.0.42
 */
public record NginxCommandResultVO(
        boolean success,
        String message,
        String stdout,
        String stderr
) {
}
