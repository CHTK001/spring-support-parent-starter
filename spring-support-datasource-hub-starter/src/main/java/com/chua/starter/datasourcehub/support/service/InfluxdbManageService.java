package com.chua.starter.datasourcehub.support.service;

import com.chua.starter.datasourcehub.support.model.InfluxdbBucketVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbFieldVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbMeasurementVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbOrgVO;
import com.chua.starter.datasourcehub.support.model.InfluxdbQueryResultVO;
import com.chua.starter.datasourcehub.support.model.MiddlewareTestVO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbBucketDTO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbMeasurementDeleteDTO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbQueryDTO;
import com.chua.starter.datasourcehub.support.model.dto.InfluxdbWriteDTO;
import com.chua.starter.datasourcehub.support.model.dto.MiddlewareTestDTO;

import java.util.List;

/**
 * InfluxDB 时序数据库管理服务：Bucket、度量与 Flux 查询。
 *
 * <p>全部走 InfluxDB 2.x 的 HTTP API，本地不缓存任何元数据；连接不可达或服务端返回
 * Flux 错误时抛 {@link IllegalStateException} 并带上原始原因，页面直接把原因摊开显示。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface InfluxdbManageService {

    /**
     * 测试 InfluxDB 连通性并取出版本号。
     *
     * @param request 测试请求，可为空（空即测试第一个 influxdb 数据源）
     * @return 测试结果，失败时 connected 为 false 且 message 为原因
     */
    MiddlewareTestVO test(MiddlewareTestDTO request);

    /**
     * 列出 org 下的 Bucket。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return Bucket 列表
     */
    List<InfluxdbBucketVO> buckets(Long datasourceId);

    /**
     * 列出 Bucket 下的度量。
     *
     * @param datasourceId 数据源 ID，可为空
     * @param bucket       Bucket 名，为空时取第一个 Bucket
     * @return 度量列表
     */
    List<InfluxdbMeasurementVO> measurements(Long datasourceId, String bucket);

    /**
     * 获取度量下的字段（tag + field）信息。
     *
     * <p>用于数据库管理器的列展示：tag 作为维度列、field 作为度量列。
     * v3 走 information_schema.columns，v2/v1 走 Flux/InfluxQL schema 查询。</p>
     *
     * @param datasourceId 数据源 ID，可为空
     * @param bucket       Bucket 名，为空时取第一个 Bucket
     * @param measurement  度量名
     * @return 字段列表（tag + field），失败时返回空列表
     */
    List<InfluxdbFieldVO> measurementFields(Long datasourceId, String bucket, String measurement);

    /**
     * 执行一条 Flux 查询。
     *
     * @param request 查询请求
     * @return 查询结果，含耗时与行数
     */
    InfluxdbQueryResultVO query(InfluxdbQueryDTO request);

    /**
     * 读取当前数据源对应的组织（Org）信息。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 组织 ID 与名称
     */
    InfluxdbOrgVO org(Long datasourceId);

    /**
     * 新建 Bucket。
     *
     * @param request 新建请求
     * @return 新建后的 Bucket
     */
    InfluxdbBucketVO createBucket(InfluxdbBucketDTO request);

    /**
     * 删除 Bucket。
     *
     * @param datasourceId 数据源 ID，可为空
     * @param bucketId     Bucket ID（UUID）
     */
    void deleteBucket(Long datasourceId, String bucketId);

    /**
     * 编辑 Bucket 保留期。
     *
     * @param datasourceId    数据源 ID，可为空
     * @param bucketId        Bucket ID（UUID）
     * @param retentionHours 新的保留时长（小时），空或非正表示永久
     */
    void updateBucketRetention(Long datasourceId, String bucketId, Long retentionHours);

    /**
     * 写入数据点（line protocol）。
     *
     * @param request 写入请求
     */
    void write(InfluxdbWriteDTO request);

    /**
     * 删除测量（InfluxDB 的「表」）或删除其数据。
     *
     * <p>v1/v2 用 InfluxQL {@code DROP MEASUREMENT} 删除整个测量；v3 用 SQL
     * {@code DROP TABLE}。只删数据保留测量不支持（时序库无独立清空语义，用户可
     * 按时间范围删除，见 {@code deleteData}）。</p>
     *
     * @param request 删除请求
     */
    void dropMeasurement(InfluxdbMeasurementDeleteDTO request);
}
