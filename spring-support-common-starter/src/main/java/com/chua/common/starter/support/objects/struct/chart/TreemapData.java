package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.List;

/**
 * 矩形树图数据
 *
 * @param blocks 矩形块集合
 * @author CH
 * @since 4.0.0
 */
public record TreemapData(
        List<Block> blocks) implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 矩形块
     *
     * @param name  名称
     * @param value 数值
     * @param path  路径
     * @author CH
     * @since 4.0.0
     */
    public record Block(
            String name,
            Number value,
            String path) implements Serializable {

        /** 串行版本UID */
        private static final long serialVersionUID = 1L;
    }
}
