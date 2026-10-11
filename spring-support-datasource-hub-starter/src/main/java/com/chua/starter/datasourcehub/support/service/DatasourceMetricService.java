package com.chua.starter.datasourcehub.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DataSourceTestVO;
import com.chua.starter.datasourcehub.support.model.DatasourceMetricPushVO;
import com.chua.starter.datasourcehub.support.model.DatasourceMetricTrendVO;

/**
 * 数据源连通性指标服务。
 *
 * <p>采集口径：<b>有人看才采</b>。{@code sample} 只对存在 SSE 订阅者的数据源调用，
 * 没人看的数据源不产生探测流量 —— 数据源中心可能挂着上百条台账，
 * 为了一条没人看的曲线去定时戳它们是不可接受的。</p>
 *
 * <p>因此这张表里的数据是「被观测过的历史」而不是「全量台账的健康档案」：
 * {@link #trend} 在该数据源从未被观测过时返回空并说明原因，
 * 而不是把「没采过」画成「一直正常」。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public interface DatasourceMetricService {

    /**
     * 探测一次并落一个采样点。
     *
     * @param datasourceId 数据源 ID
     * @return 探测结果；数据源不存在时返回错误
     */
    ReturnResult<DataSourceTestVO> sample(Long datasourceId);

    /**
     * 探测一次并组装 SSE 推送载荷（含最近窗口的采样点）。
     *
     * @param datasourceId 数据源 ID
     * @param windowPoints 随推送带上的最近点数
     * @return 推送载荷
     */
    ReturnResult<DatasourceMetricPushVO> sampleForPush(Long datasourceId, int windowPoints);

    /**
     * 查询指标趋势。
     *
     * @param datasourceId 数据源 ID
     * @param hours        回溯小时数
     * @param intervalSec  采样间隔（秒）；实际返回点数超上限时后端自动放大间隔
     * @return 趋势
     */
    ReturnResult<DatasourceMetricTrendVO> trend(Long datasourceId, Integer hours, Integer intervalSec);

    /**
     * 删除某数据源的历史指标（数据源被删除时调用）。
     *
     * @param datasourceId 数据源 ID
     */
    void purge(Long datasourceId);

    /**
     * 清理超保留期的历史指标（保留窗口滚动清理）。
     *
     * <p>没有这个的话 {@code ms_datasource_metric} 会无上限增长：只要有人盯着
     * 监控指标页签，每分钟一行，7 天窗口查询却可能回溯到更早的点。</p>
     *
     * @param retentionDays 保留天数；小于等于 0 表示不清理
     * @return 删除行数
     */
    int purgeOlderThan(int retentionDays);
}