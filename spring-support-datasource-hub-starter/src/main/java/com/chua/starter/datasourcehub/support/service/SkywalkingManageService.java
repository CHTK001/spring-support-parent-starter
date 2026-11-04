package com.chua.starter.datasourcehub.support.service;

import com.chua.common.support.apm.OtnlMetricQuery;
import com.chua.common.support.apm.OtnlQuery;
import com.chua.starter.datasourcehub.support.model.SkywalkingAlarmVO;
import com.chua.starter.datasourcehub.support.model.SkywalkingEndpointVO;
import com.chua.starter.datasourcehub.support.model.SkywalkingInstanceVO;
import com.chua.starter.datasourcehub.support.model.SkywalkingLogRecordVO;
import com.chua.starter.datasourcehub.support.model.SkywalkingMetricSeriesVO;
import com.chua.starter.datasourcehub.support.model.SkywalkingOverviewVO;
import com.chua.starter.datasourcehub.support.model.SkywalkingServiceVO;
import com.chua.starter.datasourcehub.support.model.SkywalkingStatusVO;
import com.chua.starter.datasourcehub.support.model.SkywalkingTopologyVO;
import com.chua.starter.datasourcehub.support.model.SkywalkingTraceDetailVO;
import com.chua.starter.datasourcehub.support.model.SkywalkingTracePageVO;

import java.util.List;

/**
 * SkyWalking 控制台服务。
 *
 * <p>查询合约取自 {@link com.chua.common.support.apm.OtnlEngine}：services、instances、endpoints、
 * traces、trace、topology、metrics、alarms、logs、health，以及一份汇总了引擎状态与告警/服务计数的
 * 总览。数据源地址（OAP 地址、鉴权）按请求取数时的数据源行动态注入进程级设置，因此切换数据源时
 * 不需要重建 Spring Bean。</p>
 *
 * <p><strong>降级约定</strong>：所有远端失败由控制器翻成 {@code ReturnResult} 错误信封或业务错误文案，
 * 页面永远不展示 500 堆栈。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface SkywalkingManageService {

    /**
     * 读取总览：引擎状态、版本、后端地址、能力清单、服务/告警计数。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 总览
     */
    SkywalkingOverviewVO overview(Long datasourceId);

    /**
     * 服务目录。
     *
     * @param datasourceId 数据源 ID，可空
     * @param query        查询条件
     * @return 服务列表
     */
    List<SkywalkingServiceVO> services(Long datasourceId, OtnlQuery query);

    /**
     * 服务实例。
     *
     * @param datasourceId 数据源 ID，可空
     * @param serviceName  服务名
     * @param query        查询条件
     * @return 实例列表
     */
    List<SkywalkingInstanceVO> instances(Long datasourceId, String serviceName, OtnlQuery query);

    /**
     * 服务端点。
     *
     * @param datasourceId 数据源 ID，可空
     * @param serviceName  服务名
     * @param query        查询条件（keyword 用于端点名过滤）
     * @return 端点列表
     */
    List<SkywalkingEndpointVO> endpoints(Long datasourceId, String serviceName, OtnlQuery query);

    /**
     * 链路列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param query        查询条件
     * @return 链路列表与计数
     */
    SkywalkingTracePageVO traces(Long datasourceId, OtnlQuery query);

    /**
     * 链路详情。
     *
     * @param datasourceId 数据源 ID，可空
     * @param traceId      链路 ID
     * @return 含 Span 明细的链路；不存在返回 null
     */
    SkywalkingTraceDetailVO trace(Long datasourceId, String traceId);

    /**
     * 服务拓扑。
     *
     * @param datasourceId 数据源 ID，可空
     * @param query        查询条件（serviceName 为空表示全局拓扑）
     * @return 拓扑
     */
    SkywalkingTopologyVO topology(Long datasourceId, OtnlQuery query);

    /**
     * 指标时序。
     *
     * @param datasourceId 数据源 ID，可空
     * @param query        指标查询
     * @return 时序
     */
    SkywalkingMetricSeriesVO metrics(Long datasourceId, OtnlMetricQuery query);

    /**
     * 告警列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param query        查询条件
     * @return 告警列表
     */
    List<SkywalkingAlarmVO> alarms(Long datasourceId, OtnlQuery query);

    /**
     * 日志列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param query        查询条件
     * @return 日志列表
     */
    List<SkywalkingLogRecordVO> logs(Long datasourceId, OtnlQuery query);

    /**
     * 引擎状态（可用性 + 后端地址 + 失败原因）。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 状态
     */
    SkywalkingStatusVO status(Long datasourceId);
}
