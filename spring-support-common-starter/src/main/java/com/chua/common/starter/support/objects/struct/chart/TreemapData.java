package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 矩形树图数据
 *
 * @author CH
 */
public class TreemapData implements Serializable {

    /** 矩形块集合 */
    private List<Block> blocks = new ArrayList<>();

    /** 获取Blocks */
    public List<Block> getBlocks() {
        return blocks;
    }

    /** 设置Blocks */
    public void setBlocks(List<Block> blocks) {
        this.blocks = blocks;
    }

    /**
     * 矩形块
     *
     */
    public static class Block implements Serializable {
        /** 名称 */
        private String name;
        /** 数值 */
        private Number value;
        /** 路径 */
        private String path;

        /**
         * 创建 Block 实例
         * @param name name
         * @param Number Number
         * @param String String
         */
        public Block(String name, Number value, String path) {
            this.name = name;
            this.value = value;
            this.path = path;
        }

        /** 获取Name */
        public String getName() {
            return name;
        }

        /** 设置Name */
        public void setName(String name) {
            this.name = name;
        }

        /** 获取Value */
        public Number getValue() {
            return value;
        }

        /** 设置Value */
        public void setValue(Number value) {
            this.value = value;
        }

        /** 获取Path */
        public String getPath() {
            return path;
        }

        /** 设置Path */
        public void setPath(String path) {
            this.path = path;
        }
    }
}