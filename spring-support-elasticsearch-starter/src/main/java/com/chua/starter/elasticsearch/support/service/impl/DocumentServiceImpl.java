package com.chua.starter.elasticsearch.support.service.impl;

import com.chua.common.support.bean.BeanUtils;
import com.chua.starter.common.support.result.PageResult;
import com.chua.starter.elasticsearch.support.service.DocumentService;
import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.io.IOException;
import java.util.*;

/**
 * 文档服务
 * @author CH
 */
@AllArgsConstructor(onConstructor_ = @Autowired)
public class DocumentServiceImpl implements DocumentService {

    private final ElasticsearchRestTemplate elasticsearchRestTemplate;
    private final RestHighLevelClient restHighLevelClient;

    /**
     * 检查人脸索引
     */
    @Override
    public void checkIndex(String indexName) {
        IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName.toLowerCase());
        if (!elasticsearchRestTemplate.indexOps(indexCoordinates).exists()) {
            elasticsearchRestTemplate.indexOps(indexCoordinates).create();
        }
    }

    @Override
    public boolean saveIndexDocument(String indexName, Object[] document) {
        try {
            elasticsearchRestTemplate.save(Arrays.asList(document), IndexCoordinates.of(indexName.toLowerCase()));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public <T> PageResult<T> listDocument(String indexName, String queries, Class<T> target, int page, int pageSize) {
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
                .page(page)
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
    public <T> PageResult<T> listDocument(float[] feature, String indexName, String queries, Class<T> target, int page, int pageSize) {
        checkIndex(indexName);
        if (Strings.isNullOrEmpty(queries)) {
            queries = "*:*";
        }
//        Script script = new Script(
//                ScriptType.INLINE,
//                "painless",
//                "cosineSimilarity(params.queryVector, 'feature')",
//                Collections.singletonMap("queryVector", feature));
//        NativeSearchQuery query = new NativeSearchQueryBuilder()
//                .withQuery(QueryBuilders.scriptQuery(script))
//                .withPageable(PageRequest.of(page - 1, pageSize))
//                .withHighlightBuilder(new HighlightBuilder().field("name").preTags("<font color='#dd4b39'>").postTags("</font>"))
//                .build();

        SearchRequest searchRequest = new SearchRequest(indexName);
        Script script = new Script(
                ScriptType.INLINE,
                "painless",
                "cosineSimilarity(params.queryVector, doc['feature'])",
                Collections.singletonMap("queryVector", feature));

        FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
                QueryBuilders.matchAllQuery(),
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
        long totalHits = hits.getTotalHits().value;;
        if (totalHits <= 0) {
            return PageResult.empty();
        }
        PageResult.PageResultBuilder<T> builder = PageResult.builder();
        builder.total(totalHits)
                .page(page)
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

}
