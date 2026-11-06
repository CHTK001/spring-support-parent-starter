package com.chua.starter.monitor.support.model;

/**
 * 已删除文件释放请求。
 *
 * @param pid 持有进程 ID
 * @param fd  文件描述符编号
 * @author CH
 * @since 4.0.0.51
 */
public record DeletedFileReleaseVO(long pid, int fd) {
}
