package com.chua.starter.elasticsearch.support.service.impl;

import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.elasticsearch.support.pojo.Mapping;
import com.chua.starter.elasticsearch.support.service.DocumentService;
import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.io.IOException;
import java.util.*;

/**
 * 文档服务
 *
 * @author CH
 */
@Slf4j
@AllArgsConstructor(onConstructor_ = @Autowired)
public class DocumentServiceImpl implements DocumentService {

    private final ElasticsearchRestTemplate elasticsearchRestTemplate;
    private final RestHighLevelClient restHighLevelClient;

    /**
     * 检查人脸索引
     */
    @Override
    public void checkIndex(String indexName) {
        if(!existIndex(indexName)) {
            elasticsearchRestTemplate.indexOps(IndexCoordinates.of(indexName.toLowerCase())).create();
        }
    }

    @Override
    public boolean addDocument(String indexName, Object[] document) {
        try {
            IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName.toLowerCase());
            if(!existIndex(indexCoordinates)) {
                log.warn("索引[{}]不存在", indexName);
                return false;
            }
            elasticsearchRestTemplate.save(Arrays.asList(document), IndexCoordinates.of(indexName.toLowerCase()));
        } catch (Exception e) {
            log.error("", e);
            return false;
        }
        return true;
    }


    @Override
    public boolean deleteDocument(String indexName, Object[] did) {
        try {
            IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName.toLowerCase());
            if(!existIndex(indexCoordinates)) {
                log.warn("索引[{}]不存在", indexName);
                return false;
            }
            for (Object o : did) {
                elasticsearchRestTemplate.delete(o, indexCoordinates);
            }
        } catch (Exception e) {
            log.error("", e);
            return false;
        }
        return true;
    }

    @Override
    public <T> PageResult<T> queryDocument(String indexName, String queries, Class<T> target, int page, int pageSize) {
        checkIndex(indexName);
        if (Strings.isNullOrEmpty(queries)) {
            queries = "*:*";
        }
        NativeSearchQuery query = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.queryStringQuery(queries))
                .withPageable(PageRequest.of(page - 1, pageSize))
                .withHighlightBuilder(new HighlightBuilder().field("name").preTags("<font color='#dd4b39'>").postTags("</font>"))
                .build();
        SearchHits<T> search = elasticsearchRestTemplate.search(query, target, IndexCoordinates.of(indexName.toLowerCase()));
        long totalHits = search.getTotalHits();
        if (totalHits <= 0) {
            return PageResult.empty();
        }
        PageResult.PageResultBuilder<T> builder = PageResult.builder();
        builder.total(totalHits)
                .pageNo(page)
                .pageSize(pageSize);
        List<T> searchAnswerList = new ArrayList((int) search.getTotalHits());
        for (SearchHit<T> tSearchHit : search) {
            T content = tSearchHit.getContent();
            searchAnswerList.add(content);
        }
        builder.data(searchAnswerList);
        return builder.build();
    }

    @Override
    public <T> PageResult<T> queryDocument(float[] feature, String indexName, String queries, Class<T> target, int page, int pageSize) {
        checkIndex(indexName);
        if (Strings.isNullOrEmpty(queries)) {
            queries = "*:*";
        }

        SearchRequest searchRequest = new SearchRequest(indexName);
        Script script = new Script(
                ScriptType.INLINE,
                "painless",
                "(cosineSimilarity(params.queryVector, doc['feature']) + 1)/2",
                Collections.singletonMap("queryVector", feature));

        FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
                QueryBuilders.queryStringQuery(queries),
                ScoreFunctionBuilders.scriptFunction(script));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(functionScoreQueryBuilder)
                .fetchSource(null, "feature") //不返回vector字段，太多了没用还耗时
                .size(pageSize);

        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = null;
        try {
            searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException ignored) {
        }
        org.elasticsearch.search.SearchHits hits = searchResponse.getHits();
        long totalHits = hits.getTotalHits().value;
        ;
        if (totalHits <= 0) {
            return PageResult.empty();
        }
        PageResult.PageResultBuilder<T> builder = PageResult.builder();
        builder.total(totalHits)
                .pageNo(page)
                .pageSize(pageSize);
        List<T> searchAnswerList = new ArrayList((int) totalHits);
        for (org.elasticsearch.search.SearchHit tSearchHit : hits.getHits()) {
            Map<String, Object> rs = tSearchHit.getSourceAsMap();
            rs.put("similarities", tSearchHit.getScore());
            T content = BeanUtils.copyProperties(rs, target);
            searchAnswerList.add(content);
        }
        builder.data(searchAnswerList);
        return builder.build();
    }

    @Override
    public boolean deleteDocument(String indexName, String code) {
        String code1 = elasticsearchRestTemplate.delete(code, IndexCoordinates.of(indexName.toLowerCase()));
        return !Strings.isNullOrEmpty(code1);
    }

    @Override
    public ReturnResult<String> createIndex(String indexName) {
        IndexCoordinates coordinates = IndexCoordinates.of(indexName.toLowerCase());
        IndexOperations indexOperations = elasticsearchRestTemplate.indexOps(coordinates);
        if (indexOperations.exists()) {
            return ReturnResult.failure("索引已存在");
        }
        return indexOperations.create() ? ReturnResult.success() : ReturnResult.failure("创建失败");
    }

    public boolean existIndex(IndexCoordinates coordinates) {
        IndexOperations indexOperations = elasticsearchRestTemplate.indexOps(coordinates);
        return indexOperations.exists();
    }
    @Override
    public boolean existIndex(String indexName) {
        return existIndex(IndexCoordinates.of(indexName.toLowerCase()));
    }

    @Override
    public ReturnResult<String> deleteIndex(String indexName) {
        IndexCoordinates coordinates = IndexCoordinates.of(indexName.toLowerCase());
        IndexOperations indexOperations = elasticsearchRestTemplate.indexOps(coordinates);
        if (!indexOperations.exists()) {
            return ReturnResult.failure("索引不存在");
        }
        return indexOperations.delete() ? ReturnResult.success() : ReturnResult.failure("删除失败");
    }

    @Override
    public ReturnResult<String> createMapping(Mapping mapping) {
        if (!existIndex(mapping.getIndexName())) {
            if(!mapping.isOverIndex()) {
                return ReturnResult.failure("索引不存在");
            }
            checkIndex(mapping.getIndexName());
        }
        PutMappingRequest request = new PutMappingRequest(mapping.getIndexName().toLowerCase());
        try {
            request.source(Json.getJsonObject(mapping.getMapping()));
            restHighLevelClient.indices().putMapping(request, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error("", e);
            return ReturnResult.failure("更新失败");
        }
        return ReturnResult.success("更新成功");
    }

    @Override
    public ReturnResult<String> addMapping(Mapping mapping) {
        return createMapping( mapping);
    }

    private void doBuilder(XContentBuilder builder, Object mapping) throws Exception{
        JsonObject jsonObject = null;
        if(mapping instanceof JsonObject) {
            jsonObject = (JsonObject) mapping;
        } else if(mapping instanceof String ) {
            String str = mapping.toString();
            if(str.startsWith("{")) {
                jsonObject = Json.getJsonObject(mapping.toString());
                builder.map(jsonObject);
                return ;
            }
        }
        if(null == jsonObject) {
            return;
        }

        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            builder.startObject(k);
            doBuilder(builder, v);
            builder.endObject();
        }
    }
}
