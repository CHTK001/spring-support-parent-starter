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
    private static final long serialVersionUID = 1L;

    /** 矩形块集合 */
    /** Blocks */
    private List<Block> blocks = new ArrayList<>();

    public List<Block> getBlocks() {
        return blocks;
    }

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
        /** 值 */
        private Number value;
        /** 路径 */
        private String path;

        public Block(String name, Number value, String path) {
            this.name = name;
            this.value = value;
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Number getValue() {
            return value;
        }

        public void setValue(Number value) {
            this.value = value;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}