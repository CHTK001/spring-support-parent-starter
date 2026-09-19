package com.chua.starter.monitor.support.controller;

/**
 * 文件管理命令执行请求体。
 *
 * <p>由 {@link SystemManageController#filesExec} 接收，承载新建/重命名/删除/解压等
 * 文件操作需要的命令行与工作目录。</p>
 *
 * @param hostId 目标主机标识，可为 null（管控机本机）
 * @param cmd    待执行命令行，必填
 * @param cwd    工作目录，可为 null（目标主机默认目录）
 * @since 4.0.0.42
 * @author CH
 */
public record FileExecRequest(Long hostId, String cmd, String cwd) {
}
