package com.chua.starter.monitor.support.model;

/**
 * 已删除但仍被进程占用的文件（deleted-but-open）。
 *
 * <p>文件已被 unlink，目录项不存在，但进程仍持有文件描述符，磁盘块不会释放。
 * 本视图用于定位「删了文件磁盘没降」：给出持有进程、父进程、属主、fd 与原始大小。</p>
 *
 * @param pid        持有进程 ID
 * @param ppid       父进程 ID
 * @param process    进程名
 * @param user       进程属主
 * @param fd         文件描述符编号
 * @param path       原始文件路径（已去掉内核追加的 " (deleted)" 后缀）
 * @param sizeBytes  当前仍占用的大小（字节）
 * @param sizeText   格式化的容量文本
 * @author CH
 * @since 4.0.0.51
 */
public record DeletedFileRowVO(
        long pid,
        long ppid,
        String process,
        String user,
        int fd,
        String path,
        long sizeBytes,
        String sizeText) {
}
