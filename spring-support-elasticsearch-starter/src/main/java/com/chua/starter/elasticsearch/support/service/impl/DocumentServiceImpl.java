package com.chua.starter.elasticsearch.support.service.impl;

import com.chua.common.support.query.FormQuery;
import com.chua.starter.elasticsearch.support.service.DocumentService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.StringQuery;

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
            elasticsearchRestTemplate.save(document, IndexCoordinates.of(indexName.toLowerCase()));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public <T> List<T> listDocument(String indexName, List<FormQuery> queries, Class<T> target) {
        checkIndex(indexName);
        elasticsearchRestTemplate.search(new StringQuery(""), target, IndexCoordinates.of(indexName.toLowerCase()));
        return null;
    }
}
