package com.chua.starter.spider.support.engine;

import com.alibaba.fastjson2.JSON;
import com.chua.spider.support.SpiderToolkit;
import com.chua.spider.support.model.SpiderBrainDefinition;
import com.chua.spider.support.model.SpiderExecutionDefinition;
import com.chua.spider.support.model.SpiderExecutionMode;
import com.chua.spider.support.model.SpiderPreviewResult;
import com.chua.spider.support.model.SpiderTaskResult;
import com.chua.starter.spider.support.domain.SpiderAiProfile;
import com.chua.starter.spider.support.domain.SpiderExecutionPolicy;
import com.chua.starter.spider.support.domain.SpiderExecutionRecord;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderRuntimeSnapshot;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.repository.SpiderExecutionRecordRepository;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderRuntimeSnapshotRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 爬虫执行引擎。
 *
 * <p>底层执行委托给 {@link SpiderToolkit}，本类负责：
 * <ul>
 *   <li>平台层任务定义 → 底层任务定义的转换</li>
 *   <li>运行时快照回写</li>
 *   <li>执行记录更新</li>
 *   <li>REPEAT_N 次数控制</li>
 *   <li>节点级日志记录</li>
 * </ul>
 * </p>
 *
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpiderExecutionEngine {

    private final SpiderTaskRepository taskRepository;
    private final SpiderFlowRepository flowRepository;
    private final SpiderRuntimeSnapshotRepository snapshotRepository;
    private final SpiderExecutionRecordRepository recordRepository;
    private final SpiderToolkit spiderToolkit;
    private final HumanInputSuspendRegistry humanInputSuspendRegistry;

    @Autowired(required = false)
    private SpiderRuntimePushService runtimePushService;

    @Autowired(required = false)
    private com.chua.starter.spider.support.repository.SpiderNodeExecutionLogRepository nodeLogRepository;

    /**
     * 执行指定任务。
     *
     * @param taskId 任务 ID
     */
    public void execute(Long taskId) {
        SpiderTaskDefinition task = taskRepository.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务 [" + taskId + "] 不存在");
        }

        SpiderFlowDefinition flow = flowRepository.findByTaskId(taskId).orElse(null);
        SpiderExecutionPolicy policy = parsePolicy(task);

        // 更新状态为 RUNNING
        updateTaskStatus(task, SpiderTaskStatus.RUNNING);
        updateSnapshot(taskId, SpiderTaskStatus.RUNNING, null, null, null);

        // 9.5 REPEAT_N 控制
        int repeatTimes = (policy != null && policy.getRepeatTimes() != null) ? policy.getRepeatTimes() : 1;
        boolean isRepeatN = task.getExecutionType() == SpiderExecutionType.REPEAT_N;

        long totalSuccess = 0;
        long totalFailure = 0;
        String lastError = null;

        for (int round = 0; round < repeatTimes; round++) {
            log.info("[Spider] 开始执行, taskId={}, round={}/{}", taskId, round + 1, repeatTimes);

            // 9.2 节点级日志
            List<String> nodeLog = buildNodeLog(flow, round);

            SpiderTaskResult result = null;
            try {
                // 9.1 转换并执行
                com.chua.spider.support.model.SpiderTaskDefinition utilsDef = toUtilsDefinition(task, policy, flow);

                // 推送各节点 RUNNING 状态
                if (flow != null && flow.getNodes() != null) {
                    for (SpiderFlowNode node : flow.getNodes()) {
                        pushNodeStatus(taskId, node.getNodeId(), "RUNNING", "节点开始执行");
                    }
                }

                result = spiderToolkit.run(utilsDef);

                // 推送各节点 SUCCESS 状态
                if (flow != null && flow.getNodes() != null) {
                    for (SpiderFlowNode node : flow.getNodes()) {
                        pushNodeStatus(taskId, node.getNodeId(), "SUCCESS", "节点执行完成");
                    }
                }

                totalSuccess += result.getSuccessRequests();
                totalFailure += result.getFailureRequests();

                // 9.4 回写快照
                updateSnapshot(taskId, SpiderTaskStatus.RUNNING, LocalDateTime.now(),
                        totalSuccess, totalFailure);

                log.info("[Spider] 第 {}/{} 轮执行完成, taskId={}, success={}, failure={}",
                        round + 1, repeatTimes, taskId, result.getSuccessRequests(), result.getFailureRequests());

            } catch (Exception e) {
                lastError = e.getMessage();
                totalFailure++;
                log.error("[Spider] 第 {}/{} 轮执行失败, taskId={}", round + 1, repeatTimes, taskId, e);

                // 推送节点 FAILED 状态
                if (flow != null && flow.getNodes() != null) {
                    for (SpiderFlowNode node : flow.getNodes()) {
                        pushNodeStatus(taskId, node.getNodeId(), "FAILED", e.getMessage());
                    }
                }

                // 9.3 重试耗尽后标记 FAILED
                updateTaskStatus(task, SpiderTaskStatus.FAILED);
                updateSnapshot(taskId, SpiderTaskStatus.FAILED, LocalDateTime.now(),
                        totalSuccess, totalFailure, lastError);
                return;
            }

            // REPEAT_N 间隔等待
            if (isRepeatN && round < repeatTimes - 1 && policy.getRepeatInterval() != null) {
                sleepQuietly(policy.getRepeatInterval() * 1000);
            }
        }

        // 9.5 REPEAT_N 达到次数后变更为 FINISHED
        SpiderTaskStatus finalStatus = isRepeatN ? SpiderTaskStatus.FINISHED : SpiderTaskStatus.FINISHED;
        updateTaskStatus(task, finalStatus);
        updateSnapshot(taskId, finalStatus, LocalDateTime.now(), totalSuccess, totalFailure);
        log.info("[Spider] 任务执行完成, taskId={}, status={}", taskId, finalStatus);
    }

    // ── 转换：平台层 → 底层 ───────────────────────────────────────────────────

    com.chua.spider.support.model.SpiderTaskDefinition toUtilsDefinition(
            SpiderTaskDefinition task,
            SpiderExecutionPolicy policy,
            SpiderFlowDefinition flow) {

        com.chua.spider.support.model.SpiderTaskDefinition def =
                new com.chua.spider.support.model.SpiderTaskDefinition();

        def.setTaskName(task.getTaskName());
        def.setUrl(task.getEntryUrl());

        if (policy != null) {
            // 9.3 重试策略
            if (policy.getRetryPolicy() != null) {
                def.setRetryTimes(policy.getRetryPolicy().getMaxRetries());
                def.setRetrySleepTime((int) policy.getRetryPolicy().getRetryIntervalMs());
            }
            if (policy.getThreadCount() != null) {
                def.setThreadNum(policy.getThreadCount());
            }

            // 执行模式映射
            SpiderExecutionDefinition execDef = new SpiderExecutionDefinition();
            execDef.setEnabled(true);
            execDef.setMode(mapExecutionMode(task.getExecutionType()));
            if (policy.getCron() != null) {
                execDef.setCron(policy.getCron());
            }
            if (policy.getRepeatInterval() != null) {
                execDef.setPeriodMillis(policy.getRepeatInterval() * 1000);
            }
            def.setExecution(execDef);
        }

        // AI profile: task-level aiProfile → SpiderBrainDefinition
        if (task.getAiProfile() != null && !task.getAiProfile().isBlank()) {
            try {
                SpiderAiProfile aiProfile = JSON.parseObject(task.getAiProfile(), SpiderAiProfile.class);
                if (Boolean.TRUE.equals(aiProfile.getEnabled())) {
                    def.setBrain(mapAiProfileToBrainDefinition(aiProfile));
                }
            } catch (Exception e) {
                log.warn("[Spider] 解析任务级 aiProfile 失败, taskId={}", task.getId(), e);
            }
        }

        return def;
    }

    /**
     * 将平台层 {@link SpiderAiProfile} 转换为底层 {@link SpiderBrainDefinition}。
     *
     * @param profile 平台层 AI 配置
     * @return 底层大脑定义
     */
    SpiderBrainDefinition mapAiProfileToBrainDefinition(SpiderAiProfile profile) {
        SpiderBrainDefinition brain = new SpiderBrainDefinition();
        brain.setEnabled(Boolean.TRUE.equals(profile.getEnabled()));
        brain.setProvider(profile.getProvider());
        brain.setModel(profile.getModel());
        return brain;
    }

    private SpiderExecutionMode mapExecutionMode(SpiderExecutionType type) {
        if (type == null) return SpiderExecutionMode.ONCE;
        return switch (type) {
            case ONCE -> SpiderExecutionMode.ONCE;
            case REPEAT_N -> SpiderExecutionMode.FIXED_RATE;
            case SCHEDULED -> SpiderExecutionMode.CRON;
        };
    }

    // ── 快照 & 状态更新 ───────────────────────────────────────────────────────

    private void updateTaskStatus(SpiderTaskDefinition task, SpiderTaskStatus status) {
        task.setStatus(status);
        taskRepository.updateById(task);
    }

    private void updateSnapshot(Long taskId, SpiderTaskStatus status,
                                LocalDateTime lastExecuteTime,
                                Long successCount, Long failureCount) {
        updateSnapshot(taskId, status, lastExecuteTime, successCount, failureCount, null);
    }

    private void updateSnapshot(Long taskId, SpiderTaskStatus status,
                                LocalDateTime lastExecuteTime,
                                Long successCount, Long failureCount,
                                String lastError) {
        SpiderRuntimeSnapshot snapshot = snapshotRepository.findByTaskId(taskId)
                .orElse(SpiderRuntimeSnapshot.builder().taskId(taskId).build());

        snapshot.setStatus(status);
        if (lastExecuteTime != null) snapshot.setLastExecuteTime(lastExecuteTime);
        if (successCount != null) snapshot.setSuccessCount(successCount);
        if (failureCount != null) snapshot.setFailureCount(failureCount);
        if (lastError != null) snapshot.setLastErrorSummary(
                lastError.length() > 512 ? lastError.substring(0, 512) : lastError);

        snapshotRepository.saveOrUpdate(snapshot);
    }

    // ── 节点日志 ──────────────────────────────────────────────────────────────

    /** 9.2 构建节点级日志摘要 */
    private List<String> buildNodeLog(SpiderFlowDefinition flow, int round) {
        List<String> logs = new ArrayList<>();
        if (flow == null || flow.getNodes() == null) return logs;

        for (SpiderFlowNode node : flow.getNodes()) {
            logs.add(String.format("[round=%d][%s][%s] 执行时间=%s",
                    round + 1,
                    node.getNodeType(),
                    node.getNodeId(),
                    LocalDateTime.now()));
        }
        return logs;
    }

    private SpiderExecutionPolicy parsePolicy(SpiderTaskDefinition task) {
        if (task.getExecutionPolicy() == null || task.getExecutionPolicy().isBlank()) return null;
        try {
            return JSON.parseObject(task.getExecutionPolicy(), SpiderExecutionPolicy.class);
        } catch (Exception e) {
            log.warn("[Spider] 解析执行策略失败, taskId={}", task.getId(), e);
            return null;
        }
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 推送节点状态变更（若 SpiderRuntimePushService 已注入则推送，否则仅记录日志） */
    private void pushNodeStatus(Long taskId, String nodeId, String status, String message) {
        if (runtimePushService != null) {
            runtimePushService.pushNodeStatus(taskId, nodeId, status, message);
        } else {
            log.debug("[Spider][SSE] taskId={} nodeId={} status={} message={}", taskId, nodeId, status, message);
        }
    }

    // ── DETAIL_FETCH 节点执行 ─────────────────────────────────────────────────

    /**
     * 执行 DETAIL_FETCH 节点：从 RawRecord 中取出详情 URL，抓取详情页，
     * 将详情页 HTML 合并回记录，返回富化后的 RawRecord。
     *
     * <p>节点 config 支持以下键：
     * <ul>
     *   <li>{@code urlField}（或 {@code detailUrlField}）：记录中存放详情 URL 的字段名，默认 {@code "detailUrl"}</li>
     *   <li>{@code resultField}：详情 HTML 写入记录的字段名，默认 {@code "detailHtml"}</li>
     *   <li>{@code downloaderType}：下载器类型，默认 {@code "jsoup"}</li>
     * </ul>
     * </p>
     *
     * @param node   DETAIL_FETCH 节点定义
     * @param record 输入 RawRecord（Map&lt;String, Object&gt;）
     * @return 富化后的 RawRecord；若记录中无详情 URL，原样返回
     */
    public Map<String, Object> executeDetailFetchNode(SpiderFlowNode node, Map<String, Object> record) {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();

        // 解析 urlField：优先 "urlField"，其次 "detailUrlField"，默认 "detailUrl"
        String urlField = resolveConfigString(config, "urlField",
                resolveConfigString(config, "detailUrlField", "detailUrl"));
        String resultField = resolveConfigString(config, "resultField", "detailHtml");
        String downloaderType = resolveConfigString(config, "downloaderType", "jsoup");

        Object rawUrl = record.get(urlField);
        if (rawUrl == null || rawUrl.toString().isBlank()) {
            log.warn("[Spider][DETAIL_FETCH] nodeId={} 记录中未找到详情 URL（urlField={}），跳过",
                    node.getNodeId(), urlField);
            return record;
        }

        String detailUrl = rawUrl.toString().trim();
        log.debug("[Spider][DETAIL_FETCH] nodeId={} 抓取详情页 url={}", node.getNodeId(), detailUrl);

        try {
            com.chua.spider.support.model.SpiderTaskDefinition detailDef =
                    new com.chua.spider.support.model.SpiderTaskDefinition();
            detailDef.setUrl(detailUrl);
            detailDef.setDownloader(downloaderType);

            SpiderPreviewResult preview = spiderToolkit.preview(detailUrl, detailDef);

            Map<String, Object> enriched = new HashMap<>(record);
            enriched.put(resultField, preview.getHtml());
            if (preview.getTitle() != null) {
                enriched.putIfAbsent("detailTitle", preview.getTitle());
            }
            log.debug("[Spider][DETAIL_FETCH] nodeId={} 详情页抓取成功，statusCode={}",
                    node.getNodeId(), preview.getStatusCode());
            return enriched;

        } catch (Exception e) {
            log.warn("[Spider][DETAIL_FETCH] nodeId={} 抓取详情页失败 url={}: {}",
                    node.getNodeId(), detailUrl, e.getMessage());
            return record;
        }
    }

    // ── HUMAN_INPUT 节点执行 ──────────────────────────────────────────────────

    /**
     * 执行 HUMAN_INPUT 节点：将任务状态切换为 WAITING_INPUT，挂起执行线程，
     * 等待用户通过 {@code POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/input} 提交输入。
     *
     * <p>节点 config 支持以下键：
     * <ul>
     *   <li>{@code timeoutSeconds}：等待超时秒数，默认 300</li>
     *   <li>{@code onTimeout}：超时策略，{@code "skip"}（透传输入数据）或 {@code "fail"}（抛出异常），默认 {@code "fail"}</li>
     *   <li>{@code promptText}：展示给用户的提示文字</li>
     * </ul>
     * </p>
     *
     * @param taskId    任务 ID
     * @param node      HUMAN_INPUT 节点定义
     * @param inputData 上游传入的数据（透传或作为默认值）
     * @return 用户提交的输入值；若 onTimeout=skip 则返回 inputData 的字符串形式
     * @throws Exception 超时且 onTimeout=fail，或等待被中断时抛出
     */
    public Object executeHumanInputNode(Long taskId, SpiderFlowNode node, Object inputData) throws Exception {
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();

        int timeoutSeconds = resolveConfigInt(config, "timeoutSeconds", 300);
        String onTimeout = resolveConfigString(config, "onTimeout", "fail");
        String promptText = resolveConfigString(config, "promptText", "请输入所需数据");

        // 1. 切换任务状态为 WAITING_INPUT
        SpiderTaskDefinition task = taskRepository.getById(taskId);
        if (task != null) {
            updateTaskStatus(task, SpiderTaskStatus.WAITING_INPUT);
        }
        updateSnapshot(taskId, SpiderTaskStatus.WAITING_INPUT, null, null, null);

        // 2. 推送 SSE 事件
        pushNodeStatus(taskId, node.getNodeId(), "WAITING_INPUT", promptText);

        // 3. 注册 CompletableFuture 并阻塞等待
        CompletableFuture<String> future = humanInputSuspendRegistry.register(taskId, node.getNodeId());
        try {
            String userInput = future.get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("[Spider][HUMAN_INPUT] taskId={} nodeId={} 收到用户输入", taskId, node.getNodeId());
            return userInput;
        } catch (TimeoutException e) {
            humanInputSuspendRegistry.complete(taskId, node.getNodeId(), null); // 清理注册
            log.warn("[Spider][HUMAN_INPUT] taskId={} nodeId={} 等待超时（{}s），onTimeout={}",
                    taskId, node.getNodeId(), timeoutSeconds, onTimeout);
            if ("skip".equalsIgnoreCase(onTimeout)) {
                return inputData;
            }
            throw new RuntimeException(
                    String.format("HUMAN_INPUT 节点 [%s] 等待超时（%ds）", node.getNodeId(), timeoutSeconds), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    String.format("HUMAN_INPUT 节点 [%s] 等待被中断", node.getNodeId()), e);
        }
    }

    /**
     * 恢复被 HUMAN_INPUT 节点挂起的任务执行。
     *
     * <p>前端通过 {@code POST /v1/spider/tasks/{taskId}/nodes/{nodeId}/input} 调用此方法。</p>
     *
     * @param taskId    任务 ID
     * @param nodeId    节点 ID
     * @param userInput 用户提交的输入值
     * @return {@code true} 表示成功恢复；{@code false} 表示未找到对应挂起节点
     */
    public boolean resumeHumanInput(Long taskId, String nodeId, String userInput) {
        boolean completed = humanInputSuspendRegistry.complete(taskId, nodeId, userInput);
        if (completed) {
            SpiderTaskDefinition task = taskRepository.getById(taskId);
            if (task != null) {
                updateTaskStatus(task, SpiderTaskStatus.RUNNING);
            }
            updateSnapshot(taskId, SpiderTaskStatus.RUNNING, null, null, null);
            log.info("[Spider][HUMAN_INPUT] taskId={} nodeId={} 已恢复执行", taskId, nodeId);
        } else {
            log.warn("[Spider][HUMAN_INPUT] taskId={} nodeId={} 未找到挂起节点，忽略输入", taskId, nodeId);
        }
        return completed;
    }

    private int resolveConfigInt(Map<String, Object> config, String key, int defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String resolveConfigString(Map<String, Object> config, String key, String defaultValue) {
        Object val = config.get(key);
        return (val != null && !val.toString().isBlank()) ? val.toString() : defaultValue;
    }

    // ── 节点级 AI 大脑运行时 ──────────────────────────────────────────────────

    /**
     * 为指定节点解析并创建 {@link com.chua.spider.support.brain.SpiderBrainRuntime}。
     *
     * <p>合并策略（节点级优先）：
     * <ol>
     *   <li>读取节点的 {@code config["aiProfile"]}（或 {@code aiAssistantConfig}）</li>
     *   <li>与任务级 {@code taskAiProfile} 合并：节点级字段非空时覆盖任务级字段</li>
     *   <li>若合并后 {@code enabled=true}，创建并返回 {@link com.chua.spider.support.brain.SpiderBrainRuntime}；否则返回 {@code null}</li>
     * </ol>
     * </p>
     *
     * <p>此方法应在每个节点执行前调用，以便节点可以使用独立的 AI 配置。</p>
     *
     * @param node           当前节点
     * @param taskAiProfile  任务级 AI 配置（可为 {@code null}）
     * @return 已启用的 {@link com.chua.spider.support.brain.SpiderBrainRuntime}；若 AI 未启用则返回 {@code null}
     */
    public com.chua.spider.support.brain.SpiderBrainRuntime resolveNodeBrainRuntime(
            SpiderFlowNode node,
            SpiderAiProfile taskAiProfile) {

        // 1. 读取节点级 aiProfile
        SpiderAiProfile nodeProfile = node.resolveAiProfile();

        // 2. 合并：节点级优先于任务级
        SpiderAiProfile merged = mergeAiProfiles(taskAiProfile, nodeProfile);

        // 3. 未启用则返回 null
        if (merged == null || !Boolean.TRUE.equals(merged.getEnabled())) {
            return null;
        }

        // 4. 构建底层 SpiderTaskDefinition 并创建 SpiderBrainRuntime
        com.chua.spider.support.model.SpiderTaskDefinition brainDef =
                new com.chua.spider.support.model.SpiderTaskDefinition();
        brainDef.setBrain(mapAiProfileToBrainDefinition(merged));

        try {
            com.chua.spider.support.brain.SpiderBrainRuntime runtime =
                    com.chua.spider.support.brain.SpiderBrainRuntime.create(brainDef);
            log.debug("[Spider][AI] nodeId={} 创建 SpiderBrainRuntime, provider={}, model={}",
                    node.getNodeId(), merged.getProvider(), merged.getModel());
            return runtime;
        } catch (Exception e) {
            log.warn("[Spider][AI] nodeId={} 创建 SpiderBrainRuntime 失败: {}", node.getNodeId(), e.getMessage());
            return null;
        }
    }

    /**
     * 合并两个 {@link SpiderAiProfile}，节点级（override）字段非空时覆盖基础（base）字段。
     *
     * @param base     基础配置（任务级），可为 {@code null}
     * @param override 覆盖配置（节点级），可为 {@code null}
     * @return 合并后的配置；若两者均为 {@code null} 则返回 {@code null}
     */
    SpiderAiProfile mergeAiProfiles(SpiderAiProfile base, SpiderAiProfile override) {
        if (base == null && override == null) return null;
        if (base == null) return override;
        if (override == null) return base;

        return SpiderAiProfile.builder()
                .provider(override.getProvider() != null ? override.getProvider() : base.getProvider())
                .model(override.getModel() != null ? override.getModel() : base.getModel())
                .enabled(override.getEnabled() != null ? override.getEnabled() : base.getEnabled())
                .temperature(override.getTemperature() != null ? override.getTemperature() : base.getTemperature())
                .contextWindow(override.getContextWindow() != null ? override.getContextWindow() : base.getContextWindow())
                .build();
    }

    // ── 节点执行日志 ──────────────────────────────────────────────────────────

    /**
     * 节点执行开始时写入日志（状态 RUNNING）。
     */
    public com.chua.starter.spider.support.domain.SpiderNodeExecutionLog beginNodeLog(
            Long recordId, Long taskId, SpiderFlowNode node) {
        if (nodeLogRepository == null || recordId == null) return null;
        var nodeLog = com.chua.starter.spider.support.domain.SpiderNodeExecutionLog.builder()
                .recordId(recordId)
                .taskId(taskId)
                .nodeId(node.getNodeId())
                .nodeType(node.getNodeType() != null ? node.getNodeType().name() : null)
                .status("RUNNING")
                .startTime(LocalDateTime.now())
                .successCount(0L)
                .failureCount(0L)
                .retryCount(0)
                .aiUsed(false)
                .build();
        nodeLogRepository.saveLog(nodeLog);
        return nodeLog;
    }

    /**
     * 节点执行结束时更新日志。
     */
    public void endNodeLog(
            com.chua.starter.spider.support.domain.SpiderNodeExecutionLog nodeLog,
            boolean success, String errorMsg) {
        if (nodeLogRepository == null || nodeLog == null) return;
        LocalDateTime now = LocalDateTime.now();
        nodeLog.setEndTime(now);
        if (nodeLog.getStartTime() != null) {
            nodeLog.setDurationMs(java.time.Duration.between(nodeLog.getStartTime(), now).toMillis());
        }
        nodeLog.setStatus(success ? "SUCCESS" : "FAILED");
        if (!success && errorMsg != null) {
            nodeLog.setErrorMsg(errorMsg.length() > 1000 ? errorMsg.substring(0, 1000) : errorMsg);
        }
        nodeLogRepository.saveLog(nodeLog);
    }
}
