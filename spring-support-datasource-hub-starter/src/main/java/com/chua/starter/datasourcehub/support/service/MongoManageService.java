package com.chua.starter.datasourcehub.support.service;

import com.chua.starter.datasourcehub.support.model.MongoCollectionVO;
import com.chua.starter.datasourcehub.support.model.MongoDatabaseVO;
import com.chua.starter.datasourcehub.support.model.MongoQueryResultVO;
import com.chua.starter.datasourcehub.support.model.MongoTestVO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.datasourcehub.support.model.dto.MongoDocumentDTO;
import com.chua.starter.datasourcehub.support.model.dto.MongoQueryDTO;
import java.util.List;
import java.util.Map;

/**
 * MongoDB 管理服务：连通性探测、逻辑库/集合浏览、只读查询与单文档查看。
 *
 * <p>所有方法都按「数据源 ID 定位 mongodb 数据源」工作，ID 为空时退化为第一个 mongodb 数据源；
 * 远端失败一律换成可读原因抛出（{@link IllegalStateException}），由控制层转成业务错误码。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface MongoManageService {

    /**
     * 测试连通性并取版本信息。
     *
     * @param request 测试请求，可为空（支持即席 host/port/凭据探测）
     * @return 测试结果，失败时 {@code connected} 为 false 且 {@code message} 为原因
     */
    MongoTestVO test(MiddlewareTestDTO request);

    /**
     * 逻辑库列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 逻辑库列表
     */
    List<MongoDatabaseVO> databases(Long datasourceId);

    /**
     * 指定逻辑库的集合列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param database     逻辑库名，为空用数据源默认库
     * @return 集合列表
     */
    List<MongoCollectionVO> collections(Long datasourceId, String database);

    /**
     * 执行一句只读 shell 查询。
     *
     * @param request 查询请求
     * @return 查询结果
     */
    MongoQueryResultVO query(MongoQueryDTO request);

    /**
     * 按 ID 查看单个文档。
     *
     * @param request 查看请求
     * @return 文档内容；不存在时抛 {@link IllegalStateException}
     */
    Map<String, Object> document(MongoDocumentDTO request);
}
