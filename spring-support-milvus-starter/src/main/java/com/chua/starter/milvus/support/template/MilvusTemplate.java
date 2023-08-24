package com.chua.starter.milvus.support.template;

import com.chua.common.support.net.NetAddress;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.milvus.support.properties.MilvusProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.*;
import io.milvus.param.partition.CreatePartitionParam;
import io.milvus.param.partition.HasPartitionParam;
import io.milvus.param.partition.ReleasePartitionsParam;
import io.milvus.param.partition.ShowPartitionsParam;
import io.milvus.response.DescCollResponseWrapper;
import io.milvus.response.GetCollStatResponseWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * minio
 *
 * @author CH
 */
@Slf4j
public class MilvusTemplate implements InitializingBean {

    public final MilvusProperties milvusProperties;
    public MilvusServiceClient milvusServiceClient;

    public MilvusTemplate(MilvusProperties milvusProperties) {
        this.milvusProperties = milvusProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        NetAddress netAddress = NetAddress.of(milvusProperties.getAddress());
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(netAddress.getHost())
                .withPort(netAddress.getPort(19530));

        if (StringUtils.isNotEmpty(milvusProperties.getUsername())) {
            builder.withAuthorization(milvusProperties.getUsername(), milvusProperties.getPassword());
        }
        ConnectParam connectParam = builder.build();
        this.milvusServiceClient = new MilvusServiceClient(connectParam);
    }

    /**
     * 建库
     *
     * @param createCollectionParam 建库参数
     * @param timeoutMilliseconds   超时时间(s)
     * @return 结果
     */
    public R<RpcStatus> createCollection(CreateCollectionParam createCollectionParam, long timeoutMilliseconds) {
        return milvusServiceClient.withTimeout(timeoutMilliseconds, TimeUnit.MILLISECONDS)
                .createCollection(createCollectionParam);
    }

    /**
     * 建库
     *
     * @param collectionName      库名
     * @param description         描述
     * @param shardsNum           分片
     * @param timeoutMilliseconds 超时时间(s)
     * @return 结果
     */
    public R<RpcStatus> createCollection(String collectionName, String description, int shardsNum, List<FieldType> fieldTypeList, long timeoutMilliseconds) {
        CreateCollectionParam.Builder builder = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription(description)
                .withShardsNum(shardsNum);
        for (FieldType fieldType : fieldTypeList) {
            builder.addFieldType(fieldType);
        }
        return createCollection(builder.build(), timeoutMilliseconds);
    }
    /**
     * 建库
     *
     * @param description         描述
     * @param shardsNum           分片
     * @param timeoutMilliseconds 超时时间(s)
     * @return 结果
     */
    public R<RpcStatus> createCollection(String description, int shardsNum, List<FieldType> fieldTypeList, long timeoutMilliseconds) {
        return createCollection(milvusProperties.getCollect(), description, shardsNum, fieldTypeList, timeoutMilliseconds);
    }

    /**
     * 删库
     *
     * @param collectionName 库名
     * @return 结果
     */
    public R<RpcStatus> dropCollection(String collectionName) {
        return milvusServiceClient.dropCollection(DropCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
    }

    /**
     * 删库
     *
     * @return 结果
     */
    public R<RpcStatus> dropCollection() {
        return milvusServiceClient.dropCollection(DropCollectionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .build());
    }

    /**
     * 数据库是否存在
     *
     * @param collectionName 库名
     * @return 结果
     */
    public Boolean hasCollection(String collectionName) {
        return milvusServiceClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build()).getData();
    }

    /**
     * 加载数据库
     *
     * @param collectionName 库名
     * @return 结果
     */
    public R<RpcStatus> loadCollection(String collectionName) {
        return milvusServiceClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
    }

    /**
     * 释放数据库
     *
     * @param collectionName 库名
     * @return 结果
     */
    public R<RpcStatus> releaseCollection(String collectionName) {
        return milvusServiceClient.releaseCollection(ReleaseCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
    }
    /**
     * 释放数据库
     *
     * @return 结果
     */
    public R<RpcStatus> releaseCollection() {
        return milvusServiceClient.releaseCollection(ReleaseCollectionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .build());
    }

    /**
     * 数据库描述
     *
     * @param collectionName 库名
     * @return 结果
     */
    public String describeCollection(String collectionName) {
        R<DescribeCollectionResponse> response = milvusServiceClient.describeCollection(DescribeCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
        DescCollResponseWrapper wrapper = new DescCollResponseWrapper(response.getData());
        return wrapper.toString();
    }
    /**
     * 数据库描述
     *
     * @return 结果
     */
    public String describeCollection() {
        R<DescribeCollectionResponse> response = milvusServiceClient.describeCollection(DescribeCollectionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .build());
        DescCollResponseWrapper wrapper = new DescCollResponseWrapper(response.getData());
        return wrapper.toString();
    }

    /**
     * 数据库描述
     *
     * @param collectionName 库名
     * @return 结果
     */
    public R<GetCollectionStatisticsResponse> getCollectionStatistics(String collectionName) {
        milvusServiceClient.flush(FlushParam.newBuilder().addCollectionName(collectionName).build());

        return milvusServiceClient.getCollectionStatistics(
                GetCollectionStatisticsParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build());
    }
    /**
     * 数据库描述
     *
     * @return 结果
     */
    public R<GetCollectionStatisticsResponse> getCollectionStatistics() {
        milvusServiceClient.flush(FlushParam.newBuilder().addCollectionName(milvusProperties.getCollect()).build());

        R<GetCollectionStatisticsResponse> response = milvusServiceClient.getCollectionStatistics(
                GetCollectionStatisticsParam.newBuilder()
                        .withCollectionName(milvusProperties.getCollect())
                        .build());
        GetCollStatResponseWrapper wrapper = new GetCollStatResponseWrapper(response.getData());
        return response;
    }

    /**
     * 查询所有库
     *
     * @return 所有库
     */

    public R<ShowCollectionsResponse> showCollections() {
        R<ShowCollectionsResponse> response = milvusServiceClient.showCollections(ShowCollectionsParam.newBuilder()
                .build());
        System.out.println(response);
        return response;
    }

    /**
     * 创建分区
     *
     * @param collectionName 集合名称
     * @param partitionName 分区
     * @return 所有库
     */

    public R<RpcStatus> createPartition(String collectionName, String partitionName) {
        R<RpcStatus> response = milvusServiceClient.createPartition(CreatePartitionParam.newBuilder()
                .withCollectionName(collectionName)
                .withPartitionName(partitionName)
                .build());
        System.out.println(response);
        return response;
    }

    /**
     * 创建分区
     * @param partitionName 分区
     * @return 所有库
     */

    public R<RpcStatus> createPartition(String partitionName) {
        R<RpcStatus> response = milvusServiceClient.createPartition(CreatePartitionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .withPartitionName(partitionName)
                .build());
        System.out.println(response);
        return response;
    }

    /**
     * 创建分区
     *
     * @return 所有库
     */

    public R<RpcStatus> createPartition() {
        R<RpcStatus> response = milvusServiceClient.createPartition(CreatePartitionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .withPartitionName(milvusProperties.getPartitionName())
                .build());
        System.out.println(response);
        return response;
    }

    /**
     * 删除分区
     *
     * @param collectionName 集合名称
     * @param partitionName  分区名称
     * @return 所有库
     */

    public Boolean dropPartition(String collectionName, String partitionName) {
        R<Boolean> response = milvusServiceClient.hasPartition(HasPartitionParam.newBuilder()
                .withCollectionName(collectionName)
                .withPartitionName(partitionName)
                .build());
        return response.getData();
    }

    /**
     * 删除分区
     *
     * @param partitionName  分区名称
     * @return 所有库
     */

    public Boolean dropPartition( String partitionName) {
        R<Boolean> response = milvusServiceClient.hasPartition(HasPartitionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .withPartitionName(partitionName)
                .build());
        return response.getData();
    }

    /**
     * 删除分区
     *
     * @return 所有库
     */

    public Boolean dropPartition() {
        R<Boolean> response = milvusServiceClient.hasPartition(HasPartitionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .withPartitionName(milvusProperties.getPartitionName())
                .build());
        return response.getData();
    }

    /**
     * 是否存在分区
     *
     * @param collectionName 集合名称
     * @param partitionName  分区名称
     * @return 所有库
     */

    public Boolean hasPartition(String collectionName, String partitionName) {
        R<Boolean> response = milvusServiceClient.hasPartition(HasPartitionParam.newBuilder()
                .withCollectionName(collectionName)
                .withPartitionName(partitionName)
                .build());
        return response.getData();
    }
    /**
     * 是否存在分区
     *
     * @param partitionName  分区名称
     * @return 所有库
     */

    public Boolean hasPartition( String partitionName) {
        R<Boolean> response = milvusServiceClient.hasPartition(HasPartitionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .withPartitionName(partitionName)
                .build());
        return response.getData();
    }
    /**
     * 是否存在分区
     *
     * @return 所有库
     */

    public Boolean hasPartition( ) {
        R<Boolean> response = milvusServiceClient.hasPartition(HasPartitionParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .withPartitionName(milvusProperties.getPartitionName())
                .build());
        return response.getData();
    }

    /**
     * 释放分区
     *
     * @param collectionName 集合名称
     * @param partitionName  分区名称
     * @return 释放分区
     */

    public R<RpcStatus> releasePartition(String collectionName, String partitionName) {
        return milvusServiceClient.releasePartitions(ReleasePartitionsParam.newBuilder()
                .withCollectionName(collectionName)
                .addPartitionName(partitionName)
                .build());
    }
    /**
     * 释放分区
     *
     * @param partitionName  分区名称
     * @return 释放分区
     */

    public R<RpcStatus> releasePartition(String partitionName) {
        return milvusServiceClient.releasePartitions(ReleasePartitionsParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .addPartitionName(partitionName)
                .build());
    }
    /**
     * 释放分区
     *
     * @return 释放分区
     */

    public R<RpcStatus> releasePartition() {
        return milvusServiceClient.releasePartitions(ReleasePartitionsParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .addPartitionName(milvusProperties.getPartitionName())
                .build());
    }

    /**
     * 显示分区
     *
     * @param collectionName 集合名称
     * @return 显示分区
     */

    public R<ShowPartitionsResponse> showPartitions(String collectionName) {
        return milvusServiceClient.showPartitions(ShowPartitionsParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
    }

    /**
     * 显示分区
     *
     * @return 显示分区
     */

    public R<ShowPartitionsResponse> showPartitions() {
        return milvusServiceClient.showPartitions(ShowPartitionsParam.newBuilder()
                .withCollectionName(milvusProperties.getCollect())
                .build());
    }

    /**
     * 创建索引
     *
     * @return 创建索引
     */
    public R<RpcStatus> createIndex(CreateIndexParam createIndexParam) {
        System.out.println("========== createIndex() ==========");
        R<RpcStatus> response = milvusServiceClient.createIndex(createIndexParam);
        System.out.println(response);
        return response;
    }

    /**
     * 删除索引
     *
     * @param collectionName 库名
     * @param indexName      索引名称
     * @return 索引
     */
    public R<RpcStatus> dropIndex(String collectionName, String indexName) {
        System.out.println("========== dropIndex() ==========");
        R<RpcStatus> response = milvusServiceClient.dropIndex(DropIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withIndexName(indexName)
                .build());
        System.out.println(response);
        return response;
    }

    /**
     * 索引描述
     *
     * @param collectionName 库名
     * @param indexName      索引名称
     * @return 索引
     */
    public R<DescribeIndexResponse> describeIndex(String collectionName, String indexName) {
        System.out.println("========== describeIndex() ==========");
        R<DescribeIndexResponse> response = milvusServiceClient.describeIndex(DescribeIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withIndexName(indexName)
                .build());
        System.out.println(response);
        return response;
    }

    /**
     * 索引状态
     *
     * @param collectionName 库名
     * @param indexName      索引名称
     * @return 索引
     */
    public R<GetIndexStateResponse> getIndexState(String collectionName, String indexName) {
        System.out.println("========== getIndexState() ==========");
        R<GetIndexStateResponse> response = milvusServiceClient.getIndexState(GetIndexStateParam.newBuilder()
                .withCollectionName(collectionName)
                .withIndexName(indexName)
                .build());
        System.out.println(response);
        return response;
    }

    /**
     * 索引创建进度
     *
     * @param collectionName 库名
     * @param indexName      索引名称
     * @return 索引
     */
    public R<GetIndexBuildProgressResponse> getIndexBuildProgress(String collectionName, String indexName) {
        System.out.println("========== getIndexBuildProgress() ==========");
        R<GetIndexBuildProgressResponse> response = milvusServiceClient.getIndexBuildProgress(
                GetIndexBuildProgressParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withIndexName(indexName)
                        .build());
        System.out.println(response);
        return response;
    }

    /**
     * 删除分区
     *
     * @param collectionName 库
     * @param partitionName  分区名称
     * @param expr           过期时间
     * @return 结果
     */
    public R<MutationResult> delete(String collectionName, String partitionName, String expr) {
        System.out.println("========== delete() ==========");
        DeleteParam build = DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withPartitionName(partitionName)
                .withExpr(expr)
                .build();
        R<MutationResult> response = milvusServiceClient.delete(build);
        System.out.println(response.getData());
        return response;
    }

    /**
     * 查询人脸
     *
     * @param vectors        特征值
     * @param collectionName 库
     * @param topK           前几
     * @param vectorFields   特征值字段
     * @param params         参数
     * @param expr           表达式
     * @param outFields      输出字段
     * @return 结果
     */
    public SearchResultsWrapper searchFace(List<List<Float>> vectors, String collectionName, int topK, String vectorFields, String params, String expr, String... outFields) {
        System.out.println("========== searchFace() ==========");
        long begin = System.currentTimeMillis();

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.L2)
                .withOutFields(Arrays.asList(outFields))
                .withTopK(topK)
                .withVectors(vectors)
                .withVectorFieldName(vectorFields)
                .withExpr(expr)
                .withParams(params)
                .withGuaranteeTimestamp(Constant.GUARANTEE_EVENTUALLY_TS)
                .build();

        R<SearchResults> response = milvusServiceClient.search(searchParam);
        long end = System.currentTimeMillis();
        long cost = (end - begin);
        System.out.println("Search time cost: " + cost + "ms");

        return new SearchResultsWrapper(response.getData().getResults());
    }

    /**
     * 添加数据
     *
     * @param collectionName 库
     * @param partitionName  分区
     * @return 结果
     */

    public R<MutationResult> addDocument(String collectionName, String partitionName, List<InsertParam.Field> fields) {
        System.out.println("========== insert() ==========");
        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withPartitionName(partitionName)
                .withFields(fields)
                .build();

        return milvusServiceClient.insert(insertParam);
    }

    /**
     * 删除数据
     *
     * @param collectionName 库
     * @param partitionName  分区
     * @param expr 表达式
     * @return 结果
     */

    public R<MutationResult> deleteDocument(String collectionName, String partitionName, String expr) {
        System.out.println("========== delete() ==========");
        DeleteParam insertParam = DeleteParam.newBuilder()
                .withExpr(expr)
                .withCollectionName(collectionName)
                .withPartitionName(partitionName)
                .build();

        return milvusServiceClient.delete(insertParam);
    }


}
