package com.chua.starter.elasticsearch.support.service.impl;

import com.chua.common.support.base.bean.BeanUtils;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.text.json.Json;
import com.chua.starter.elasticsearch.support.pojo.Mapping;
import com.chua.starter.elasticsearch.support.service.DocumentService;
import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.StringQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 文档服务实现
 * 提供 Elasticsearch 文档的增删改查功能
 *
 * @author CH
 * @since 2024-12-24
 */
@Slf4j
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    /**
     * Elasticsearch 操作模板
     */
    private final ElasticsearchTemplate elasticsearchTemplate;

    @Override
    public void checkIndex(String indexName) {
        if (!existIndex(indexName)) {
            elasticsearchTemplate.indexOps(IndexCoordinates.of(indexName.toLowerCase())).create();
        }
    }

    @Override
    public boolean addDocument(String indexName, Object[] document) {
        try {
            var indexCoordinates = IndexCoordinates.of(indexName.toLowerCase());
            if (!existIndex(indexCoordinates)) {
                log.warn("[ES][文档] 索引[{}]不存在", indexName);
                return false;
            }
            elasticsearchTemplate.save(Arrays.asList(document), indexCoordinates);
            return true;
        } catch (Exception e) {
            log.error("[ES][文档] 添加文档失败, index={}", indexName, e);
            return false;
        }
    }

    @Override
    public boolean deleteDocument(String indexName, Object[] did) {
        try {
            var indexCoordinates = IndexCoordinates.of(indexName.toLowerCase());
            if (!existIndex(indexCoordinates)) {
                log.warn("[ES][文档] 索引[{}]不存在", indexName);
                return false;
            }
            for (Object o : did) {
                elasticsearchTemplate.delete(o, indexCoordinates);
            }
            return true;
        } catch (Exception e) {
            log.error("[ES][文档] 删除文档失败, index={}", indexName, e);
            return false;
        }
    }

    @Override
    public <T> PageResult<T> queryDocument(String indexName, String queries, Class<T> target, int page, int pageSize) {
        checkIndex(indexName);
        if (Strings.isNullOrEmpty(queries)) {
            queries = "*:*";
        }

        // 使用 StringQuery 构建查询
        var queryString = String.format("""
                {
                    "query_string": {
                        "query": "%s"
                    }
                }
                """, queries.replace("\"", "\\\""));

        Query query = new StringQuery(queryString);
        query.setPageable(PageRequest.of(page - 1, pageSize));

        var indexCoordinates = IndexCoordinates.of(indexName.toLowerCase());
        SearchHits<T> searchHits = elasticsearchTemplate.search(query, target, indexCoordinates);

        return buildPageResult(searchHits, page, pageSize);
    }

    @Override
    public <T> PageResult<T> queryDocument(float[] feature, String indexName, String queries, Class<T> target, int page, int pageSize) {
        checkIndex(indexName);
        if (Strings.isNullOrEmpty(queries)) {
            queries = "*:*";
        }

        // 构建带脚本评分的查询
        var featureArray = new StringBuilder("[");
        for (int i = 0; i < feature.length; i++) {
            if (i > 0) {
                featureArray.append(",");
            }
            featureArray.append(feature[i]);
        }
        featureArray.append("]");

        var queryJson = String.format("""
                {
                    "function_score": {
                        "query": {
                            "query_string": {
                                "query": "%s"
                            }
                        },
                        "script_score": {
                            "script": {
                                "source": "(cosineSimilarity(params.queryVector, doc['feature']) + 1)/2",
                                "params": {
                                    "queryVector": %s
                                }
                            }
                        }
                    }
                }
                """, queries.replace("\"", "\\\""), featureArray);

        Query query = new StringQuery(queryJson);
        query.setPageable(PageRequest.of(page - 1, pageSize));

        var indexCoordinates = IndexCoordinates.of(indexName.toLowerCase());
        SearchHits<T> searchHits = elasticsearchTemplate.search(query, target, indexCoordinates);

        return buildPageResultWithScore(searchHits, page, pageSize, target);
    }

    @Override
    public boolean deleteDocument(String indexName, String code) {
        var result = elasticsearchTemplate.delete(code, IndexCoordinates.of(indexName.toLowerCase()));
        return !Strings.isNullOrEmpty(result);
    }

    @Override
    public ReturnResult<String> createIndex(String indexName) {
        var coordinates = IndexCoordinates.of(indexName.toLowerCase());
        IndexOperations indexOps = elasticsearchTemplate.indexOps(coordinates);
        if (indexOps.exists()) {
            return ReturnResult.failure("索引已存在");
        }
        return indexOps.create() ? ReturnResult.success() : ReturnResult.failure("创建失败");
    }

    @Override
    public boolean existIndex(String indexName) {
        return existIndex(IndexCoordinates.of(indexName.toLowerCase()));
    }

    @Override
    public ReturnResult<String> deleteIndex(String indexName) {
        var coordinates = IndexCoordinates.of(indexName.toLowerCase());
        IndexOperations indexOps = elasticsearchTemplate.indexOps(coordinates);
        if (!indexOps.exists()) {
            return ReturnResult.failure("索引不存在");
        }
        return indexOps.delete() ? ReturnResult.success() : ReturnResult.failure("删除失败");
    }

    @Override
    public ReturnResult<String> createMapping(Mapping mapping) {
        if (!existIndex(mapping.getIndexName())) {
            if (!mapping.isOverIndex()) {
                return ReturnResult.failure("索引不存在");
            }
            checkIndex(mapping.getIndexName());
        }

        try {
            var coordinates = IndexCoordinates.of(mapping.getIndexName().toLowerCase());
            IndexOperations indexOps = elasticsearchTemplate.indexOps(coordinates);

            // 解析 mapping 并应用
            var mappingJson = Json.fromJson(mapping.getMapping());
            indexOps.putMapping(Document.from(mappingJson));
            return ReturnResult.success("更新成功");
        } catch (Exception e) {
            log.error("[ES][映射] 创建映射失败, index={}", mapping.getIndexName(), e);
            return ReturnResult.failure("更新失败");
        }
    }

    @Override
    public ReturnResult<String> addMapping(Mapping mapping) {
        return createMapping(mapping);
    }

    /**
     * 检查索引是否存在
     *
     * @param coordinates 索引坐标
     * @return true 存在, false 不存在
     */
    private boolean existIndex(IndexCoordinates coordinates) {
        return elasticsearchTemplate.indexOps(coordinates).exists();
    }

    /**
     * 构建分页结果
     *
     * @param searchHits 搜索结果
     * @param page       页码
     * @param pageSize   每页大小
     * @param <T>        结果类型
     * @return 分页结果
     */
    private <T> PageResult<T> buildPageResult(SearchHits<T> searchHits, int page, int pageSize) {
        long totalHits = searchHits.getTotalHits();
        if (totalHits <= 0) {
            return PageResult.empty();
        }

        List<T> dataList = new ArrayList<>((int) Math.min(totalHits, pageSize));
        for (SearchHit<T> hit : searchHits) {
            dataList.add(hit.getContent());
        }

        return PageResult.<T>builder()
                .total(totalHits)
                .pageNo(page)
                .pageSize(pageSize)
                .data(dataList)
                .build();
    }

    /**
     * 构建带评分的分页结果
     *
     * @param searchHits 搜索结果
     * @param page       页码
     * @param pageSize   每页大小
     * @param target     目标类型
     * @param <T>        结果类型
     * @return 分页结果
     */
    private <T> PageResult<T> buildPageResultWithScore(SearchHits<T> searchHits, int page, int pageSize, Class<T> target) {
        long totalHits = searchHits.getTotalHits();
        if (totalHits <= 0) {
            return PageResult.empty();
        }

        List<T> dataList = new ArrayList<>((int) Math.min(totalHits, pageSize));
        for (SearchHit<T> hit : searchHits) {
            T content = hit.getContent();
            // 将评分添加到结果中
            var map = BeanUtils.copyProperties(content, Map.class);
            if (map != null) {
                map.put("similarities", hit.getScore());
                content = BeanUtils.copyProperties(map, target);
            }
            dataList.add(content);
        }

        return PageResult.<T>builder()
                .total(totalHits)
                .pageNo(page)
                .pageSize(pageSize)
                .data(dataList)
                .build();
    }
}
