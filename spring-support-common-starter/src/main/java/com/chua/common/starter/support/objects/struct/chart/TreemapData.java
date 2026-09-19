package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 矩形树图数据
 *
 * @author CH
 * @since 4.0.0
 */
public class TreemapData implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /** 矩形块集合 */
    /** Blocks */
    private List<Block> blocks = new ArrayList<>();

    /**
     * 获取blocks。
     * @return 获取blocks的结果
     */
    public List<Block> getBlocks() {
        return blocks;
    }

    /**
     * 设置blocks。
     * @param blocks blocks
     */
    public void setBlocks(List<Block> blocks) {
        this.blocks = blocks;
    }

    /**
     * 矩形块
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Block implements Serializable {
        private static final long serialVersionUID = 1L; // 串行版本uid
        /** 名称 */
        private String name;
        /** 数值 */
        /** 值 */
        private Number value;
        /** 路径 */
        private String path;

        /**
         * Block。
         * @param name 名称
         * @param value 值
         * @param path 路径
         */
        public Block(String name, Number value, String path) {
            this.name = name;
            this.value = value;
            this.path = path;
        }

        /**
         * 获取名称。
         * @return 获取名称的结果
         */
        public String getName() {
            return name;
        }

        /**
         * 设置名称。
         * @param name 名称
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取值。
         * @return 获取值的结果
         */
        public Number getValue() {
            return value;
        }

        /**
         * 设置值。
         * @param value 值
         */
        public void setValue(Number value) {
            this.value = value;
        }

        /**
         * 获取路径。
         * @return 获取路径的结果
         */
        public String getPath() {
            return path;
        }

        /**
         * 设置路径。
         * @param path 路径
         */
        public void setPath(String path) {
            this.path = path;
        }
    }
}
