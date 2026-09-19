package com.chua.starter.monitor.support.scheduler;

import com.chua.common.support.task.scheduler.CronTrigger;
import com.chua.common.support.task.scheduler.Trigger;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorMetricConfig;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import com.chua.starter.monitor.support.enums.ProviderType;
import com.chua.starter.monitor.support.enums.ServiceType;
import com.chua.starter.monitor.support.registry.ProviderRegistry;
import com.chua.starter.monitor.support.service.MetricService;
import com.chua.starter.monitor.support.service.MonitorConfigService;
import com.chua.starter.monitor.support.service.MonitorMetricConfigService;
import com.chua.starter.monitor.support.service.MonitorService;
import com.chua.starter.monitor.support.service.MonitorTaskService;
import com.chua.starter.monitor.support.spi.CollectContext;
import com.chua.starter.monitor.support.spi.CollectResult;
import com.chua.starter.monitor.support.spi.MetricPoint;
import com.chua.starter.monitor.support.spi.MetricProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 监控采集调度器
 * <p>
 * 每 10 秒扫描启用的任务, 通过 Trigger 判断任务是否到达触发时间,
 * 使用 Reactor 背压控制采集并发与入库压力。
 * 支持从配置读取调度间隔, 应用 ms监控指标配置 解析规则将原始输出转为结构化指标。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorScheduler {

    /** 默认调度间隔(毫秒) */
    private static final long DEFAULT_INTERVAL_MS = 10_000L;
    /** 最大并发数 */
    private static final int CONCURRENCY = 50;
    /** 调度间隔配置键 */
    private static final String CONFIG_KEY_INTERVAL = "monitor.collect.interval.ms";

    /** 任务 服务 */
    private final MonitorTaskService taskService;
    /** 资产 服务 */
    private final MonitorService monitorService;
    /** 指标 服务 */
    private final MetricService metricService;
    /** 配置 服务 */
    private final MonitorConfigService configService;
    /** 指标配置 服务 */
    private final MonitorMetricConfigService metricConfigService;
    /** 提供者 注册中心 */
    private final ProviderRegistry providerRegistry;
    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    /** 上次调度时间(nano时间), 用于间隔控制 */
    private final AtomicLong lastTickTime = new AtomicLong(0);

    /**
     * 调度入口, 每 10 秒扫描一次, 根据配置的间隔决定是否执行采集
     */
    @Scheduled(fixedRate = 10_000)
    public void tick() {
        long intervalMs = configService.getLong(CONFIG_KEY_INTERVAL, DEFAULT_INTERVAL_MS);
        long now = System.nanoTime();
        long last = lastTickTime.get();
        if (last != 0 && (now - last) / 1_000_000 < intervalMs) {
            return;
        }
        lastTickTime.set(now);

        List<MsMonitorTask> tasks = taskService.listEnabled().getData();
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        LocalDateTime nowTime = LocalDateTime.now();
        Flux.fromIterable(tasks)
                .filter(task -> isTriggered(task, nowTime))
                .flatMap(task -> execute(task), CONCURRENCY)
                .onBackpressureBuffer(10_000)
                .subscribe(
                        result -> log.debug("[MonitorScheduler] 采集完成 task={} points={}",
                                result.getTask().getId(), result.getPoints().size()),
                        error -> log.error("[MonitorScheduler] 采集异常", error)
                );
    }

    /**
     * 小时聚合, 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void aggregateHourly() {
        metricService.aggregateHourly();
    }

    /**
     * 数据保留清理, 每天凌晨 3 点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldData() {
        int retentionDays = configService.getInt("monitor.data.retention.days", 30);
        metricService.cleanupOldData(retentionDays);
    }

    /**
     * 立即执行一次采集（手动触发, 忽略 cron 与启用状态, 异步执行）
     *
     * @param task 任务
     */
    public void triggerNow(MsMonitorTask task) {
        execute(task).subscribe(
                result -> log.info("[MonitorScheduler] 手动采集完成 task={} points={}",
                        task.getId(), result.getPoints().size()),
                error -> log.error("[MonitorScheduler] 手动采集异常 task={}", task.getId(), error)
        );
    }

    /**
     * 判断任务是否到达触发时间
     *
     * @param task 任务
     * @param now  当前时间
     * @return true 表示应触发
     */
    private boolean isTriggered(MsMonitorTask task, LocalDateTime now) {
        String cron = task.getMonitorTaskCron();
        if (cron == null || cron.isEmpty()) {
            return false;
        }
        try {
            Trigger trigger = new CronTrigger(cron);
            LocalDateTime lastRun = task.getLastRunTime();
            if (lastRun == null) {
                return true;
            }
            LocalDateTime next = trigger.nextExecutionTime(lastRun);
            return next != null && !next.isAfter(now);
        } catch (Exception e) {
            log.warn("[MonitorScheduler] cron 解析失败 task={} cron={}", task.getId(), cron);
            return false;
        }
    }

    /**
     * 执行采集任务
     * <p>
     * 无论执行成功、失败或提前跳过，均会标记 最后一个运行时间，
     * 防止 是否triggered 在后续 tick 中被重复判定为真（避免任务刷屏）。
     * </p>
     *
     * @param task 任务
     * @return 响应式采集结果
     */
    private reactor.core.publisher.Mono<CollectResult> execute(MsMonitorTask task) {
        ServiceType serviceType = ServiceType.of(task.getServiceType());
        ProviderType providerType = ProviderType.of(task.getProvider());
        Optional<MetricProvider> providerOpt = providerRegistry.resolveMetric(serviceType, providerType);
        if (providerOpt.isEmpty()) {
            log.warn("[MonitorScheduler] 未找到 Provider service={} provider={} task={}",
                    serviceType, providerType, task.getId());
 // 提供者 缺失属正常兜底（如本地无对应采集器），标记完成防止反复触发
            taskService.markLastRunTime(task.getId());
            return reactor.core.publisher.Mono.empty();
        }
        MsMonitor monitor = monitorService.findById(task.getMonitorId()).getData();
        if (monitor == null) {
            log.warn("[MonitorScheduler] 主机不存在 task={} monitorId={}", task.getId(), task.getMonitorId());
            taskService.markLastRunTime(task.getId());
            return reactor.core.publisher.Mono.empty();
        }
        CollectContext context = CollectContext.builder()
                .task(task)
                .monitor(monitor)
                .build();
        return providerOpt.get().collect(context)
                .doOnSuccess(result -> {
 // 成功：标记时间 + 解析规则 + 写入指标（含 influxdb 双写）
                    taskService.markLastRunTime(task.getId());
                    /**
                     * apply解析rules。
                     * @param task.getId( 任务.获取标识(
                     */
                    applyParseRules(task.getId(), result);
                    metricService.writeCollectResult(result);
                })
                .doOnError(error -> {
                    log.error("[MonitorScheduler] 采集失败 task={} error={}", task.getId(), error.getMessage());
                    // 失败也要标记，防止每个 tick 反复尝试同一个注定失败的任务
                    taskService.markLastRunTime(task.getId());
                })
                .doOnTerminate(() -> {
 // 兜底：Mono.空() 路径（提供者/监控 缺失）不触发 执行on成功/执行on错误，
 // 但 是否triggered 已在调用前通过，需保证时间被更新。
 // 此处仅作为保险，正常路径已在 执行on成功 中完成标记。
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 应用 ms监控指标配置 解析规则, 将原始输出转为结构化指标点
     *
     * @param taskId 任务 标识
     * @param result 采集结果
     */
    private void applyParseRules(Long taskId, CollectResult result) {
        try {
            List<MsMonitorMetricConfig> rules = metricConfigService.listByTask(taskId).getData();
            if (rules == null || rules.isEmpty() || result.getRawOutput() == null) {
                return;
            }
            String rawOutput = result.getRawOutput();
            for (MsMonitorMetricConfig rule : rules) {
                List<MetricPoint> parsed = parseByRule(rule, rawOutput);
                if (parsed != null) {
                    parsed.forEach(result::addPoint);
                }
            }
        } catch (Exception e) {
            log.warn("[MonitorScheduler] 解析规则应用失败 taskId={}", taskId, e);
        }
    }

    /**
     * 根据单条解析规则从原始输出中提取指标点
     *
     * @param rule      解析规则
     * @param rawOutput 原始输出
     * @return 指标点列表
     */
    private List<MetricPoint> parseByRule(MsMonitorMetricConfig rule, String rawOutput) {
        String parseRule = rule.getParseRule();
        if (parseRule == null || parseRule.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> ruleMap = objectMapper.readValue(parseRule,
                    new TypeReference<Map<String, Object>>() {});
            String type = (String) ruleMap.getOrDefault("type", "json");
            String expression = (String) ruleMap.get("expression");
            if (expression == null) {
                return Collections.emptyList();
            }
            List<MetricPoint> points = new ArrayList<>();
            if ("jsonpath".equals(type)) {
                points.addAll(parseJsonPath(rule, rawOutput, expression));
            } else if ("regex".equals(type)) {
                points.addAll(parseRegex(rule, rawOutput, expression));
            } else if ("json".equals(type)) {
                points.addAll(parseJsonFields(rule, rawOutput, expression));
            }
            return points;
        } catch (Exception e) {
            log.warn("[MonitorScheduler] 解析规则执行失败 ruleId={}", rule.getId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 按 json路径 表达式解析, 从原始输出 JSON 中提取指定路径的值
     * <p>规则格式: {"type":"jsonpath","expression":"$.cpu.usage"}</p>
     *
     * @param rule       解析规则
     * @param rawOutput  原始输出
     * @param expression json路径 表达式, 如 "$.cpu.usage"
     * @return 指标点列表
     */
    private List<MetricPoint> parseJsonPath(MsMonitorMetricConfig rule, String rawOutput, String expression) {
        try {
            String path = expression;
            if (path.startsWith("$.")) {
                path = path.substring(2);
            }
            Map<String, Object> root = objectMapper.readValue(rawOutput,
                    new TypeReference<Map<String, Object>>() {});
            Object value = resolveJsonPath(root, path);
            List<MetricPoint> points = new ArrayList<>();
            if (value instanceof Number) {
                points.add(MetricPoint.builder()
                        .metricName(rule.getMetricName())
                        .value(((Number) value).doubleValue())
                        .targetTable(rule.getTargetTable())
                        .build());
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        points.add(MetricPoint.builder()
                                .metricName(rule.getMetricName() + "_" + entry.getKey())
                                .value(((Number) entry.getValue()).doubleValue())
                                .targetTable(rule.getTargetTable())
                                .build());
                    }
                }
            }
            return points;
        } catch (Exception e) {
            log.warn("[MonitorScheduler] JSONPath 解析失败 ruleId={}", rule.getId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 按正则表达式解析, 从原始输出中提取匹配的数值
     * <p>规则格式: {"type":"regex","expression":"Cpu\\(s\\):\\s+(\\d+\\.\\d+)","group":1}</p>
     *
     * @param rule       解析规则
     * @param rawOutput  原始输出
     * @param expression 正则表达式, 必须包含捕获组
     * @return 指标点列表
     */
    private List<MetricPoint> parseRegex(MsMonitorMetricConfig rule, String rawOutput, String expression) {
        try {
            Pattern pattern = Pattern.compile(expression, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(rawOutput);
            List<MetricPoint> points = new ArrayList<>();
            int index = 0;
            while (matcher.find()) {
                for (int g = 1; g <= matcher.groupCount(); g++) {
                    String val = matcher.group(g);
                    if (val != null) {
                        try {
                            double num = Double.parseDouble(val);
                            String name = rule.getMetricName() + (matcher.groupCount() > 1 ? "_g" + g : "");
                            if (index > 0) {
                                name = name + "_" + index;
                            }
                            points.add(MetricPoint.builder()
                                    .metricName(name)
                                    .value(num)
                                    .targetTable(rule.getTargetTable())
                                    .build());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                index++;
            }
            return points;
        } catch (Exception e) {
            log.warn("[MonitorScheduler] 正则解析失败 ruleId={}", rule.getId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 按 JSON 字段路径解析, 从原始输出 JSON 中提取指定字段的值
     *
     * @param rule       解析规则
     * @param rawOutput  原始输出
     * @param expression 逗号分隔的字段路径, 如 "cpu.usage,mem.used"
     * @return 指标点列表
     */
    private List<MetricPoint> parseJsonFields(MsMonitorMetricConfig rule, String rawOutput, String expression) {
        try {
            Map<String, Object> root = objectMapper.readValue(rawOutput,
                    new TypeReference<Map<String, Object>>() {});
            String[] fields = expression.split(",");
            List<MetricPoint> points = new ArrayList<>();
            for (String field : fields) {
                field = field.trim();
                Object value = resolveJsonPath(root, field);
                if (value instanceof Number) {
                    MetricPoint pt = MetricPoint.builder()
                            .metricName(rule.getMetricName() + "_" + field.replace(".", "_"))
                            .value(((Number) value).doubleValue())
                            .targetTable(rule.getTargetTable())
                            .build();
                    points.add(pt);
                }
            }
            return points;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 按点号分隔的路径从 映射 中取值
     *
     * @param root 根 映射
     * @param path 点号分隔路径, 如 "cpu.usage"
     * @return 路径对应的值, 不存在返回 空
     */
    @SuppressWarnings("unchecked")
    private Object resolveJsonPath(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
