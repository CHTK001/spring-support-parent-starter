package com.chua.starter.elasticsearch.support.pojo;

import lombok.Data;

/**
 * 映射
 * @author CH
 */
@Data
public class Mapping {
    /**
     * 索引
     */
    private String indexName;
    /**
     * 索引不存在是否先创建索引
     */
    private boolean overIndex;
    /**
     * 映射
     */
    private String mapping;

    // Lombok @Data 生成的 getter/setter 方法（如果 Lombok 未生效，这些方法会被使用）
    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public boolean isOverIndex() {
        return overIndex;
    }

    public void setOverIndex(boolean overIndex) {
        this.overIndex = overIndex;
    }

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }
}
