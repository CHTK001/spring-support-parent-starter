package com.chua.starter.datasourcehub.support.service.impl;

import com.chua.common.support.apm.OtnlEngine;
import com.chua.common.support.apm.OtnlMetricQuery;
import com.chua.common.support.apm.OtnlQuery;
import com.chua.common.support.apm.OtnlTag;
import com.chua.common.support.apm.model.OtnlAlarm;
import com.chua.common.support.apm.model.OtnlEndpoint;
import com.chua.common.support.apm.model.OtnlInstance;
import com.chua.common.support.apm.model.OtnlLogRecord;
import com.chua.common.support.apm.model.OtnlMetricSeries;
import com.chua.common.support.apm.model.OtnlService;
import com.chua.common.support.apm.model.OtnlSpan;
import com.chua.common.support.apm.model.OtnlStatus;
import com.chua.common.support.apm.model.OtnlTopology;
import com.chua.common.support.apm.model.OtnlTrace;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.constants.DatasourceHubConstants;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
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
import com.chua.starter.datasourcehub.support.model.TagVO;
import com.chua.starter.datasourcehub.support.model.TraceSpanLogVO;
import com.chua.starter.datasourcehub.support.model.TraceSpanVO;
import com.chua.starter.datasourcehub.support.model.TraceSummaryVO;
import com.chua.starter.datasourcehub.support.model.TopologyCallVO;
import com.chua.starter.datasourcehub.support.model.TopologyNodeVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.datasourcehub.support.service.SkywalkingManageService;
import com.chua.skywalking.support.client.SkywalkingOapClient;
import com.chua.skywalking.support.client.SkywalkingSettings;
import com.chua.skywalking.support.engine.SkywalkingOtnlEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * SkyWalking 控制台服务实现。
 *
 * <p>查询合约由 {@link SkywalkingOtnlEngine}（SPI 名 {@code skywalking}）承担，这里只负责
 * 把每请求指定的数据源运行期注入到 {@link SkywalkingSettings}——OAP 地址、账号、超时与启用标记
 * 都是进程级静态字段，因此每次查询前重新 apply 即可在「当前 OAP 地址」与「上一个」之间切换，
 * 再新建引擎实例即可享用连接失效保护（{@code SkywalkingOapClient} 会在 OAP 地址变更时释放旧连接）。</p>
 * RPG数据源行约定：
 *
 * <p>数据源行约定：{@code host} + {@code port}（默认 12800），
 * {@code database} 用于填写完整的 OAP 地址时原样使用，
 * {@code username}/{@code password} 为 OAP 鉴权（可选）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkywalkingManageServiceImpl implements SkywalkingManageService {

    /**
     * 数据源类型标识
     */
    private static final String DS_TYPE = "skywalking";

    /**
     * 总览里「最近告警」的查询时间窗口（分钟）
     */
    private static final int RECENT_ALARM_MINUTES = 15;

    /**
     * 连接超时（毫秒）
     */
    private static final long TIMEOUT_MS = 5_000L;

    /**
     * 数据源服务
     */
    private final DatasourceService datasourceService;

    // ==================== 总览 ====================

    @Override
    public SkywalkingOverviewVO overview(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        SkywalkingOtnlEngine engine = engine(ds);
        OtnlStatus status = statusSilently(engine);
        List<OtnlAlarm> alarms = alarmSilently(engine);
        int serviceCount = serviceCountSilently(engine);

        return new SkywalkingOverviewVO(
                ds.id(),
                ds.name(),
                oapUrl(ds),
                status.version(),
                status.available(),
                status.enabled(),
                status.message(),
                status.capabilities(),
                status.checkedAt(),
                serviceCount,
                alarms.size(),
                recentAlarmCountSilently(engine)
        );
    }

    // ==================== 列表查询 ====================

    @Override
    public List<SkywalkingServiceVO> services(Long datasourceId, OtnlQuery query) {
        return withEngine(datasourceId, engine -> {
            List<OtnlService> services = engine.services(query);
            List<SkywalkingServiceVO> result = new ArrayList<>(services.size());
            for (OtnlService s : services) {
                result.add(new SkywalkingServiceVO(s.id(), s.name(), s.group(),
                        s.layers(), s.instances(), s.endpoints(), s.calls(), s.avgMs(),
                        s.p95Ms(), s.p99Ms(), s.errorRate(), toTagList(s.tags())));
            }
            return result;
        });
    }

    @Override
    public List<SkywalkingInstanceVO> instances(Long datasourceId, String serviceName, OtnlQuery query) {
        return withEngine(datasourceId, engine -> {
            List<OtnlInstance> instances = engine.instances(serviceName, query);
            List<SkywalkingInstanceVO> result = new ArrayList<>(instances.size());
            for (OtnlInstance i : instances) {
                result.add(new SkywalkingInstanceVO(i.id(), i.serviceName(), i.name(),
                        i.language(), i.heartbeatTime(), toTagList(i.attributes())));
            }
            return result;
        });
    }

    @Override
    public List<SkywalkingEndpointVO> endpoints(Long datasourceId, String serviceName, OtnlQuery query) {
        return withEngine(datasourceId, engine -> {
            List<OtnlEndpoint> endpoints = engine.endpoints(serviceName, query);
            List<SkywalkingEndpointVO> result = new ArrayList<>(endpoints.size());
            for (OtnlEndpoint e : endpoints) {
                result.add(new SkywalkingEndpointVO(e.id(), e.serviceName(), e.name(),
                        e.calls(), e.avgMs(), e.p95Ms(), e.p99Ms(), e.errorRate(),
                        toTagList(e.tags())));
            }
            return result;
        });
    }

    @Override
    public SkywalkingTracePageVO traces(Long datasourceId, OtnlQuery query) {
        return withEngine(datasourceId, engine -> {
            List<OtnlTrace> traces = engine.traces(query);
            List<TraceSummaryVO> list = new ArrayList<>(traces.size());
            for (OtnlTrace t : traces) {
                list.add(new TraceSummaryVO(t.traceId(), t.serviceName(), t.endpointName(),
                        t.durationMs(), t.startTime(), t.endTime(), t.error(), t.spanCount()));
            }
            return new SkywalkingTracePageVO(list, traces.size(),
                    query.pageNumOrOne(), query.pageSizeOrTwenty());
        });
    }

    @Override
    public SkywalkingTraceDetailVO trace(Long datasourceId, String traceId) {
        return withEngine(datasourceId, engine -> {
            OtnlTrace trace = engine.trace(traceId);
            if (trace == null) {
                return null;
            }
            List<TraceSpanVO> spans = new ArrayList<>(trace.spans().size());
            for (OtnlSpan span : trace.spans()) {
                spans.add(new TraceSpanVO(span.traceId(), span.segmentId(), span.spanId(),
                        span.parentSpanId(), span.serviceName(), span.instanceName(),
                        span.endpointName(), span.layer(), span.type(), span.component(), span.peer(),
                        span.startTime(), span.endTime(), span.durationMs(), span.error(),
                        toTagList(span.tags()), span.logs() == null ? List.of()
                                : span.logs()
                                .stream()
                                .map(l -> new TraceSpanLogVO(l.time(), toTagList(l.tags())))
                                .toList()));
            }
            return new SkywalkingTraceDetailVO(trace.traceId(), trace.serviceName(),
                    trace.endpointName(), trace.durationMs(), trace.startTime(), trace.endTime(),
                    trace.error(), trace.spans().size(), spans);
        });
    }

    @Override
    public SkywalkingTopologyVO topology(Long datasourceId, OtnlQuery query) {
        return withEngine(datasourceId, engine -> {
            OtnlTopology topology = engine.topology(query);
            List<TopologyNodeVO> nodes = new ArrayList<>(topology.nodes().size());
            for (OtnlTopology.OtnlTopologyNode n : topology.nodes()) {
                nodes.add(new TopologyNodeVO(n.id(), n.name(), n.type(), n.real(), n.isSelf(), n.layer()));
            }
            List<TopologyCallVO> calls = new ArrayList<>(topology.calls().size());
            for (OtnlTopology.OtnlTopologyCall c : topology.calls()) {
                calls.add(new TopologyCallVO(c.id(), c.source(), c.target(),
                        c.detectPoint(), c.calls(), c.errorRate(), c.avgMs()));
            }
            return new SkywalkingTopologyVO(nodes, calls);
        });
    }

    @Override
    public SkywalkingMetricSeriesVO metrics(Long datasourceId, OtnlMetricQuery query) {
        return withEngine(datasourceId, engine -> {
            OtnlMetricSeries series = engine.metrics(query);
            List<SkywalkingMetricSeriesVO.MetricPointVO> points = new ArrayList<>(series.points().size());
            for (OtnlMetricSeries.OtnlMetricPoint p : series.points()) {
                points.add(new SkywalkingMetricSeriesVO.MetricPointVO(p.time(), p.value()));
            }
            return new SkywalkingMetricSeriesVO(series.name(), series.scope().name(),
                    series.label(), series.unit(), points);
        });
    }

    @Override
    public List<SkywalkingAlarmVO> alarms(Long datasourceId, OtnlQuery query) {
        return withEngine(datasourceId, engine -> {
            List<OtnlAlarm> alarms = engine.alarms(query);
            List<SkywalkingAlarmVO> result = new ArrayList<>(alarms.size());
            for (OtnlAlarm a : alarms) {
                result.add(new SkywalkingAlarmVO(a.id(), a.name(), a.message(),
                        a.startTime(), a.scope(), a.serviceName(), a.ruleName(),
                        a.events(), toTagList(a.tags())));
            }
            return result;
        });
    }

    @Override
    public List<SkywalkingLogRecordVO> logs(Long datasourceId, OtnlQuery query) {
        return withEngine(datasourceId, engine -> {
            List<OtnlLogRecord> logs = engine.logs(query);
            List<SkywalkingLogRecordVO> result = new ArrayList<>(logs.size());
            for (OtnlLogRecord l : logs) {
                result.add(new SkywalkingLogRecordVO(l.timestamp(), l.serviceName(),
                        l.instanceName(), l.endpointName(), l.traceId(), l.level(),
                        l.contentType(), l.content(), toTagList(l.tags())));
            }
            return result;
        });
    }

    @Override
    public SkywalkingStatusVO status(Long datasourceId) {
        return withEngine(datasourceId, engine -> {
            OtnlStatus status = engine.status();
            return new SkywalkingStatusVO(status.name(), status.version(),
                    status.available(), status.enabled(), status.endpoint(), status.mode(),
                    status.capabilities(), status.message(), status.checkedAt());
        });
    }

    // ==================== 内部：数据源与引擎 ====================

    /**
     * 解析数据源：按 ID 取并校验类型，缺省时挑第一个 skywalking 数据源。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 数据源配置
     */
    private DataSourceVO requireDatasource(Long datasourceId) {
        if (datasourceId != null && datasourceId > 0L) {
            ReturnResult<DataSourceVO> result = datasourceService.get(datasourceId);
            if (result == null || !result.isOk() || result.getData() == null) {
                throw new IllegalStateException("数据源 #" + datasourceId + " 不存在");
            }
            DataSourceVO ds = result.getData();
            if (!DS_TYPE.equalsIgnoreCase(trim(ds.type()))) {
                throw new IllegalStateException("数据源 #" + ds.id() + " 是 " + ds.type()
                        + " 类型，无法用于 SkyWalking 管理");
            }
            return ds;
        }
        ReturnResult<List<DataSourceVO>> list = datasourceService.list();
        if (list != null && list.getData() != null) {
            for (DataSourceVO ds : list.getData()) {
                if (DS_TYPE.equalsIgnoreCase(trim(ds.type()))) {
                    return ds;
                }
            }
        }
        throw new IllegalStateException("尚未登记 skywalking 数据源，请先在「数据源管理」新增");
    }

    /**
     * OAP 地址：database 填完整 URL 时原样使用；否则 host + port（来自数据源默认端口）。
     *
     * @param ds 数据源
     * @return 地址，形如 {@code http://127.0.0.1:12800}
     */
    private static String oapUrl(DataSourceVO ds) {
        String database = trim(ds.database());
        if (database.startsWith("http://") || database.startsWith("https://")) {
            return database;
        }
        String host = trim(ds.host());
        if (host.isEmpty() || "local".equalsIgnoreCase(host)) {
            host = "127.0.0.1";
        }
        int port = DatasourceHubConstants.DS_DEFAULT_PORTS.get(DS_TYPE);
        if (ds.port() != null && ds.port() > 0) {
            port = ds.port();
        }
        return "http://" + host + ":" + port;
    }

    /**
     * 按数据源行重新写入进程级设置并新建引擎实例。
     * <p>
     * 不必单例持有引擎：{@code SkywalkingOapClient} 会在 OAP 地址变更时自动释放旧连接，
     * 新的引擎实例使用新的地址建连。
     *
     * @param ds 数据源
     * @return 引擎
     */
    private static SkywalkingOtnlEngine engine(DataSourceVO ds) {
        SkywalkingSettings.apply(oapUrl(ds), ds.username(), ds.password(),
                TIMEOUT_MS, ds.status() != null && ds.status() == 1);
        return new SkywalkingOtnlEngine();
    }

    /**
     * 取当前数据源行对应的引擎执行查询。数据源行解析失败（未登记 / 不匹配）时抛出
     * {@link IllegalStateException}，由控制器 {@code call()} 翻成错误信封，而非 500。
     *
     * @param datasourceId 数据源 ID，可空
     * @param work         业务动作
     * @param <T>          返回类型
     * @return 查询结果
     */
    private <T> T withEngine(Long datasourceId, Function<SkywalkingOtnlEngine, T> work) {
        return work.apply(engine(requireDatasource(datasourceId)));
    }

    // ==================== 状态/计数器的静默查询 ====================

    private static OtnlStatus statusSilently(SkywalkingOtnlEngine engine) {
        try {
            return engine.status();
        } catch (Exception e) {
            log.warn("[skywalking] 状态探测失败: {}", e.getMessage());
            return OtnlStatus.unavailable("skywalking", "状态探测失败: " + e.getMessage());
        }
    }

    private static List<OtnlAlarm> alarmSilently(SkywalkingOtnlEngine engine) {
        try {
            return engine.alarms(OtnlQuery.empty());
        } catch (Exception e) {
            log.warn("[skywalking] 告警统计失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static int serviceCountSilently(SkywalkingOtnlEngine engine) {
        try {
            return engine.services(OtnlQuery.empty()).size();
        } catch (Exception e) {
            log.warn("[skywalking] 服务统计失败: {}", e.getMessage());
            return 0;
        }
    }

    private static int recentAlarmCountSilently(SkywalkingOtnlEngine engine) {
        try {
            OtnlQuery query = OtnlQuery.lastMinutes(RECENT_ALARM_MINUTES);
            return engine.alarms(query).size();
        } catch (Exception e) {
            log.warn("[skywalking] 最近告警统计失败: {}", e.getMessage());
            return 0;
        }
    }

    // ==================== 工具 ====================

    private static List<TagVO> toTagList(List<OtnlTag> tags) {
        if (tags == null) {
            return List.of();
        }
        List<TagVO> result = new ArrayList<>(tags.size());
        for (OtnlTag t : tags) {
            result.add(new TagVO(t.key(), t.value()));
        }
        return result;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
