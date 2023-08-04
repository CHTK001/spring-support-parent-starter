package com.chua.starter.elasticsearch.support.service;

import com.chua.starter.common.support.result.PageResult;

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
     * @param page      页码
     * @param pageSize  每页数量
     * @return 结果
     */
    <T> PageResult<T> listDocument(String indexName, String queries, Class<T> target, int page, int pageSize);

    /**
     * 特征值查询文档
     *
     * @param feature   特征值
     * @param indexName 名称
     * @param queries   查询器
     * @param target    返回类型
     * @param page      页码
     * @param pageSize  每页数量
     * @return 结果
     */
    <T> PageResult<T> listDocument(float[] feature, String indexName, String queries, Class<T> target, int page, int pageSize);
}
