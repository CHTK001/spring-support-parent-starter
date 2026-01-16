package com.chua.starter.elasticsearch.support;

import com.chua.starter.elasticsearch.support.properties.ElasticSearchProperties;
import com.chua.starter.elasticsearch.support.service.impl.DocumentServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

/**
 * es
 *
 * @author CH
 */
@EnableConfigurationProperties(ElasticSearchProperties.class)
@Import(DocumentServiceImpl.class)
@RequiredArgsConstructor
public class ElasticSearchConfiguration {


    final ElasticSearchProperties elasticSearchProperties;

    @Bean
    @ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('${plugin.elasticsearch.address:}')")
    public RestHighLevelClient restHighLevelClient() {
        // 拆分地址
        List<HttpHost> hostLists = new ArrayList<>();
        String[] hostList = elasticSearchProperties.getAddress().split(",");
        for (String addr : hostList) {
            String host = addr.split(":")[0];
            String port = addr.split(":")[1];
            hostLists.add(new HttpHost(host, Integer.parseInt(port), elasticSearchProperties.getSchema()));
        }
        // 转换成 HttpHost 数组
        HttpHost[] httpHost = hostLists.toArray(new HttpHost[]{});
        // 构建连接对象
        RestClientBuilder builder = RestClient.builder(httpHost);
        // 异步连接延时配置
        builder.setRequestConfigCallback(requestConfigBuilder -> {
            requestConfigBuilder.setConnectTimeout(elasticSearchProperties.getConnectTimeoutMs());
            requestConfigBuilder.setSocketTimeout(elasticSearchProperties.getSocketTimeoutMs());
            requestConfigBuilder.setConnectionRequestTimeout(elasticSearchProperties.getConnectionRequestTimeoutMs());
            return requestConfigBuilder;
        });
        // 异步连接数配置
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            httpClientBuilder.setMaxConnTotal(elasticSearchProperties.getMaxConnectNum());
            httpClientBuilder.setMaxConnPerRoute(elasticSearchProperties.getMaxConnectPerRoute());
            return httpClientBuilder;
        });
        return new RestHighLevelClient(builder);
    }
}
