package com.chua.starter.datasourcehub.support.service;

import com.chua.starter.datasourcehub.support.model.ElasticsearchClusterVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchGraphVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchIndexDetailVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchIndexVO;
import com.chua.starter.datasourcehub.support.model.ElasticsearchShardVO;
import com.chua.starter.datasourcehub.support.model.MiddlewareTestVO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchClusterSettingsDTO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchIndexDTO;
import com.chua.starter.datasourcehub.support.model.dto.ElasticsearchMappingDTO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import java.util.List;

/**
 * Elasticsearch 搜索引擎管理服务：索引与字段设计、集群配置、关系图
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ElasticsearchManageService {

    /**
     * 测试连通性
     *
     * @param request 请求，可为空
     * @return 测试结果
     */
    MiddlewareTestVO test(MiddlewareTestDTO request);

    /**
     * 索引列表
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 索引列表
     */
    List<ElasticsearchIndexVO> indices(Long datasourceId);

    /**
     * 索引详情（字段映射 + 设置）
     *
     * @param index        索引名
     * @param datasourceId 数据源 ID，可为空
     * @return 索引详情
     */
    ElasticsearchIndexDetailVO index(String index, Long datasourceId);

    /**
     * 新建索引（相当于建表）
     *
     * @param request 新建请求
     * @return 新建后的索引详情
     */
    ElasticsearchIndexDetailVO createIndex(ElasticsearchIndexDTO request);

    /**
     * 删除索引
     *
     * @param index        索引名
     * @param datasourceId 数据源 ID，可为空
     * @return 是否删除成功
     */
    Boolean deleteIndex(String index, Long datasourceId);

    /**
     * 给已有索引追加字段
     *
     * @param request 追加请求
     * @return 更新后的索引详情
     */
    ElasticsearchIndexDetailVO addMapping(ElasticsearchMappingDTO request);

    /**
     * 集群信息（健康度 + 节点 + 设置）
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 集群信息
     */
    ElasticsearchClusterVO cluster(Long datasourceId);

    /**
     * 分片分配
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 分片列表
     */
    List<ElasticsearchShardVO> shards(Long datasourceId);

    /**
     * 更新集群设置
     *
     * @param request 更新请求
     * @return 更新后的集群信息
     */
    ElasticsearchClusterVO updateClusterSettings(ElasticsearchClusterSettingsDTO request);

    /**
     * 关系图：索引/字段为节点、别名为边
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 关系图
     */
    ElasticsearchGraphVO graph(Long datasourceId);
}
