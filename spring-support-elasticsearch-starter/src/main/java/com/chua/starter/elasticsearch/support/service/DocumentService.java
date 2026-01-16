package com.chua.starter.elasticsearch.support.service;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.elasticsearch.support.pojo.Mapping;

/**
 * 文档服务
 *
 * @author CH
 */
public interface DocumentService {


    /**
     * 保存文档
     *
     * @param indexName 索引
     * @param document  文档
     * @return 状态
     */
    boolean addDocument(String indexName, Object... document);

    /**
     * 删除文档
     *
     * @param indexName 索引
     * @param did       文档ID
     * @return 状态
     */
    boolean deleteDocument(String indexName, Object[] did);

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
    <T> PageResult<T> queryDocument(String indexName, String queries, Class<T> target, int page, int pageSize);

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
    <T> PageResult<T> queryDocument(float[] feature, String indexName, String queries, Class<T> target, int page, int pageSize);

    /**
     * 底库刪除
     *
     * @param indexName 名称
     * @param code      唯一编码
     * @return 结果
     */
    boolean deleteDocument(String indexName, String code);

    /**
     * 创建索引
     *
     * @param indexName 索引
     * @return 结果
     */
    ReturnResult<String> createIndex(String indexName);

    /**
     * 检查索引(不存在则创建)
     *
     * @param indexName 索引
     */
    void checkIndex(String indexName);

    /**
     * 检查索引是否存在
     *
     * @param indexName 索引
     * @return 检查索引是否存在
     */
    boolean existIndex(String indexName);

    /**
     * 删除索引
     *
     * @param indexName 索引
     * @return 结果
     */
    ReturnResult<String> deleteIndex(String indexName);

    /**
     * 创建映射
     *
     * @param mapping   映射
     * @return 结果
     */
    ReturnResult<String> createMapping(Mapping mapping);

    /**
     * 添加映射
     *
     * @param mapping   映射
     * @return 添加映射
     */
    ReturnResult<String> addMapping(Mapping mapping);
}
