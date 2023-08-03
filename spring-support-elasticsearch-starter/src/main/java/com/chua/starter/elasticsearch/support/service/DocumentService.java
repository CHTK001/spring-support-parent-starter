package com.chua.starter.elasticsearch.support.service;

import com.chua.common.support.query.FormQuery;

import java.util.List;

/**
 * 文档服务
 *
 * @author CH
 */
public interface DocumentService {


    /**
     * 检查人脸索引
     *
     * @param indexName 索引
     */
    void checkIndex(String indexName);

    /**
     * 保存文档
     *
     * @param indexName 索引
     * @param document  文档
     * @return 状态
     */
    boolean saveIndexDocument(String indexName, Object... document);

    /**
     * 查询文档
     *
     * @param indexName 名称
     * @param queries   查询器
     * @param target    返回类型
     * @return 结果
     */
    <T> List<T> listDocument(String indexName, List<FormQuery> queries, Class<T> target);
}
