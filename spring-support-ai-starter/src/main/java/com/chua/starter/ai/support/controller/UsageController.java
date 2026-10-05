package com.chua.starter.ai.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.datasearch.usage.spi.UsageParser;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.entity.SysAiUsageSyncLog;
import com.chua.starter.ai.support.mapper.SysAiUsageSyncLogMapper;
import com.chua.starter.ai.support.model.AiUsageDTO;
import com.chua.starter.ai.support.model.vo.AiUsagePageVO;
import com.chua.starter.ai.support.model.vo.AiUsageParserVO;
import com.chua.starter.ai.support.model.vo.AiUsageStatisticsVO;
import com.chua.starter.ai.support.model.vo.AiUsageTaskStatusVO;
import com.chua.starter.ai.support.model.vo.AiUsageTrendPointVO;
import com.chua.starter.ai.support.schedule.UsageAggregationTask;
import com.chua.starter.ai.support.service.SysAiUsageService;
import com.chua.starter.ai.support.service.settings.AiUsageService;
import com.chua.starter.ai.support.service.UsagePricingReconService;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 用量控制器
 * 提供用量查询和统计接口
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/usage")
@RequiredArgsConstructor
@Tag(name = "AI 用量", description = "查询 AI 调用用量统计")
public class UsageController {

    /**
     * 用量服务
     */
    private final SysAiUsageService usageService;

    /**
     * 用量同步日志 映射器（持久化日志分页查询）
     */
    private final SysAiUsageSyncLogMapper syncLogMapper;

    /**
     * 用量维度聚合服务（导出与用量分布表共用同一口径）
     */
    private final AiUsageService usageSummaryService;

    /**
     * 用量/定价对账服务
     */
    private final UsagePricingReconService reconService;

    /**
     * Spring 应用上下文，用于收集容器中的用量解析器实现。
     */
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 分页查询 AI 用量明细。
     *
     * @param groupName 用量分组名称，为空时不限定
     * @param provider 服务商名称，为空时不限定
     * @param model 模型名称，为空时不限定
     * @param startTime 统计开始时间，为空时不限定
     * @param endTime 统计结束时间，为空时不限定
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return AI 用量分页结果
     */
    @Operation(summary = "用量列表", description = "分页查询用量明细；普通用户仅列本人明细，管理员列全量")
    @GetMapping("/list")
    public ReturnResult<AiUsagePageVO<SysAiUsage>> list(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SysAiUsage> p = usageService.list(groupName, provider, model, startTime, endTime,
                currentScopeUserId(), page, size);
        return ReturnResult.ok(AiUsagePageVO.of(p));
    }

    /**
     * 汇总查询 AI 用量统计。
     *
     * @param groupName 用量分组名称，为空时不限定
     * @param provider 服务商名称，为空时不限定
     * @param model 模型名称，为空时不限定
     * @param startTime 统计开始时间，为空时不限定
     * @param endTime 统计结束时间，为空时不限定
     * @return AI 用量统计结果
     */
    @Operation(summary = "用量统计", description = "汇总统计；普通用户仅统计本人明细，管理员统计全量")
    @GetMapping("/statistics")
    public ReturnResult<AiUsageStatisticsVO> statistics(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        SysAiUsageService.UsageStatistics stats = usageService.getStatistics(
                groupName, provider, model, startTime, endTime, currentScopeUserId());
        return ReturnResult.ok(new AiUsageStatisticsVO(
                stats.totalCalls,
                stats.totalPromptTokens,
                stats.totalCompletionTokens,
                stats.totalTokens,
                stats.totalCost,
                stats.totalCachedTokens,
                stats.avgLatency,
                stats.avgFirstTokenLatency,
                stats.standardCost
        ));
    }

    /**
     * 查询 AI 用量趋势。
     *
     * @param provider 服务商名称，为空时不限定
     * @param model 模型名称，为空时不限定
     * @param startTime 趋势开始时间，为空时不限定
     * @param endTime 趋势结束时间，为空时不限定
     * @return 按天或按小时聚合的用量趋势
     */
    @Operation(summary = "用量趋势", description = "按天聚合返回 Token 使用趋势；普通用户只看本人明细的按天聚合")
    @GetMapping("/trend")
    public ReturnResult<List<AiUsageTrendPointVO>> trend(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        String userId = currentScopeUserId();
        // 单日范围（今天 / 起止同一天）按小时分桶，其余按天
        boolean singleDay = startTime != null && endTime != null
                && startTime.toLocalDate().equals(endTime.toLocalDate());
        if (singleDay) {
            return ReturnResult.ok(usageService.getHourlyTrend(startTime, endTime, userId));
        }
        List<com.chua.starter.ai.support.entity.SysAiUsageDaily> dailies =
                usageService.getDailyTrend(provider, model, startTime, endTime, userId);
        List<AiUsageTrendPointVO> result = new ArrayList<>(dailies.size());
        for (var d : dailies) {
            result.add(new AiUsageTrendPointVO(
                    d.getStatDate() != null ? d.getStatDate().toString() : null,
                    d.getTotalCalls() != null ? d.getTotalCalls() : 0L,
                    d.getTotalPromptTokens() != null ? d.getTotalPromptTokens() : 0L,
                    d.getTotalCompletionTokens() != null ? d.getTotalCompletionTokens() : 0L,
                    d.getTotalTokens() != null ? d.getTotalTokens() : 0L,
                    d.getTotalCachedTokens() != null ? d.getTotalCachedTokens() : 0L
            ));
        }
        return ReturnResult.ok(result);
    }

    /**
     * 查询已注册的用量数据源解析器。
     *
     * @return 用量数据源解析器列表
     */
    @Operation(summary = "用量数据源列表", description = "返回已注册的 UsageParser SPI 实现列表（静态目录，登录即可读）")
    @GetMapping("/parsers")
    public ReturnResult<List<AiUsageParserVO>> parsers() {
        Map<String, UsageParser> allParsers = new LinkedHashMap<>();

        // 1. 优先从 Spring 容器收集系统默认的用量解析器
        try {
            Map<String, UsageParser> springBeans = applicationContext.getBeansOfType(UsageParser.class);
            for (UsageParser parser : springBeans.values()) {
                allParsers.putIfAbsent(parser.name(), parser);
            }
        } catch (Throwable e) {
            // 必须捕获 Throwable 而非 Exception：解析器是 SPI 插件，任一实现类初始化失败抛的是
            // ExceptionInInitializerError（Error 分支），catch (Exception) 抓不住，
            // 会让整个目录接口 500。实测 GooseUsageParser 因 APPDATA 环境变量缺失即触发此路径
            // （2026-09-29，GET /v2/usage/parsers 返回 S9999C0000）。
            // 插件装载是「尽力而为」，一个坏实现应被降级跳过，而不是拖垮整个接口。
            log.warn("[Usage] 收集 Spring UsageParser 失败: {}", e.toString());
        }

        // 2. 再收集 SPI 注册的外部工具解析器（opencode/codex 等）
        try {
            Map<String, UsageParser> spiParsers = ServiceProvider.of(UsageParser.class).list();
            if (spiParsers != null) {
                for (Map.Entry<String, UsageParser> entry : spiParsers.entrySet()) {
                    allParsers.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        } catch (Throwable e) {
            // 同上：SPI 插件类初始化失败抛的是 Error，catch (Exception) 抓不住。
            log.warn("[Usage] SPI 收集 UsageParser 失败: {}", e.toString());
        }

        List<AiUsageParserVO> result = new ArrayList<>();
        for (Map.Entry<String, UsageParser> e : allParsers.entrySet()) {
            // 跳过名称或实现为空的扫描条目，避免空指针异常
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            // 单条读取名称也要兜底：这里的异常一旦逃出去同样是整个接口 500
            try {
                result.add(new AiUsageParserVO(e.getKey(), e.getValue().getClass().getSimpleName(), true));
            } catch (Throwable ex) {
                log.warn("[Usage] 读取解析器条目失败，已跳过: {} ({})", e.getKey(), ex.toString());
            }
        }
        return ReturnResult.ok(result);
    }

    /**
     * 全量同步任务 Future（用于取消前一次任务）
     */
    private volatile java.util.concurrent.Future<?> aggregateAllFuture;

    /**
     * 定时聚合任务 Future
     */
    private volatile java.util.concurrent.Future<?> aggregateFuture;

    /**
     * 异步执行器
     */
    private final java.util.concurrent.ExecutorService taskExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "usage-task-executor");
        t.setDaemon(true);
        return t;
    });

    /**
     * 异步触发用量同步与聚合。
     *
     * @return 触发结果
     */
    @Permission("admin")
    @Operation(summary = "手动触发同步聚合", description = "立即执行外部数据同步与当天日汇总表聚合（异步执行）")
    @PostMapping("/trigger")
    public ReturnResult<String> trigger() {
        UsageAggregationTask task = applicationContext.getBean(UsageAggregationTask.class);
        new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("[UsageTrigger] 同步聚合失败: {}", e.getMessage(), e);
            }
        }, "usage-trigger").start();
        return ReturnResult.ok("已触发同步聚合（后台执行）");
    }

    /**
     * 异步执行当天用量聚合。
     *
     * @return 触发结果
     */
    @Permission("admin")
    @Operation(summary = "仅执行当天聚合", description = "跳过外部同步，仅对当天已有明细数据执行日汇总（异步执行）")
    @PostMapping("/aggregate")
    public ReturnResult<String> aggregate() {
        // 取消前一次任务
        if (aggregateFuture != null && !aggregateFuture.isDone()) {
            aggregateFuture.cancel(true);
            log.info("[UsageAggregate] 已取消前一次聚合任务");
        }
        UsageAggregationTask task = applicationContext.getBean(UsageAggregationTask.class);
        aggregateFuture = taskExecutor.submit(() -> {
            try {
                task.aggregate();
            } catch (Exception e) {
                log.error("[UsageAggregate] 聚合失败: {}", e.getMessage(), e);
            }
        });
        return ReturnResult.ok("已触发当天聚合（后台执行）");
    }

    /**
     * 异步执行历史用量全量聚合。
     *
     * @return 触发结果
     */
    @Permission("admin")
    @Operation(summary = "手动全量同步", description = "对全部历史用量明细执行全量聚合回填日汇总表（异步执行，重复点击会取消前一次任务）")
    @PostMapping("/aggregate-all")
    public ReturnResult<String> aggregateAll() {
        // 取消前一次任务
        if (aggregateAllFuture != null && !aggregateAllFuture.isDone()) {
            aggregateAllFuture.cancel(true);
            log.info("[UsageAggregateAll] 已取消前一次全量同步任务");
        }
        UsageAggregationTask task = applicationContext.getBean(UsageAggregationTask.class);
        aggregateAllFuture = taskExecutor.submit(() -> {
            try {
                task.aggregateAll();
            } catch (Exception e) {
                log.error("[UsageAggregateAll] 全量聚合失败: {}", e.getMessage(), e);
            } finally {
                broadcastTaskStatus();
            }
        });
        broadcastTaskStatus();
        return ReturnResult.ok("已触发全量同步（后台执行，重复点击会取消前一次任务）");
    }

    /**
     * 取消正在执行的全量聚合任务。
     *
     * @return 取消结果
     */
    @Permission("admin")
    @Operation(summary = "取消全量同步任务", description = "取消正在执行的全量同步任务")
    @PostMapping("/aggregate-all/cancel")
    public ReturnResult<String> cancelAggregateAll() {
        if (aggregateAllFuture != null && !aggregateAllFuture.isDone()) {
            aggregateAllFuture.cancel(true);
            broadcastTaskStatus();
            return ReturnResult.ok("已取消全量同步任务");
        }
        return ReturnResult.ok("没有正在执行的全量同步任务");
    }

    /**
     * 查询当前聚合任务状态。
     *
     * @return 聚合任务状态
     */
    @Permission("admin")
    @Operation(summary = "查询同步任务状态", description = "查询当前是否有正在执行的同步任务")
    @GetMapping("/task-status")
    public ReturnResult<AiUsageTaskStatusVO> taskStatus() {
        return ReturnResult.ok(currentTaskStatus());
    }

    /**
     * 当前聚合任务运行状态。
     *
     * @return 任务状态
     */
    private AiUsageTaskStatusVO currentTaskStatus() {
        return new AiUsageTaskStatusVO(
                aggregateFuture != null && !aggregateFuture.isDone(),
                aggregateAllFuture != null && !aggregateAllFuture.isDone()
        );
    }

    /**
     * SSE 推送任务状态
     */
    private final List<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> sseEmitors = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 创建任务状态事件流并推送当前状态。
     *
     * @return 服务器推送事件流
     */
    @Permission("admin")
    @Operation(summary = "SSE订阅任务状态", description = "通过SSE实时推送任务状态变化")
    @GetMapping(value = "/task-status/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter taskStatusStream() {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(0L);
        sseEmitors.add(emitter);
        emitter.onCompletion(() -> sseEmitors.remove(emitter));
        emitter.onTimeout(() -> sseEmitors.remove(emitter));
        emitter.onError(e -> sseEmitors.remove(emitter));
        // 立即推送一次当前状态
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("status").data(currentTaskStatus()));
        } catch (Exception e) {
            // 忽略
        }
        return emitter;
    }

    /**
     * 广播任务状态变化
     */
    private void broadcastTaskStatus() {
        if (sseEmitors.isEmpty()) {
            return;
        }
        AiUsageTaskStatusVO status = currentTaskStatus();
        for (org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter : sseEmitors) {
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("status").data(status));
            } catch (Exception e) {
                sseEmitors.remove(emitter);
            }
        }
    }

    /**
     * 查询最近一次同步聚合日志。
     *
     * @return 同步日志列表
     */
    @Permission("admin")
    @Operation(summary = "获取同步日志", description = "查看最近一次定时同步聚合的日志")
    @GetMapping("/logs")
    public ReturnResult<List<UsageAggregationTask.SyncLogEntry>> logs() {
        return ReturnResult.ok(UsageAggregationTask.getSyncLogs());
    }

    /**
     * 分页查询持久化的用量同步日志。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数，最大为 200
     * @param level 日志级别，为空时不限定
     * @return 用量同步日志分页结果
     */
    @Permission("admin")
    @Operation(summary = "用量同步日志（持久化分页）", description = "从 sys_ai_usage_sync_log 表分页查询历史同步日志")
    @GetMapping("/sync-logs")
    public ReturnResult<AiUsagePageVO<SysAiUsageSyncLog>> syncLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String level) {
        Page<SysAiUsageSyncLog> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiUsageSyncLog> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(level != null && !level.isBlank(), SysAiUsageSyncLog::getLevel, level)
                .orderByDesc(SysAiUsageSyncLog::getCreateTime);
        Page<SysAiUsageSyncLog> result = syncLogMapper.selectPage(p, wrapper);
        return ReturnResult.ok(AiUsagePageVO.of(result));
    }

    @Permission("admin")
    @Operation(summary = "用量/定价对账", description = "比对实际用量与各数据源模型指标，输出缺价模型、零费用来源、闲置定价与重复定价")
    @GetMapping("/recon")
    public ReturnResult<Object> recon(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return ReturnResult.ok(reconService.reconcile(startTime, endTime));
    }

    /**
     * 导出用量分布为 CSV 文件。
     *
     * @param model 模型名称，为空时不限定
     * @param startTime 导出开始时间，为空时不限定
     * @param endTime 导出结束时间，为空时不限定
     * @param response HTTP 响应对象
     * @throws IOException 写入响应失败时抛出
     */
    @Operation(summary = "导出用量分布",
            description = "按 分组+服务商+模型 聚合导出 CSV，与用量分布表同口径；普通用户仅导出本人数据")
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public void export(
            @RequestParam(required = false) String model,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            HttpServletResponse response) throws IOException {
        List<AiUsageDTO> rows = usageSummaryService.query(model, startTime, endTime, currentScopeUserId());
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"usage.csv\"");
        Writer writer = response.getWriter();
        StringBuilder line = new StringBuilder();
        // Excel 会按本地编码猜 CSV，前置 BOM 才能稳定识别为 UTF-8
        writer.write('\uFEFF');
        writer.write("group,provider,model,calls,promptTokens,completionTokens,totalTokens,cost\n");
        for (AiUsageDTO row : rows) {
            line.setLength(0);
            line.append(csvCell(row.getGroup())).append(',')
                    .append(csvCell(row.getProvider())).append(',')
                    .append(csvCell(row.getModel())).append(',')
                    .append(row.getRequests()).append(',')
                    .append(row.getPromptTokens()).append(',')
                    .append(row.getCompletionTokens()).append(',')
                    .append(row.getTotalTokens()).append(',')
                    .append(row.getCost()).append('\n');
            writer.write(line.toString());
        }
        writer.flush();
    }

    /**
     * CSV 单元格转义：含分隔符、引号或换行时整体加引号，内部引号翻倍。
     *
     * @param value 原值（可为空）
     * @return 转义后的单元格文本
     */
    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * 当前请求的用量统计口径：仅跨账号放行时返回 {@code null}（看全量），否则返回本人 ID。
     *
     * @return 归属用户标识；跨账号放行时为 null
     */
    private String currentScopeUserId() {
        return AiUserContext.canAccessOthersData() ? null : AiUserContext.requireUserId();
    }
}
