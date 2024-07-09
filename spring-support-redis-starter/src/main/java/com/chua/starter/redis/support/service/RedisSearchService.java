package com.chua.starter.redis.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.redis.support.search.SearchIndex;
import com.chua.redis.support.search.SearchQuery;
import com.chua.redis.support.search.SearchResultItem;

import java.util.Map;

/**
 * RedisSearchService接口定义了与Redis搜索引擎交互的操作。
 * 该接口的实现负责执行针对Redis中的数据的搜索操作。
 *
 * @author CH
 * @since 2024/7/5
 */
public interface RedisSearchService {


    /**
     * 删除Redis搜索引擎索引。
     *
     * @param index     索引名称
     * @param expireTime 索引过期时间(MS)
     * @return 返回删除索引操作的结果
     */
    ReturnResult<Boolean> dropIndex(String index, long expireTime);
    /**
     * 创建Redis搜索引擎索引。
     *
     * @param searchIndex     索引的模式字段
     * @return 返回创建索引操作的结果
     */
    ReturnResult<Boolean> createIndex(SearchIndex searchIndex);
    /**
     * 添加文档到Redis搜索引擎。
     *
     * @param key       文档的键
     * @param document  要添加的文档内容
     * @return 返回添加操作的结果
     */
    ReturnResult<Boolean> addDocument(String key, Map<String, String> document);

    /**
     * 查询Redis搜索引擎中的文档。
     *
     * @param query     查询条件
     * @param offset    查询偏移量
     * @param limit     查询限制数量
     * @return 返回查询操作的结果
     */
    ReturnResult<SearchResultItem> queryAll(SearchQuery query, int offset, int limit);
}
