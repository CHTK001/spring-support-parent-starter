// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.apm.OtnlMetricQuery;
import com.chua.common.support.apm.OtnlQuery;
import com.chua.common.support.apm.OtnlScope;
import com.chua.common.support.apm.OtnlTraceState;
import com.chua.common.support.lang.code.ReturnResult;
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
import com.chua.starter.datasourcehub.support.service.SkywalkingManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Supplier;

/**
 * SkyWalking 控制台 REST 接口。
 *
 * <p>所有查询走 OAP 的 GraphQL，由 {@link com.chua.starter.datasourcehub.support.service.SkywalkingManageService}
 * 负责按每请求的数据源地址建连。列表页与落地页的返回格式由控制器的 {@code call()} 统一翻成
 * {@code ReturnResult}，因此远端失败只暴露原因、不会 500。</p>
 *
 * <p>datasourceId 可缺省，服务端退化为第一个 skywalking 数据源。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Tag(name = "监控管理", description = "SkyWalking 管理")
@RestController
@RequestMapping("/ms/monitor/skywalking")
@RequiredArgsConstructor
public class SkywalkingManageController {

    /**
     * SkyWalking 管理服务
     */
    private final SkywalkingManageService skywalkingManageService;

    // ==================== 总览 / 状态 ====================

    /**
     * 读取总览：版本、可用性、能力清单、服务/告警计数。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 总览
     */
    @Operation(summary = "SkyWalking 总览")
    @GetMapping("/overview")
    public ReturnResult<SkywalkingOverviewVO> overview(
            @RequestParam(required = false) Long datasourceId) {
        return call("读取 SkyWalking 总览",
                () -> skywalkingManageService.overview(datasourceId));
    }

    /**
     * 读取引擎状态：可用性 + 后端地址 + 失败原因。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 状态
     */
    @Operation(summary = "SkyWalking 状态")
    @GetMapping("/status")
    public ReturnResult<SkywalkingStatusVO> status(@RequestParam(required = false) Long datasourceId) {
        return call("读取 SkyWalking 状态", () -> skywalkingManageService.status(datasourceId));
    }

    // ==================== 列表查询 ====================

    /**
     * 服务目录。
     *
     * @param serviceName  服务名，可空
     * @param keyword      关键字（按服务名过滤），可空
     * @param page         页码（从 1 开始）
     * @param size         每页条数
     * @param startTime    起始毫秒时间戳
     * @param endTime      结束毫秒时间戳
     * @param datasourceId 数据源 ID，可空
     * @return 服务列表
     */
    @Operation(summary = "SkyWalking 服务目录")
    @GetMapping("/services")
    public ReturnResult<List<SkywalkingServiceVO>> services(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long datasourceId) {
        OtnlQuery query = buildQuery(serviceName, null, null, null, keyword, null,
                startTime, endTime, null, page, size, false);
        return call("读取 SkyWalking 服务目录",
                () -> skywalkingManageService.services(datasourceId, query));
    }

    /**
     * 服务实例。
     *
     * @param serviceName  服务名
     * @param keyword      关键字（按实例名过滤），可空
     * @param page         页码（从 1 开始）
     * @param size         每页条数
     * @param startTime    起始毫秒时间戳
     * @param endTime      结束毫秒时间戳
     * @param datasourceId 数据源 ID，可空
     * @return 实例列表
     */
    @Operation(summary = "SkyWalking 服务实例")
    @GetMapping("/instances/{serviceName}")
    public ReturnResult<List<SkywalkingInstanceVO>> instances(
            @PathVariable String serviceName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long datasourceId) {
        OtnlQuery query = buildQuery(null, null, null, null, keyword, null,
                startTime, endTime, null, page, size, false);
        return call("读取 SkyWalking 服务实例",
                () -> skywalkingManageService.instances(datasourceId, serviceName, query));
    }

    /**
     * 服务端点。
     *
     * @param serviceName  服务名
     * @param keyword      关键字（按端点名过滤）
     * @param page         页码（从 1 开始）
     * @param size         每页条数
     * @param startTime    起始毫秒时间戳
     * @param endTime      结束毫秒时间戳
     * @param datasourceId 数据源 ID，可空
     * @return 端点列表
     */
    @Operation(summary = "SkyWalking 服务端点")
    @GetMapping("/endpoints/{serviceName}")
    public ReturnResult<List<SkywalkingEndpointVO>> endpoints(
            @PathVariable String serviceName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long datasourceId) {
        OtnlQuery query = buildQuery(null, null, null, null, keyword, null,
                startTime, endTime, null, page, size, false);
        return call("读取 SkyWalking 服务端点",
                () -> skywalkingManageService.endpoints(datasourceId, serviceName, query));
    }

    /**
     * 链路列表。
     *
     * @param serviceName  服务名，可空
     * @param keyword      关键字（按端点名过滤），可空
     * @param state        链路状态，可空（ALL）
     * @param page         页码（从 1 开始）
     * @param size         每页条数
     * @param startTime    起始毫秒时间戳
     * @param endTime      结束毫秒时间戳
     * @param datasourceId 数据源 ID，可空
     * @return 链路摘要
     */
    @Operation(summary = "SkyWalking 链路列表")
    @GetMapping("/traces")
    public ReturnResult<SkywalkingTracePageVO> traces(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OtnlTraceState state,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) Long datasourceId) {
        OtnlQuery query = buildQuery(serviceName, null, null, null, keyword, state,
                startTime, endTime, null, page, size, false);
        return call("读取 SkyWalking 链路列表",
                () -> skywalkingManageService.traces(datasourceId, query));
    }

    /**
     * 链路详情（含 Span 明细）。
     *
     * @param traceId 链路 ID
     * @param datasourceId 数据源 ID，可空
     * @return 链路详情；不存在返回空数据
     */
    @Operation(summary = "SkyWalking 链路详情")
    @GetMapping("/trace/{traceId}")
    public ReturnResult<SkywalkingTraceDetailVO> trace(@PathVariable String traceId,
                                                       @RequestParam(required = false) Long datasourceId) {
        return call("读取 SkyWalking 链路详情",
                () -> skywalkingManageService.trace(datasourceId, traceId));
    }

    /**
     * 服务拓扑。
     *
     * @param serviceName 服务名，可空（空表示全局拓扑）
     * @param page         页码（从 1 开始）
     * @param size         每页条数
     * @param startTime    起始毫秒时间戳
     * @param endTime      结束毫秒时间戳
     * @param datasourceId 数据源 ID，可空
     * @return 拓扑
     */
    @Operation(summary = "SkyWalking 服务拓扑")
    @GetMapping("/topology")
    public ReturnResult<SkywalkingTopologyVO> topology(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) Long datasourceId) {
        OtnlQuery query = buildQuery(serviceName, null, null, null, null, null,
                startTime, endTime, null, page, size, false);
        return call("读取 SkyWalking 服务拓扑",
                () -> skywalkingManageService.topology(datasourceId, query));
    }

    // ==================== 指标 / 告警 / 日志 ====================

    /**
     * 指标时序。
     *
     * @param name          指标名
     * @param scope         作用域（SERVICE / ENDPOINT / SERVICE_INSTANCE）
     * @param serviceName   服务名
     * @param instanceName  实例名
     * @param endpointName  端点名
     * @param startTime     起始毫秒时间戳
     * @param endTime       结束毫秒时间戳
     * @param step          聚合步长（SECOND / MINUTE / HOUR / DAY）
     * @param datasourceId  数据源 ID，可空
     * @return 时序
     */
    @Operation(summary = "SkyWalking 指标时序")
    @GetMapping("/metrics")
    public ReturnResult<SkywalkingMetricSeriesVO> metrics(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String instanceName,
            @RequestParam(required = false) String endpointName,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) String step,
            @RequestParam(required = false) Long datasourceId) {
        OtnlMetricQuery query = new OtnlMetricQuery(name, OtnlScope.of(scope), serviceName,
                instanceName, endpointName,
                startTime == null ? 0L : startTime, endTime == null ? 0L : endTime, step);
        return call("读取 SkyWalking 指标时序", () -> skywalkingManageService.metrics(datasourceId, query));
    }

    /**
     * 告警列表。
     *
     * @param keyword      关键字，可空
     * @param page         页码（从 1 开始）
     * @param size         每页条数
     * @param startTime    起始毫秒时间戳
     * @param endTime      结束毫秒时间戳
     * @param datasourceId 数据源 ID，可空
     * @return 告警列表
     */
    @Operation(summary = "SkyWalking 告警列表")
    @GetMapping("/alarms")
    public ReturnResult<List<SkywalkingAlarmVO>> alarms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) Long datasourceId) {
        OtnlQuery query = buildQuery(null, null, null, null, keyword, null,
                startTime, endTime, null, page, size, false);
        return call("读取 SkyWalking 告警列表",
                () -> skywalkingManageService.alarms(datasourceId, query));
    }

    /**
     * 日志列表。
     *
     * @param serviceName  服务名，可空
     * @param instanceName 实例名，可空
     * @param endpointName 端点名，可空
     * @param keyword      关键字（按日志内容过滤）
     * @param page         页码（从 1 开始）
     * @param size         每页条数
     * @param startTime    起始毫秒时间戳
     * @param endTime      结束毫秒时间戳
     * @param datasourceId 数据源 ID，可空
     * @return 日志列表
     */
    @Operation(summary = "SkyWalking 日志列表")
    @GetMapping("/logs")
    public ReturnResult<List<SkywalkingLogRecordVO>> logs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String instanceName,
            @RequestParam(required = false) String endpointName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) Long datasourceId) {
        OtnlQuery query = buildQuery(serviceName, instanceName, endpointName, null, keyword, null,
                startTime, endTime, null, page, size, false);
        return call("读取 SkyWalking 日志列表", () -> skywalkingManageService.logs(datasourceId, query));
    }

    // ==================== 内部 ====================

    /**
     * 把查询参数归一变成 {@link OtnlQuery}。
     *
     * @param serviceName     服务名
     * @param instanceName    实例名
     * @param endpointName    端点名
     * @param traceId         链路 ID
     * @param keyword         关键字
     * @param state           链路状态
     * @param startTime       起始毫秒（null 表示不指定）
     * @param endTime         结束毫秒（null 表示不指定）
     * @param step            时序聚合步长
     * @param page            页码
     * @param size            每页条数
     * @param orderByDuration 是否按耗时倒序
     * @return 查询条件
     */
    private static OtnlQuery buildQuery(String serviceName, String instanceName,
                                        String endpointName, String traceId,
                                        String keyword, OtnlTraceState state,
                                        Long startTime, Long endTime, String step,
                                        Integer page, Integer size, boolean orderByDuration) {
        return new OtnlQuery(serviceName, instanceName, endpointName, traceId, keyword, state,
                startTime == null ? 0L : startTime, endTime == null ? 0L : endTime, step,
                page == null ? 1 : page, size == null ? 20 : size, orderByDuration);
    }

    /**
     * 执行一次查询，把远端失败换成可读原因而不是 500 堆栈。
     *
     * @param action   操作描述
     * @param supplier 业务调用
     * @param <T>      返回类型
     * @return 响应
     */
    private <T> ReturnResult<T> call(String action, Supplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[skywalking] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}