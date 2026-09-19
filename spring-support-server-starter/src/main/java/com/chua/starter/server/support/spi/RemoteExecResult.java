package com.chua.starter.server.support.spi;

/**
 * 远程命令执行结果
 * <p>
 * 屏蔽底层传输差异：POSIX 走 SSH，Windows 走 WinRM，上层统一拿到
 * 退出码 + 标准输出 + 错误输出。
 * </p>
 *
 * @param exitCode 退出码，0 表示成功
 * @param stdout   标准输出，可能为空串
 * @param stderr   错误输出，可能为空串
 * @author CH
 * @since 4.0.0.42
 * @return 结果值
 */
public record RemoteExecResult(int exitCode, String stdout, String stderr) {

    /**
     * 空成功结果（无输出的写类操作使用）
     *
     * @return 退出码 0 的空结果
     */
    public static RemoteExecResult ok() {
        return new RemoteExecResult(0, "", "");
    }

    /**
     * 构造成功结果
     *
     * @param stdout 标准输出
     * @return 退出码 0 的结果
     */
    public static RemoteExecResult ok(String stdout) {
        return new RemoteExecResult(0, stdout == null ? "" : stdout, "");
    }

    /**
     * 是否执行成功
     *
     * @return 退出码是否为 0
     */
    public boolean success() {
        return exitCode == 0;
    }

    /**
     * 失败原因：优先错误输出，其次退出码描述
     *
     * @return 可直接写入日志的错误描述
     */
    public String failureMessage() {
        if (stderr != null && !stderr.isBlank()) {
            return stderr.trim();
        }
        return "退出码非0: " + exitCode;
    }
}
