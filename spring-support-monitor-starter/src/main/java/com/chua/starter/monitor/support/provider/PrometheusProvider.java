package com.chua.starter.monitor.support.provider;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.prometheus.support.client.PrometheusClient;
import com.chua.prometheus.support.model.PrometheusMetric;
import com.chua.prometheus.support.model.QueryResult;
import com.chua.starter.monitor.support.enums.ProviderType;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.service.MonitorConfigService;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.monitor.support.spi.MetricProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prometheus 采集 Provider
 * <p>
 * 基于 {@link PrometheusClient} 拉取 Prometheus 指标, 支持服务器 / Redis / 数据库等
 * 任意已接入 Prometheus 的目标。Prometheus 地址取自全局配置
 * {@code monitor.prometheus.url}。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
@Slf4j
@Component
public class PrometheusProvider implements MetricProvider {

    /**
     * Prometheus 地址配置项
     */
    private static final String CONFIG_KEY_URL = "monitor.prometheus.url";

    /**
     * Prometheus 客户端提供者
     */
    private final ObjectProvider<PrometheusClient> clientProvider;

    /**
     * 全局配置 Service
     */
    private final MonitorConfigService configService;

    /**
     * 构造方法
     *
     * @param clientProvider Prometheus 客户端提供者
     * @param configService  全局配置 Service
     */
    @Autowired
    public PrometheusProvider(ObjectProvider<PrometheusClient> clientProvider,
                              MonitorConfigService configService) {
        this.clientProvider = clientProvider;
        this.configService = configService;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.PROMETHEUS;
    }

    @Override
    public Set<ServiceType> supportServiceTypes() {
        return Set.of(ServiceType.SERVER, ServiceType.REDIS, ServiceType.MYSQL,
                ServiceType.PG, ServiceType.ORACLE, ServiceType.MSSQL, ServiceType.MQ);
    }

    @Override
    public Mono<CollectResult> collect(CollectContext context) {
        return Mono.fromCallable(() -> doCollect(context))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 同步执行 PromQL 查询
     */
    private CollectResult doCollect(CollectContext context) {
        CollectResult result = new CollectResult();
        result.setTask(context.getTask());
        result.setMonitor(context.getMonitor());

        JsonObject config = Json.getJsonObject(context.getTask().getCollectConfig());
        JsonArray promqls = config.getJsonArray("promqls");
        if (promqls == null || promqls.isEmpty()) {
            return result.fail("collect_config 缺少 promqls");
        }

        PrometheusClient client = clientProvider.getIfAvailable();
        if (client == null) {
            return result.fail("utils-support-prometheus-starter 未启用或未配置地址");
        }

        try {
            promqls.forEach(promqlObj -> {
                String promql = promqlObj == null ? null : String.valueOf(promqlObj);
                if (promql == null || promql.isEmpty()) {
                    return;
                }
                QueryResult queryResult = client.query(promql).execute();
                parseQueryResult(queryResult, promql, result);
            });
            result.setSuccess(true);
        } catch (Exception e) {
            log.error("[PrometheusProvider] PromQL 查询失败", e);
            result.fail(e.getMessage());
        }
        return result;
    }

    /**
     * 解析查询结果为指标点
     */
    private void parseQueryResult(QueryResult queryResult, String promql, CollectResult result) {
        if (queryResult == null || !queryResult.hasData()) {
            return;
        }
        List<PrometheusMetric> metrics = queryResult.getResult();
        for (PrometheusMetric m : metrics) {
            Map<String, String> tags = new HashMap<>();
            if (m.getMetric() != null) {
                tags.putAll(m.getMetric());
            }
            Double value = m.getValue();
            if (value == null && m.getValues() != null && !m.getValues().isEmpty()) {
                value = m.getValues().get(m.getValues().size() - 1).value();
            }
            if (value == null) {
                continue;
            }
            result.addPoint(MetricPoint.builder()
                    .metricName(m.getName().isEmpty() ? promql : m.getName())
                    .value(value)
                    .unit("")
                    .tags(tags)
                    .targetTable("ms_monitor_metric")
                    .build());
        }
    }
}