# Spring Support Elasticsearch Starter

## 📖 模块简介

Spring Support Elasticsearch Starter 是一个功能强大的Elasticsearch集成模块，提供了企业级应用中全文搜索、数据分析、日志检索等功能的完整解决方案。该模块封装了Elasticsearch客户端操作，提供了简单易用的API接口和高级查询功能。

## ✨ 主要功能

### 🔍 全文搜索
- 多字段搜索
- 模糊匹配查询
- 高亮显示
- 搜索建议

### 📊 数据分析
- 聚合查询
- 统计分析
- 数据可视化支持
- 实时分析

### 📝 文档管理
- 文档CRUD操作
- 批量操作支持
- 文档版本控制
- 自动映射生成

### 🏗️ 索引管理
- 索引创建和删除
- 映射配置
- 别名管理
- 索引模板

### ⚡ 高性能查询
- 查询优化
- 缓存机制
- 分页查询
- 排序和过滤

## 🚀 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-elasticsearch-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 基础配置

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
    username: elastic
    password: password
    connection-timeout: 10s
    socket-timeout: 30s
    
plugin:
  elasticsearch:
    enable: true
    # 索引配置
    index:
      number-of-shards: 1
      number-of-replicas: 0
      refresh-interval: 1s
    # 查询配置
    query:
      default-size: 20
      max-size: 1000
      timeout: 30s
```

## 📋 详细功能说明

### 1. 文档操作

#### 实体类定义

```java
@Document(indexName = "products")
@Data
public class Product {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String name;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String description;
    
    @Field(type = FieldType.Keyword)
    private String category;
    
    @Field(type = FieldType.Double)
    private BigDecimal price;
    
    @Field(type = FieldType.Integer)
    private Integer stock;
    
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private LocalDateTime createTime;
    
    @Field(type = FieldType.Boolean)
    private Boolean active;
    
    @Field(type = FieldType.Nested)
    private List<ProductAttribute> attributes;
}
```

#### Repository接口

```java
@Repository
public interface ProductRepository extends ElasticsearchRepository<Product, String> {
    
    // 根据名称搜索
    List<Product> findByNameContaining(String name);
    
    // 根据分类查询
    List<Product> findByCategory(String category);
    
    // 价格范围查询
    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);
    
    // 复合查询
    @Query("{\"bool\": {\"must\": [{\"match\": {\"name\": \"?0\"}}, {\"range\": {\"price\": {\"gte\": ?1, \"lte\": ?2}}}]}}")
    List<Product> findByNameAndPriceRange(String name, BigDecimal minPrice, BigDecimal maxPrice);
}
```

### 2. 高级搜索

#### 搜索服务

```java
@Service
public class ProductSearchService {
    
    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;
    
    public SearchResponse<Product> searchProducts(ProductSearchRequest request) {
        // 构建查询条件
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        
        // 关键词搜索
        if (StringUtils.hasText(request.getKeyword())) {
            boolQuery.must(QueryBuilders.multiMatchQuery(request.getKeyword())
                .field("name", 2.0f)  // 名称权重更高
                .field("description", 1.0f)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS));
        }
        
        // 分类过滤
        if (StringUtils.hasText(request.getCategory())) {
            boolQuery.filter(QueryBuilders.termQuery("category", request.getCategory()));
        }
        
        // 价格范围过滤
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (request.getMinPrice() != null) {
                rangeQuery.gte(request.getMinPrice());
            }
            if (request.getMaxPrice() != null) {
                rangeQuery.lte(request.getMaxPrice());
            }
            boolQuery.filter(rangeQuery);
        }
        
        // 构建搜索查询
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(boolQuery)
            .withPageable(PageRequest.of(request.getPage(), request.getSize()))
            .withSort(SortBuilders.scoreSort().order(SortOrder.DESC))
            .withHighlightFields(
                new HighlightBuilder.Field("name").preTags("<em>").postTags("</em>"),
                new HighlightBuilder.Field("description").preTags("<em>").postTags("</em>")
            )
            .build();
        
        return elasticsearchTemplate.search(searchQuery, Product.class);
    }
}
```

### 3. 聚合查询

#### 统计分析服务

```java
@Service
public class ProductAnalyticsService {
    
    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;
    
    public Map<String, Object> getCategoryStatistics() {
        // 分类统计聚合
        TermsAggregationBuilder categoryAgg = AggregationBuilders
            .terms("category_stats")
            .field("category")
            .size(10);
        
        // 平均价格聚合
        AvgAggregationBuilder avgPriceAgg = AggregationBuilders
            .avg("avg_price")
            .field("price");
        
        categoryAgg.subAggregation(avgPriceAgg);
        
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(QueryBuilders.matchAllQuery())
            .addAggregation(categoryAgg)
            .withPageable(PageRequest.of(0, 0)) // 不需要返回文档
            .build();
        
        SearchHits<Product> searchHits = elasticsearchTemplate.search(searchQuery, Product.class);
        
        // 解析聚合结果
        Map<String, Object> result = new HashMap<>();
        Aggregations aggregations = searchHits.getAggregations();
        
        if (aggregations != null) {
            Terms categoryTerms = aggregations.get("category_stats");
            List<Map<String, Object>> categories = new ArrayList<>();
            
            for (Terms.Bucket bucket : categoryTerms.getBuckets()) {
                Map<String, Object> categoryData = new HashMap<>();
                categoryData.put("category", bucket.getKeyAsString());
                categoryData.put("count", bucket.getDocCount());
                
                Avg avgPrice = bucket.getAggregations().get("avg_price");
                categoryData.put("avgPrice", avgPrice.getValue());
                
                categories.add(categoryData);
            }
            
            result.put("categories", categories);
        }
        
        return result;
    }
    
    public Map<String, Object> getPriceRangeStatistics() {
        // 价格区间统计
        RangeAggregationBuilder priceRangeAgg = AggregationBuilders
            .range("price_ranges")
            .field("price")
            .addRange("0-100", 0, 100)
            .addRange("100-500", 100, 500)
            .addRange("500-1000", 500, 1000)
            .addRange("1000+", 1000, Double.MAX_VALUE);
        
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(QueryBuilders.matchAllQuery())
            .addAggregation(priceRangeAgg)
            .withPageable(PageRequest.of(0, 0))
            .build();
        
        SearchHits<Product> searchHits = elasticsearchTemplate.search(searchQuery, Product.class);
        
        Map<String, Object> result = new HashMap<>();
        Aggregations aggregations = searchHits.getAggregations();
        
        if (aggregations != null) {
            Range priceRanges = aggregations.get("price_ranges");
            List<Map<String, Object>> ranges = new ArrayList<>();
            
            for (Range.Bucket bucket : priceRanges.getBuckets()) {
                Map<String, Object> rangeData = new HashMap<>();
                rangeData.put("range", bucket.getKeyAsString());
                rangeData.put("count", bucket.getDocCount());
                ranges.add(rangeData);
            }
            
            result.put("priceRanges", ranges);
        }
        
        return result;
    }
}
```

### 4. 索引管理

#### 索引管理服务

```java
@Service
public class IndexManagementService {
    
    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;
    
    public boolean createIndex(String indexName, Class<?> entityClass) {
        try {
            IndexOperations indexOps = elasticsearchTemplate.indexOps(entityClass);
            
            if (!indexOps.exists()) {
                // 创建索引
                indexOps.create();
                
                // 创建映射
                Document mapping = indexOps.createMapping();
                indexOps.putMapping(mapping);
                
                log.info("索引创建成功: {}", indexName);
                return true;
            } else {
                log.warn("索引已存在: {}", indexName);
                return false;
            }
        } catch (Exception e) {
            log.error("创建索引失败: {}", indexName, e);
            return false;
        }
    }
    
    public boolean deleteIndex(String indexName) {
        try {
            IndexOperations indexOps = elasticsearchTemplate.indexOps(IndexCoordinates.of(indexName));
            
            if (indexOps.exists()) {
                indexOps.delete();
                log.info("索引删除成功: {}", indexName);
                return true;
            } else {
                log.warn("索引不存在: {}", indexName);
                return false;
            }
        } catch (Exception e) {
            log.error("删除索引失败: {}", indexName, e);
            return false;
        }
    }
    
    public Map<String, Object> getIndexInfo(String indexName) {
        try {
            IndexOperations indexOps = elasticsearchTemplate.indexOps(IndexCoordinates.of(indexName));
            
            Map<String, Object> info = new HashMap<>();
            info.put("exists", indexOps.exists());
            
            if (indexOps.exists()) {
                Settings settings = indexOps.getSettings();
                info.put("settings", settings.getAsMap());
                
                Document mapping = indexOps.getMapping();
                info.put("mapping", mapping);
            }
            
            return info;
        } catch (Exception e) {
            log.error("获取索引信息失败: {}", indexName, e);
            return Collections.emptyMap();
        }
    }
}
```

### 5. 批量操作

#### 批量操作服务

```java
@Service
public class BulkOperationService {
    
    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;
    
    public BulkResponse bulkIndex(List<Product> products) {
        List<IndexQuery> queries = products.stream()
            .map(product -> new IndexQueryBuilder()
                .withId(product.getId())
                .withObject(product)
                .build())
            .collect(Collectors.toList());
        
        return elasticsearchTemplate.bulkIndex(queries, IndexCoordinates.of("products"));
    }
    
    public BulkResponse bulkUpdate(List<Product> products) {
        List<UpdateQuery> queries = products.stream()
            .map(product -> UpdateQuery.builder(product.getId())
                .withDocument(Document.from(convertToMap(product)))
                .withDocAsUpsert(true)
                .build())
            .collect(Collectors.toList());
        
        return elasticsearchTemplate.bulkUpdate(queries, IndexCoordinates.of("products"));
    }
    
    public void bulkDelete(List<String> ids) {
        List<String> queries = ids.stream()
            .map(id -> new DeleteQuery.Builder(id).build())
            .collect(Collectors.toList());
        
        elasticsearchTemplate.delete(queries, IndexCoordinates.of("products"));
    }
    
    private Map<String, Object> convertToMap(Product product) {
        // 将Product对象转换为Map
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(product, Map.class);
    }
}
```

## ⚙️ 高级配置

### 完整配置示例

```yaml
spring:
  elasticsearch:
    uris: 
      - http://es-node1:9200
      - http://es-node2:9200
      - http://es-node3:9200
    username: elastic
    password: password
    connection-timeout: 10s
    socket-timeout: 30s
    
    # 连接池配置
    webclient:
      max-in-memory-size: 100MB
      
plugin:
  elasticsearch:
    enable: true
    
    # 索引配置
    index:
      number-of-shards: 3
      number-of-replicas: 1
      refresh-interval: 1s
      max-result-window: 10000
      
    # 查询配置
    query:
      default-size: 20
      max-size: 1000
      timeout: 30s
      track-total-hits: true
      
    # 高亮配置
    highlight:
      pre-tags: ["<mark>"]
      post-tags: ["</mark>"]
      fragment-size: 150
      number-of-fragments: 3
```

## 🔧 自定义扩展

### 自定义分析器

```java
@Configuration
public class ElasticsearchConfig {
    
    @Bean
    public ElasticsearchCustomConversions elasticsearchCustomConversions() {
        return new ElasticsearchCustomConversions(
            Arrays.asList(
                new LocalDateTimeToStringConverter(),
                new StringToLocalDateTimeConverter()
            )
        );
    }
    
    @WriteConverter
    static class LocalDateTimeToStringConverter implements Converter<LocalDateTime, String> {
        @Override
        public String convert(LocalDateTime source) {
            return source.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
    
    @ReadConverter
    static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {
        @Override
        public LocalDateTime convert(String source) {
            return LocalDateTime.parse(source, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}
```

### 自定义查询构建器

```java
@Component
public class CustomQueryBuilder {
    
    public QueryBuilder buildComplexQuery(SearchCriteria criteria) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        
        // 必须匹配条件
        if (criteria.getMustMatch() != null) {
            criteria.getMustMatch().forEach((field, value) -> 
                boolQuery.must(QueryBuilders.matchQuery(field, value)));
        }
        
        // 应该匹配条件
        if (criteria.getShouldMatch() != null) {
            criteria.getShouldMatch().forEach((field, value) -> 
                boolQuery.should(QueryBuilders.matchQuery(field, value)));
        }
        
        // 过滤条件
        if (criteria.getFilters() != null) {
            criteria.getFilters().forEach((field, value) -> 
                boolQuery.filter(QueryBuilders.termQuery(field, value)));
        }
        
        // 范围条件
        if (criteria.getRanges() != null) {
            criteria.getRanges().forEach((field, range) -> {
                RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(field);
                if (range.getFrom() != null) rangeQuery.gte(range.getFrom());
                if (range.getTo() != null) rangeQuery.lte(range.getTo());
                boolQuery.filter(rangeQuery);
            });
        }
        
        return boolQuery;
    }
}
```

## 📝 注意事项

1. **索引设计**：合理设计索引结构和映射，避免过度分片
2. **查询优化**：使用过滤器而非查询来提高性能
3. **内存管理**：大批量操作时注意内存使用
4. **版本兼容**：确保客户端版本与Elasticsearch服务器版本兼容
5. **安全配置**：生产环境建议启用认证和SSL

## 🐛 故障排除

### 常见问题

1. **连接失败**
   - 检查Elasticsearch服务状态
   - 验证网络连接和防火墙
   - 确认认证信息正确

2. **映射冲突**
   - 检查字段类型定义
   - 验证动态映射设置
   - 重建索引解决冲突

3. **查询性能问题**
   - 优化查询条件
   - 添加适当的过滤器
   - 检查索引分片配置

### 调试建议

启用调试日志：

```yaml
logging:
  level:
    org.springframework.data.elasticsearch: DEBUG
    org.elasticsearch.client: DEBUG
```
