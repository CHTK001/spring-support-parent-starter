package com.chua.starter.elasticsearch.support;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.chua.starter.elasticsearch.support.properties.ElasticSearchProperties;
import com.chua.starter.elasticsearch.support.service.DocumentService;
import com.chua.starter.elasticsearch.support.service.impl.DocumentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 自动配置类
 * 提供 Elasticsearch 客户端和模板的自动配置
 *
 * @author CH
 * @since 2024-12-24
 */
@Slf4j
@EnableConfigurationProperties(ElasticSearchProperties.class)
public class ElasticSearchConfiguration {

    private final ElasticSearchProperties elasticSearchProperties;

    /**
     * 构造器
     *
     * @param elasticSearchProperties ES 配置属性
     */
    public ElasticSearchConfiguration(ElasticSearchProperties elasticSearchProperties) {
        this.elasticSearchProperties = elasticSearchProperties;
    }

    /**
     * 创建 RestClient
     *
     * @return RestClient 实例
     */
    @Bean
    @ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('${plugin.elasticsearch.address:}')")
    @ConditionalOnMissingBean
    public RestClient elasticsearchRestClient() {
        List<HttpHost> hostList = new ArrayList<>();
        String[] addresses = elasticSearchProperties.getAddress().split(",");
        for (String addr : addresses) {
            String[] parts = addr.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            hostList.add(new HttpHost(host, port, elasticSearchProperties.getSchema()));
        }

        HttpHost[] httpHosts = hostList.toArray(new HttpHost[0]);
        RestClientBuilder builder = RestClient.builder(httpHosts);

        // 配置连接超时
        builder.setRequestConfigCallback(requestConfigBuilder -> {
            requestConfigBuilder.setConnectTimeout(elasticSearchProperties.getConnectTimeoutMs());
            requestConfigBuilder.setSocketTimeout(elasticSearchProperties.getSocketTimeoutMs());
            requestConfigBuilder.setConnectionRequestTimeout(elasticSearchProperties.getConnectionRequestTimeoutMs());
            return requestConfigBuilder;
        });

        // 配置连接池
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            httpClientBuilder.setMaxConnTotal(elasticSearchProperties.getMaxConnectNum());
            httpClientBuilder.setMaxConnPerRoute(elasticSearchProperties.getMaxConnectPerRoute());
            return httpClientBuilder;
        });

        log.info("[ES][配置] 创建 RestClient, address={}", elasticSearchProperties.getAddress());
        return builder.build();
    }

    /**
     * 创建 ElasticsearchTransport
     *
     * @param restClient RestClient
     * @return ElasticsearchTransport 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    /**
     * 创建 ElasticsearchClient
     *
     * @param transport ElasticsearchTransport
     * @return ElasticsearchClient 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        log.info("[ES][配置] 创建 ElasticsearchClient");
        return new ElasticsearchClient(transport);
    }

    /**
     * 创建 ElasticsearchTemplate
     *
     * @param elasticsearchClient ElasticsearchClient
     * @return ElasticsearchTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate elasticsearchTemplate(
            ElasticsearchClient elasticsearchClient) {
        log.info("[ES][配置] 创建 ElasticsearchTemplate");
        return new org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate(elasticsearchClient);
    }

    /**
     * 创建文档服务
     *
     * @param elasticsearchTemplate ElasticsearchTemplate
     * @return DocumentService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DocumentService documentService(
            org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate elasticsearchTemplate) {
        return new DocumentServiceImpl(elasticsearchTemplate);
    }
}
