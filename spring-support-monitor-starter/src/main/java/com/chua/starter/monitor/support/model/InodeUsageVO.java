package com.chua.starter.monitor.support.model;

/**
 * 根文件系统 inode 使用情况。
 *
 * <p>「磁盘没满但 inode 先耗尽」是 Linux 上常见的写失败原因，
 * 与容量使用率是两套口径，不能互相替代。</p>
 *
 * @param used    已用 inode 数
 * @param total   总 inode 数
 * @param percent 使用率（0-100）
 * @param mount   统计的挂载点，通常为 /
 * @author CH
 * @since 4.0.0.51
 */
public record InodeUsageVO(long used, long total, double percent, String mount) {

    /**
     * 取不到时的占位：三个数值都是 -1，前端显示「—」。
     *
     * @return 占位对象
     */
    public static InodeUsageVO unknown() {
        return new InodeUsageVO(-1L, -1L, -1.0, "");
    }

    /**
     * 是否取到了有效数据。
     *
     * @return 有效返回 true
     */
    public boolean available() {
        return total > 0;
    }
}
