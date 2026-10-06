package com.chua.starter.datasourcehub.support.service;

import com.chua.starter.datasourcehub.support.model.Neo4jLabelVO;
import com.chua.starter.datasourcehub.support.model.Neo4jQueryResultVO;
import com.chua.starter.datasourcehub.support.model.Neo4jRelationshipVO;
import com.chua.starter.datasourcehub.support.model.Neo4jTestVO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.model.dto.Neo4jQueryDTO;
import java.util.List;

/**
 * Neo4j 图数据库管理服务：连通性探测、标签/关系类型统计与 Cypher 执行。
 *
 * <p>所有方法都按「数据源 ID 定位 neo4j 数据源」工作，ID 为空时退化为第一个 neo4j 数据源，
 * 远端失败一律换成可读原因抛出，由控制层转成业务错误码。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface Neo4jManageService {

    /**
     * 测试连通性并取版本信息。
     *
     * @param request 测试请求，可为空
     * @return 测试结果
     */
    Neo4jTestVO test(MiddlewareTestDTO request);

    /**
     * 标签列表及其统计。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 标签统计行
     */
    List<Neo4jLabelVO> labels(Long datasourceId);

    /**
     * 关系类型列表及其统计。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 关系类型统计行
     */
    List<Neo4jRelationshipVO> relationships(Long datasourceId);

    /**
     * 执行一句 Cypher。
     *
     * @param request 查询请求
     * @return 查询结果
     */
    Neo4jQueryResultVO query(Neo4jQueryDTO request);
}
