package com.chua.starter.apm.support.web;

import com.chua.common.support.apm.OtnlEngine;
import com.chua.common.support.apm.OtnlMetricQuery;
import com.chua.common.support.apm.OtnlQuery;
import com.chua.common.support.apm.OtnlScope;
import com.chua.common.support.apm.OtnlTraceState;
import com.chua.common.support.apm.model.OtnlHealth;
import com.chua.common.support.apm.model.OtnlTopology;
import com.chua.starter.apm.support.ApmRegistry;
import com.chua.starter.apm.support.model.ApmDescriptor;
import com.chua.starter.apm.support.properties.ApmProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * APM 统一接口路由器。
 * <p>
 * 嵌入（Servlet）与独立（Server）两种启动方式共用本类，因此这里的每个分支
 * 都是「协议无关」的：不碰 {@code HttpServletRequest}，也不碰 {@code ServerRequest}，
 * 只吃 {@link ApmRequest} 吐 {@link ApmResult}。
 * </p>
 * <h3>接口清单（相对 {@code plugin.apm.path}，默认 {@code /api/apm/v1}）</h3>
 * <table border="1">
 *   <caption>APM REST 接口</caption>
 *   <tr><th>路径</th><th>说明</th></tr>
 *   <tr><td>{@code GET /status}</td><td>是否存在/启用了 APM（前端首屏判据）</td></tr>
 *   <tr><td>{@code GET /capabilities}</td><td>当前引擎支持的能力清单</td></tr>
 *   <tr><td>{@code GET /overview}</td><td>健康度 + 描述（首页卡片）</td></tr>
 *   <tr><td>{@code GET /services}</td><td>服务目录</td></tr>
 *   <tr><td>{@code GET /instances?service=}</td><td>服务实例</td></tr>
 *   <tr><td>{@code GET /endpoints?service=&keyword=}</td><td>端点</td></tr>
 *   <tr><td>{@code GET /traces?service=&state=&pageNum=&pageSize=}</td><td>链路列表</td></tr>
 *   <tr><td>{@code GET /trace/{traceId}}</td><td>链路详情（含 Span）</td></tr>
 *   <tr><td>{@code GET /topology?service=}</td><td>服务拓扑</td></tr>
 *   <tr><td>{@code GET /topology/multi?services=a,b}</td><td>多服务合并拓扑（对比页）</td></tr>
 *   <tr><td>{@code GET /metrics?name=&scope=&service=&instance=&endpoint=}</td><td>指标时序</td></tr>
 *   <tr><td>{@code GET /metric-definitions?scope=}</td><td>指标定义（下拉框）</td></tr>
 *   <tr><td>{@code GET /alarms?keyword=}</td><td>告警</td></tr>
 *   <tr><td>{@code GET /alerts?keyword=}</td><td>告警（{@code /alarms} 的别名，前端 Alert 页语义）</td></tr>
 *   <tr><td>{@code GET /logs?service=&keyword=}</td><td>日志</td></tr>
 *   <tr><td>{@code GET /profiles?service=&endpoint=}</td><td>性能剖析任务列表</td></tr>
 *   <tr><td>{@code GET /profiles/{taskId}/segments}</td><td>剖析任务采样到的链路片段</td></tr>
 *   <tr><td>{@code GET /profiles/{taskId}/analysis}</td><td>剖析结果（线程栈树）</td></tr>
 *   <tr><td>{@code GET /metrics-batch?names=&services=&scope=}</td><td>批量指标（多实体对比）</td></tr>
 *   <tr><td>{@code GET /infra-3d?minutes=}</td><td>全局拓扑 + 分层信息（3D 基础设施图）</td></tr>
 * </table>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class ApmDispatcher {

    /**
     * 默认时间窗口（分钟）
     */
    private static final long DEFAULT_WINDOW_MINUTES = 30L;

    /**
     * 链路列表路径前缀
     */
    private static final String TRACE_DETAIL_PREFIX = "/trace/";

    /**
     * 剖析片段路径前缀（{@code /profiles/{taskId}/segments}）
     */
    private static final String PROFILE_SEGMENTS_PREFIX = "/profiles/";

    /**
     * 剖析片段路径后缀
     */
    private static final String PROFILE_SEGMENTS_SUFFIX = "/segments";

    /**
     * 剖析分析路径前缀（与片段共用 {@code /profiles/} 前缀）
     */
    private static final String PROFILE_ANALYSIS_PREFIX = "/profiles/";

    /**
     * 剖析分析路径后缀
     */
    private static final String PROFILE_ANALYSIS_SUFFIX = "/analysis";

    /**
     * 批量指标单次最多返回的曲线数（对比页上限，防止误点出几百条）
     */
    private static final int MAX_BATCH_SERIES = 24;

    private final ApmRegistry registry;
    private final ApmProperties properties;

    /**
     * 构造路由器
     *
     * @param registry   注册表
     * @param properties 配置
     */
    public ApmDispatcher(ApmRegistry registry, ApmProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    /**
     * 分发请求
     *
     * @param request 请求
     * @return 结果
     */
    public ApmResult dispatch(ApmRequest request) {
        String method = request.method() == null ? "GET" : request.method().toUpperCase(Locale.ROOT);
        String path = suffix(request.path());
        if (!"GET".equals(method) && !"POST".equals(method)) {
            return ApmResult.fail("仅支持 GET / POST，当前为 " + method);
        }
        if ("/status".equals(path) || "/".equals(path)) {
            // 状态接口必须永远可用，哪怕没有任何 APM
            return ApmResult.ok(registry.describe());
        }
        if ("/capabilities".equals(path)) {
            ApmProviderHolder holder = requireEngine();
            if (holder.error() != null) {
                return ApmResult.fail(holder.error());
            }
            Map<String, Object> payload = new LinkedHashMap<>(4);
            payload.put("engine", holder.providerName());
            payload.put("capabilities", holder.engine().capabilities());
            return ApmResult.ok(payload);
        }
        if ("/overview".equals(path)) {
            ApmProviderHolder holder = requireEngine();
            if (holder.error() != null) {
                return ApmResult.fail(holder.error());
            }
            Map<String, Object> payload = new LinkedHashMap<>(8);
            payload.put("descriptor", registry.describe());
            payload.put("health", holder.engine().health(query(request.params())));
            return ApmResult.ok(payload);
        }
        if ("/services".equals(path)) {
            return withEngine(OtnlEngine.CAP_SERVICES,
                    engine -> engine.services(query(request.params())));
        }
        if ("/instances".equals(path)) {
            return withEngine(OtnlEngine.CAP_INSTANCES,
                    engine -> engine.instances(required(request, "service"), query(request.params())));
        }
        if ("/endpoints".equals(path)) {
            return withEngine(OtnlEngine.CAP_ENDPOINTS,
                    engine -> engine.endpoints(required(request, "service"), query(request.params())));
        }
        if ("/traces".equals(path)) {
            return withEngine(OtnlEngine.CAP_TRACES,
                    engine -> engine.traces(query(request.params())));
        }
        if (path.startsWith(TRACE_DETAIL_PREFIX)) {
            String traceId = path.substring(TRACE_DETAIL_PREFIX.length());
            if (traceId.isBlank()) {
                return ApmResult.fail("缺少 traceId");
            }
            return withEngine(OtnlEngine.CAP_TRACES, engine -> {
                Object trace = engine.trace(traceId);
                if (trace == null) {
                    throw new IllegalStateException("链路不存在或已过期: " + traceId);
                }
                return trace;
            });
        }
        if ("/topology".equals(path)) {
            return withEngine(OtnlEngine.CAP_TOPOLOGY,
                    engine -> engine.topology(query(request.params())));
        }
        if ("/topology/multi".equals(path)) {
            return withEngine(OtnlEngine.CAP_TOPOLOGY, engine -> {
                List<String> names = split(request.params(), "services");
                if (names.isEmpty()) {
                    throw new IllegalArgumentException("缺少必填参数: services");
                }
                return engine.multiTopology(names, query(request.params()));
            });
        }
        if ("/metrics".equals(path)) {
            return withEngine(OtnlEngine.CAP_METRICS, engine -> {
                String name = required(request, "name");
                OtnlMetricQuery metricQuery = metricQuery(name, request.params());
                return engine.metrics(metricQuery);
            });
        }
        if ("/metric-definitions".equals(path)) {
            ApmProviderHolder holder = requireEngine();
            if (holder.error() != null) {
                return ApmResult.fail(holder.error());
            }
            return ApmResult.ok(holder.engine().metricDefinitions(scope(request.params())));
        }
        if ("/alarms".equals(path)) {
            return withEngine(OtnlEngine.CAP_ALARMS,
                    engine -> engine.alarms(query(request.params())));
        }
        if ("/alerts".equals(path)) {
            // 前端「告警分析」页与本接口同源；单独开别名是为了让能力判断能落到 CAP_ALERTS
            return withEngine(OtnlEngine.CAP_ALERTS,
                    engine -> engine.alarms(query(request.params())));
        }
        if ("/logs".equals(path)) {
            return withEngine(OtnlEngine.CAP_LOGS,
                    engine -> engine.logs(query(request.params())));
        }
        if ("/profiles".equals(path)) {
            return withEngine(OtnlEngine.CAP_PROFILES,
                    engine -> engine.profileTasks(query(request.params())));
        }
        if (path.startsWith(PROFILE_SEGMENTS_PREFIX) && path.endsWith(PROFILE_SEGMENTS_SUFFIX)) {
            String taskId = middle(path, PROFILE_SEGMENTS_PREFIX, PROFILE_SEGMENTS_SUFFIX);
            if (taskId.isBlank()) {
                return ApmResult.fail("缺少剖析任务 ID");
            }
            return withEngine(OtnlEngine.CAP_PROFILES, engine -> engine.profiledSegments(taskId));
        }
        if (path.startsWith(PROFILE_ANALYSIS_PREFIX) && path.endsWith(PROFILE_ANALYSIS_SUFFIX)) {
            String taskId = middle(path, PROFILE_ANALYSIS_PREFIX, PROFILE_ANALYSIS_SUFFIX);
            if (taskId.isBlank()) {
                return ApmResult.fail("缺少剖析任务 ID");
            }
            return withEngine(OtnlEngine.CAP_PROFILES, engine -> engine.profileAnalysis(taskId));
        }
        if ("/metrics-batch".equals(path)) {
            return withEngine(OtnlEngine.CAP_METRICS, engine -> metricsBatch(engine, request.params()));
        }
        if ("/infra-3d".equals(path)) {
            return withEngine(OtnlEngine.CAP_TOPOLOGY, engine -> infra3d(engine, request.params()));
        }
        return ApmResult.fail("未知的 APM 接口: " + path);
    }

    /**
     * 批量指标：指标名 × 实体 的笛卡尔组合（对比页一次拿多曲线）
     * <p>
     * 两个参数都可传多个（逗号分隔）：{@code names=service_cpm,service_resp_time&services=a,b}
     * 会得到 4 条曲线。上限 {@value #MAX_BATCH_SERIES} 条，防止一次点选把 OAP 打穿。
     * </p>
     *
     * @param engine 引擎
     * @param params 参数
     * @return 时序列表
     */
    private Object metricsBatch(OtnlEngine engine, Map<String, String> params) {
        List<String> names = split(params, "names");
        if (names.isEmpty()) {
            String single = text(params, "name");
            if (single != null) {
                names = List.of(single);
            }
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("缺少必填参数: names");
        }
        List<String> services = split(params, "services");
        if (services.isEmpty()) {
            String single = text(params, "service");
            services = List.of(single == null ? "" : single);
        }
        OtnlQuery base = query(params);
        OtnlScope scope = scope(params);
        String instance = base.instanceName();
        String endpoint = base.endpointName();
        List<OtnlMetricQuery> queries = new ArrayList<>();
        outer:
        for (String name : names) {
            for (String service : services) {
                queries.add(new OtnlMetricQuery(name, scope,
                        service == null || service.isBlank() ? null : service,
                        instance, endpoint,
                        base.startTime(), base.endTime(),
                        base.step() == null || base.step().isBlank() ? "MINUTE" : base.step()));
                if (queries.size() >= MAX_BATCH_SERIES) {
                    break outer;
                }
            }
        }
        return engine.metricsBatch(queries);
    }

    /**
     * 3D 基础设施图数据：全局拓扑 + 按层聚合
     * <p>
     * 只做一次 {@code getGlobalTopology}，把每个节点归到它的第一个 layer 上；
     * 图上的坐标一律由前端算（后端给不出有物理意义的坐标），这里只保证
     * 「同一层的节点进同一个分组」。
     * </p>
     *
     * @param engine 引擎
     * @param params 参数
     * @return 3D 图数据
     */
    private Object infra3d(OtnlEngine engine, Map<String, String> params) {
        OtnlTopology topology = engine.topology(query(params).withService(null));
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Integer> layerCount = new LinkedHashMap<>();
        if (topology != null && topology.nodes() != null) {
            for (OtnlTopology.OtnlTopologyNode node : topology.nodes()) {
                String layer = node.layer() == null || node.layer().isBlank() ? "UNKNOWN" : node.layer();
                layerCount.merge(layer, 1, Integer::sum);
                Map<String, Object> item = new LinkedHashMap<>(8);
                item.put("id", node.id());
                item.put("name", node.name());
                item.put("type", node.type());
                item.put("layer", layer);
                item.put("real", node.real());
                nodes.add(item);
            }
        }
        List<Map<String, Object>> layers = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : layerCount.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>(2);
            item.put("name", entry.getKey());
            item.put("count", entry.getValue());
            layers.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>(4);
        payload.put("nodes", nodes);
        payload.put("calls", topology == null || topology.calls() == null ? List.of() : topology.calls());
        payload.put("layers", layers);
        return payload;
    }

    /**
     * 取路径中间段（{@code /profiles/{id}/analysis} → {@code {id}}）
     *
     * @param path   路径
     * @param prefix 前缀
     * @param suffix 后缀
     * @return 中间段
     */
    private String middle(String path, String prefix, String suffix) {
        return path.substring(prefix.length(), Math.max(prefix.length(), path.length() - suffix.length()));
    }

    /**
     * 逗号分隔参数
     *
     * @param params 参数
     * @param name   参数名
     * @return 非空值列表
     */
    private List<String> split(Map<String, String> params, String name) {
        String value = text(params, name);
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 去掉统一前缀，得到接口相对路径
     *
     * @param rawPath 原始路径
     * @return 相对路径
     */
    private String suffix(String rawPath) {
        String path = rawPath == null ? "" : rawPath;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        String base = properties.getPath() == null ? "" : properties.getPath().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.isEmpty() && path.startsWith(base)) {
            path = path.substring(base.length());
        }
        if (path.isEmpty()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 引擎 + 提供者（内部载体）
     *
     * @param engine       引擎，null 表示不可用
     * @param providerName 提供者名称
     * @param error        不可用原因，null 表示可用
     */
    private record ApmProviderHolder(OtnlEngine engine, String providerName, String error) {
    }

    /**
     * 取引擎，失败时返回带原因的结果
     *
     * @return 载体
     */
    private ApmProviderHolder requireEngine() {
        ApmDescriptor descriptor = registry.describe();
        if (!descriptor.present()) {
            return new ApmProviderHolder(null, null, "APM 未启用：" + descriptor.message());
        }
        OtnlEngine engine = registry.engine();
        if (engine == null) {
            return new ApmProviderHolder(null, null, "APM 未启用：引擎不可用");
        }
        return new ApmProviderHolder(engine, descriptor.name(), null);
    }

    /**
     * 带能力的引擎调用
     *
     * @param capability 所需能力
     * @param action     动作
     * @return 结果
     */
    private ApmResult withEngine(String capability, EngineAction action) {
        ApmProviderHolder holder = requireEngine();
        if (holder.error() != null) {
            return ApmResult.fail(holder.error());
        }
        if (!holder.engine().supports(capability)) {
            return ApmResult.fail("引擎 " + holder.providerName() + " 不支持 " + capability);
        }
        try {
            return ApmResult.ok(action.apply(holder.engine()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApmResult.fail(e.getMessage());
        } catch (Exception e) {
            log.debug("[APM] 调用 {} 失败: {}", capability, e.getMessage());
            return ApmResult.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 引擎动作
     */
    @FunctionalInterface
    private interface EngineAction {
        /**
         * 执行
         *
         * @param engine 引擎
         * @return 数据
         */
        Object apply(OtnlEngine engine);
    }

    /**
     * 必填参数
     *
     * @param request 请求
     * @param name    参数名
     * @return 值
     */
    private String required(ApmRequest request, String name) {
        String value = request.param(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填参数: " + name);
        }
        return value;
    }

    /**
     * 通用查询条件
     *
     * @param params 参数
     * @return 条件
     */
    private OtnlQuery query(Map<String, String> params) {
        Map<String, String> safe = params == null ? Map.of() : params;
        long minutes = number(safe, "minutes", DEFAULT_WINDOW_MINUTES);
        long end = number(safe, "endTime", 0L);
        long start = number(safe, "startTime", 0L);
        if (end <= 0) {
            end = System.currentTimeMillis();
        }
        if (start <= 0) {
            start = end - Math.max(1L, minutes) * 60_000L;
        }
        return new OtnlQuery(
                text(safe, "service"),
                text(safe, "instance"),
                text(safe, "endpoint"),
                text(safe, "traceId"),
                text(safe, "keyword"),
                traceState(text(safe, "state")),
                start, end,
                text(safe, "step"),
                (int) number(safe, "pageNum", 1L),
                (int) number(safe, "pageSize", 20L),
                "true".equalsIgnoreCase(text(safe, "orderByDuration")));
    }

    /**
     * 指标查询条件
     *
     * @param name   指标名
     * @param params 参数
     * @return 条件
     */
    private OtnlMetricQuery metricQuery(String name, Map<String, String> params) {
        OtnlQuery base = query(params);
        return new OtnlMetricQuery(name, scope(params),
                base.serviceName(), base.instanceName(), base.endpointName(),
                base.startTime(), base.endTime(),
                base.step() == null || base.step().isBlank() ? "MINUTE" : base.step());
    }

    /**
     * 作用域
     *
     * @param params 参数
     * @return 作用域
     */
    private OtnlScope scope(Map<String, String> params) {
        String value = text(params, "scope");
        if (value == null) {
            return OtnlScope.SERVICE;
        }
        try {
            return OtnlScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OtnlScope.SERVICE;
        }
    }

    /**
     * 链路状态
     *
     * @param value 值
     * @return 状态
     */
    private OtnlTraceState traceState(String value) {
        if (value == null) {
            return OtnlTraceState.ALL;
        }
        try {
            return OtnlTraceState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OtnlTraceState.ALL;
        }
    }

    /**
     * 取字符串参数
     *
     * @param params 参数
     * @param name   参数名
     * @return 值（空串归一为 null）
     */
    private String text(Map<String, String> params, String name) {
        String value = params.get(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 取数字参数
     *
     * @param params       参数
     * @param name         参数名
     * @param defaultValue 默认值
     * @return 值
     */
    private long number(Map<String, String> params, String name, long defaultValue) {
        String value = text(params, name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
