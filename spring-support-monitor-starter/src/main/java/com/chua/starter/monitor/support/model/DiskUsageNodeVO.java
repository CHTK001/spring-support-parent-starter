package com.chua.starter.monitor.support.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 磁盘占用树节点（WizTree 风格空间分析）。
 *
 * <p>目录的 {@link #size} 为其下全部子孙的累计字节数；{@link #children} 只在目录且未到
 * 深度上限时存在，按 size 降序排列，便于矩形树图直接分块。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class DiskUsageNodeVO {

    /**
     * 名称
     */
    private String name;

    /**
     * 完整路径
     */
    private String path;

    /**
     * 占用字节（目录为累计值）
     */
    private long size;

    /**
     * 是否目录
     */
    private boolean directory;

    /**
     * 子节点（目录），按大小降序
     */
    private List<DiskUsageNodeVO> children;

    /**
     * 节点下（含子孙）文件总数
     */
    private long fileCount;

    /**
     * 节点下（含子孙）目录总数
     */
    private long dirCount;

    /**
     * 修改时间展示文本（文件为自身 mtime，目录取最近修改子项，通道未上报时为空）
     */
    private String modified;

    /**
     * 所在卷总容量（字节，仅根节点填充）
     */
    private long totalSpace;

    /**
     * 所在卷可用容量（字节，仅根节点填充）
     */
    private long freeSpace;

    /**
     * 追加一个子节点并保持数组非 null。
     *
     * @param node 子节点
     */
    public void addChild(DiskUsageNodeVO node) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(node);
    }
}
