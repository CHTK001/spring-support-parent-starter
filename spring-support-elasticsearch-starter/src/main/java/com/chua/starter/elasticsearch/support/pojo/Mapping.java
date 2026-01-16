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
}
