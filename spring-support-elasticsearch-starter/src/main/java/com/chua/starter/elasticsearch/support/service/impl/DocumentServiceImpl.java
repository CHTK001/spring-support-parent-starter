package com.chua.starter.elasticsearch.support.service.impl;

import com.chua.starter.common.support.result.PageResult;
import com.chua.starter.elasticsearch.support.service.DocumentService;
import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文档服务
 * @author CH
 */
@AllArgsConstructor(onConstructor_ = @Autowired)
public class DocumentServiceImpl implements DocumentService {

    private final ElasticsearchRestTemplate elasticsearchRestTemplate;

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
                .withPageable(PageRequest.of(page, pageSize))
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
            // 处理高亮
//            Map<String, List<String>> highlightFields = tSearchHit.getHighlightFields();
//            for (Map.Entry<String, List<String>> stringHighlightFieldEntry : highlightFields.entrySet()) {
//                String key = stringHighlightFieldEntry.getKey();
//                if (StringUtils.equals(key, "questionContent")) {
//                    List<String> fragments = stringHighlightFieldEntry.getValue();
//                    StringBuilder sb = new StringBuilder();
//                    for (String fragment : fragments) {
//                        sb.append(fragment);
//                    }
//                    content.setQuestionContent(sb.toString());
//                }
//                if (StringUtils.equals(key, "options")) {
//                    List<String> fragments = stringHighlightFieldEntry.getValue();
//                    StringBuilder sb = new StringBuilder();
//                    for (String fragment : fragments) {
//                        sb.append(fragment);
//                    }
//                    content.setOptions(sb.toString());
//                }
//            }
            searchAnswerList.add(content);
        }
        builder.data(searchAnswerList);
        return builder.build();
    }
}
