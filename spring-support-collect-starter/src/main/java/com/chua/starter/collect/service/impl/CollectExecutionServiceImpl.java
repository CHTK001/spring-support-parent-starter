// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.collect.service.impl;

import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.dto.CollectRunDTO;
import com.chua.starter.collect.engine.CollectContext;
import com.chua.starter.collect.engine.CollectEngine;
import com.chua.starter.collect.engine.CollectExecutionAdmission;
import com.chua.starter.collect.engine.CollectRunResult;
import com.chua.starter.collect.engine.CollectSink;
import com.chua.starter.collect.entity.CollectFlow;
import com.chua.starter.collect.entity.CollectTask;
import com.chua.starter.collect.mapper.CollectFlowMapper;
import com.chua.starter.collect.mapper.CollectRecordMapper;
import com.chua.starter.collect.mapper.CollectTaskLogMapper;
import com.chua.starter.collect.mapper.CollectTaskMapper;
import com.chua.starter.collect.properties.CollectProperties;
import com.chua.starter.collect.service.CollectExecutionService;
import com.chua.starter.collect.service.CollectFieldService;
import com.chua.starter.collect.service.CollectFlowService;
import com.chua.starter.collect.service.CollectNoticeService;
import com.chua.starter.collect.service.CollectVariableService;
import com.chua.starter.collect.store.CollectSinkFactory;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.support.CollectTaskStatus;
import com.chua.starter.collect.support.CollectTriggerType;
import com.chua.starter.collect.vo.CollectTaskVO;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 采集执行服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@Order(0)
@RequiredArgsConstructor
public class CollectExecutionServiceImpl implements CollectExecutionService, ApplicationRunner {

    /**
     * 任务号时间部分格式。
     */
    private static final DateTimeFormatter TASK_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 未保存草稿的占位流程ID。
     */
    private static final Integer NO_FLOW = null;

    /**
     * 执行引擎。
     */
    private final CollectEngine engine;

    /**
     * 平台配置。
     */
    private final CollectProperties properties;

    /**
     * 流程服务，负责取 DSL 与校验。
     */
    private final CollectFlowService flowService;

    /**
     * 全局变量服务。
     */
    private final CollectVariableService variableService;

    /**
     * 任务表访问。
     */
    private final CollectTaskMapper taskMapper;

    /**
     * 流程定义访问，负责跨节点并发名额的原子占用与释放。
     */
    private final CollectFlowMapper flowMapper;

    /**
     * 节点日志表访问，交给落库通道写日志。
     */
    private final CollectTaskLogMapper taskLogMapper;

    /**
     * 结果表访问，交给落库通道写结果。
     */
    private final CollectRecordMapper recordMapper;

    /**
     * 字段定义服务，任务收尾时登记字段。
     */
    private final CollectFieldService fieldService;

    /**
     * 执行通知服务，任务落定终态后按配置投递。
     */
    private final CollectNoticeService noticeService;

    /**
     * 落库通道工厂：按配置创建每次任务的通道，宿主可注册自定义实现整体替换。
     */
    private final CollectSinkFactory sinkFactory;

    /** 本实例全部入口共用的任务准入控制。 */
    private final CollectExecutionAdmission admission;

    /**
     * 每次执行占一个虚拟线程，引擎内部再按流程线程数建池。
     */
    private final ExecutorService launcher =
// [P3C C1 豁免] 场景化创建线程，需文档化
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("collect-run-", 0).factory());

    /**
     * 执行器标识，进程内不变。
     */
    private final String executorId = resolveExecutorId();

    @Override
    public CollectTaskVO runFlow(CollectRunDTO dto) {
        if (dto == null || dto.collectFlowId() == null) {
            throw new IllegalArgumentException("必须指定要运行的流程");
        }
        CollectFlow flow = flowService.requireFlow(dto.collectFlowId());
        CollectDslDTO dsl = flowService.readDsl(flow);
        boolean persist = dto.persist() == null || dto.persist();
        String trigger = persist ? CollectTriggerType.MANUAL : CollectTriggerType.TEST;
        return start(flow, dsl, trigger, dto.variables(), persist);
    }

    @Override
    public CollectTaskVO runScheduled(Integer flowId, String triggerType) {
        CollectFlow flow = flowService.requireFlow(flowId);
        CollectDslDTO dsl = flowService.readDsl(flow);
        String trigger = CollectTriggerType.MANUAL.equals(triggerType)
                ? CollectTriggerType.MANUAL : CollectTriggerType.CRON;
        CollectTaskVO vo = start(flow, dsl, trigger, List.of(), true);
        // 执行次数已由 start() 统一记账，这里不再重复累加
        return vo;
    }

    @Override
    public CollectTaskVO runDraft(CollectDslDTO dsl) {
        if (dsl == null) {
            throw new IllegalArgumentException("草稿流程图为空");
        }
        flowService.normalizeDsl(dsl);
        CollectFlow virtual = new CollectFlow();
        virtual.setCollectFlowId(NO_FLOW);
        virtual.setCollectFlowName(dsl.name() == null || dsl.name().isBlank()
                ? "未命名草稿" : dsl.name());
        virtual.setCollectFlowThreadCount(properties.getDefaultThreadCount());
        virtual.setCollectFlowConcurrency(1);
        return start(virtual, dsl, CollectTriggerType.TEST, List.of(), false);
    }

    @Override
    public boolean stop(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        boolean hit = engine.cancel(taskId);
        if (!hit) {
            log.info("[collect] 停止请求未命中本机运行中的任务 task={}", taskId);
        }
        return hit;
    }

    @Override
    public int inflight(Integer flowId) {
        if (flowId == null) {
            return 0;
        }
        try {
            return Math.max(flowMapper.currentRunning(flowId), 0);
        } catch (Exception ex) {
            log.warn("[collect] 读取流程运行中数量失败 flow={} cause={}", flowId, ex.getMessage());
            return 0;
        }
    }

    @Override
    public int runningCount() {
        return engine.runningCount();
    }

    @Override
    public String executorId() {
        return executorId;
    }

    /**
     * 关闭 launcher，拒绝新任务后等待在途任务收尾。
     */
    @PreDestroy
    public void shutdown() {
        admission.stopAccepting();
        launcher.shutdown();
        try {
            if (!launcher.awaitTermination(5L, TimeUnit.SECONDS)) {
                launcher.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            launcher.shutdownNow();
        }
    }

    /**
     * 启动即对账，把上次非正常退出留下的账补平。
     *
     * <p>排在最前执行：并发计数漂移会挡住后续所有触发，必须在流程重新登记调度之前修好。</p>
     *
     * @param args 启动参数，不使用
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            int reaped = reconcileRunning();
            if (reaped > 0) {
                log.warn("[collect] 启动对账收口了 {} 个失联任务，请确认上次是否为非正常退出", reaped);
            }
        } catch (RuntimeException ex) {
            // 对账失败不能挡住启动，否则采集平台会整个起不来；下一轮启动还会再修一次
            log.warn("[collect] 启动对账失败，本次不修正并发计数: {}", ex.getMessage());
        }
    }

    @Override
    public int reconcileRunning() {
        LocalDateTime now = LocalDateTime.now();
        // 使用任务启动时固化的实际期限，不能用当前全局配置推断另一实例的 DSL 超时。
        // 历史任务没有期限快照，保留运行态供管理员排查，避免误伤长任务。
        int reaped = taskMapper.reapStaleRunning(now, CollectTaskStatus.FAILURE,
                "启动对账：任务已超过本次执行期限及一分钟收尾宽限，判定执行进程已失联", now);
        int flows = flowMapper.resetRunningFromTasks();
        if (reaped > 0 || flows > 0) {
            log.info("[collect] 启动对账完成：收口失联任务 {} 个，重算并发计数流程 {} 个", reaped, flows);
        }
        return reaped;
    }

    /**
     * 建任务行并异步执行。
     *
     * @param flow      流程
     * @param dsl       流程图
     * @param trigger   触发方式
     * @param overrides 运行变量覆盖
     * @param persist   结果是否落库
     * @return 任务出参
     */
    private CollectTaskVO start(CollectFlow flow, CollectDslDTO dsl, String trigger,
                               List<CollectParamDTO> overrides, boolean persist) {
        Integer flowId = flow.getCollectFlowId();
        Integer reservedFlowId = null;
        CollectExecutionAdmission.Permit permit = admission.acquire();
        CollectTask task = null;
        CollectSink sink = null;
        try {
            if (flowId != null) {
                reserve(flowId, concurrencyOf(flow));
                reservedFlowId = flowId;
            }
            long timeoutMs = engine.effectiveTimeoutMs(dsl);
            task = createTask(flow, trigger, overrides, timeoutMs);
            // 执行次数在这里记：手动、定时、外部触发都走同一个入口。
            // 此前只写在 runScheduled 里，手动运行永远不计数（实测成语流程跑了 24 次仍为 0）。
            // 记账失败只记日志 —— 累计次数是展示口径，不能因为它让一次运行起不来。
            if (flowId != null && flowId > 0) {
                try {
                    flowService.markStarted(flowId);
                } catch (Exception ex) {
                    log.warn("[collect] 记录执行次数失败 flow={} err={}", flowId, ex.getMessage());
                }
            }
            CollectDslDTO runtime = prepareDsl(flow, dsl, overrides, timeoutMs);
            CollectTask saved = task;
            sink = sinkFactory.create(flowId, saved.getCollectTaskId(), persist);
            CollectContext context = new CollectContext(flowId, saved.getCollectTaskId(),
                    saved.getCollectTaskNo(), properties, sink);
            sink.attach(context);
            Integer releaseFlowId = reservedFlowId;
            CollectSink executionSink = sink;
            launcher.execute(() -> execute(context, runtime, saved, executionSink, releaseFlowId, permit));
            return toVo(saved, true);
        } catch (RuntimeException | Error ex) {
            if (sink != null) {
                try {
                    sink.close();
                } catch (RuntimeException | Error closeError) {
                    ex.addSuppressed(closeError);
                }
            }
            release(reservedFlowId);
            permit.close();
            failTask(task, ex);
            throw ex;
        }
    }

    /**
     * 在虚拟线程里跑完一次执行并回写任务行。
     *
     * @param context  执行上下文
     * @param dsl      流程图
     * @param task     任务行
     * @param sink     落库通道
     * @param reservedFlowId 已占用名额的流程，草稿运行为 null
     * @param permit 本实例的准入名额
     */
    private void execute(CollectContext context, CollectDslDTO dsl, CollectTask task,
                         CollectSink sink, Integer reservedFlowId, CollectExecutionAdmission.Permit permit) {
        CollectRunResult result = new CollectRunResult();
        result.setStatus(CollectTaskStatus.FAILURE);
        result.setErrorMessage("任务执行未正常结束");
        boolean completed = false;
        try {
            result = engine.execute(context, dsl);
            completed = true;
        } catch (Exception ex) {
            log.error("[collect] 任务执行异常 task={}", task.getCollectTaskNo(), ex);
            result = new CollectRunResult();
            result.setStatus(CollectTaskStatus.FAILURE);
            result.setErrorMessage(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally {
            if (!completed) {
                result.setNodeCount(context.getNodeCount().get());
                result.setRequestCount(context.getRequestCount().get());
                result.setRecordCount(context.getRecordCount().get());
            }
            try {
                // 先结清 Sink 的增量计量，再写终值，否则最后一段增量会叠在绝对值上翻倍。
                try {
                    sink.close();
                } catch (Exception ex) {
                    log.error("[collect] 落库通道收尾失败 task={}", task.getCollectTaskNo(), ex);
                    result.setStatus(CollectTaskStatus.FAILURE);
                    String previous = result.getErrorMessage();
                    result.setErrorMessage((previous == null || previous.isBlank() ? "" : previous + "; ")
                            + "落库通道收尾失败: " + ex.getMessage());
                }
                // close 失败仍要尝试结束任务行，不能留下永久 RUNNING。
                finishTask(task, result);
            } catch (Exception ex) {
                log.error("[collect] 任务收尾失败 task={}", task.getCollectTaskNo(), ex);
            } finally {
                release(reservedFlowId);
                permit.close();
            }
        }
        // 通知在收尾之后无条件编排：投递本身是异步的，失败只写进通知的最近结果。
        try {
            noticeService.notifyTask(task, result);
        } catch (RuntimeException ex) {
            log.error("[collect] 任务通知提交失败 task={}", task.getCollectTaskNo(), ex);
        }
    }

    /**
     * 组装运行时 DSL：平台默认线程数、全局变量、流程变量、本次覆盖依次写入。
     *
     * @param flow      流程
     * @param dsl       原始流程图
     * @param overrides 本次覆盖
     * @param timeoutMs 本次启动固化的有效超时
     * @return 可执行 DSL
     */
    private CollectDslDTO prepareDsl(CollectFlow flow, CollectDslDTO dsl, List<CollectParamDTO> overrides, long timeoutMs) {
        Integer threadCount = flow.getCollectFlowThreadCount() == null || flow.getCollectFlowThreadCount() <= 0
                ? properties.getDefaultThreadCount() : flow.getCollectFlowThreadCount();
        List<CollectParamDTO> variables = new ArrayList<>();
        if (flow.getCollectFlowId() != null) {
            variables.addAll(variableService.runtimeVariables());
        }
        variables.addAll(dsl.variables());
        if (overrides != null) {
            variables.addAll(overrides);
        }
        return new CollectDslDTO(dsl.name(), threadCount, timeoutMs, dsl.headers(), dsl.cookies(),
                variables, dsl.nodes(), dsl.edges());
    }

    /**
     * 插入 RUNNING 状态的任务行。
     *
     * @param flow      流程
     * @param trigger   触发方式
     * @param overrides 本次覆盖
     * @param timeoutMs 本次启动固化的有效超时
     * @return 任务行
     */
    private CollectTask createTask(CollectFlow flow, String trigger, List<CollectParamDTO> overrides, long timeoutMs) {
        CollectTask task = new CollectTask();
        task.setCollectTaskNo(newTaskNo());
        task.setCollectTaskFlowId(flow.getCollectFlowId());
        task.setCollectTaskFlowName(flow.getCollectFlowName());
        task.setCollectTaskStatus(CollectTaskStatus.RUNNING);
        task.setCollectTaskTriggerType(trigger);
        task.setCollectTaskParams(cut(CollectJson.toJson(overrides == null ? List.of() : overrides), 2000));
        task.setCollectTaskBeginTime(LocalDateTime.now());
        LocalDateTime deadline = task.getCollectTaskBeginTime().plus(Duration.ofMillis(timeoutMs)).plusMinutes(1);
        if (deadline.getYear() > 9999) {
            throw new IllegalArgumentException("任务超时超出数据库日期范围，请缩短运行超时");
        }
        task.setCollectTaskDeadline(deadline);
        task.setCollectTaskCost(0L);
        task.setCollectTaskNodeCount(0);
        task.setCollectTaskRecordCount(0);
        task.setCollectTaskRequestCount(0);
        task.setCollectTaskExecutorId(executorId);
        taskMapper.insert(task);
        return task;
    }

    /**
     * 回写任务的结束状态与计量。
     *
     * @param task   任务行
     * @param result 执行结果
     */
    private void finishTask(CollectTask task, CollectRunResult result) {
        CollectTask update = new CollectTask();
        update.setCollectTaskId(task.getCollectTaskId());
        update.setCollectTaskStatus(result.getStatus());
        update.setCollectTaskEndTime(LocalDateTime.now());
        update.setCollectTaskCost(result.getCostMs());
        update.setCollectTaskNodeCount(result.getNodeCount());
        update.setCollectTaskRequestCount(result.getRequestCount());
        update.setCollectTaskRecordCount(result.getRecordCount());
        update.setCollectTaskError(cut(result.getErrorMessage(), 2000));
        taskMapper.updateById(update);
        // 累计采集条数跟任务同一时刻落账：列表页只读 collect_flow 这一列，
        // 不再对 collect_task 求和（任务表只会越来越长，临时聚合会越跑越慢）。
        // 增量写，失败只记日志 —— 累计数是个展示口径，不能因为它让任务收尾失败。
        try {
            flowService.addRecords(task.getCollectTaskFlowId(), result.getRecordCount());
        } catch (Exception ex) {
            log.warn("[collect] 累计采集条数写入失败 flow={} delta={} err={}",
                    task.getCollectTaskFlowId(), result.getRecordCount(), ex.getMessage());
        }
    }

    /**
     * 任务行创建失败或提交失败时补一条失败记录。
     *
     * @param task 已插入的任务行，可为 null
     * @param ex   异常
     */
    private void failTask(CollectTask task, Throwable ex) {
        if (task == null || task.getCollectTaskId() == null) {
            return;
        }
        CollectTask update = new CollectTask();
        update.setCollectTaskId(task.getCollectTaskId());
        update.setCollectTaskStatus(CollectTaskStatus.FAILURE);
        update.setCollectTaskEndTime(LocalDateTime.now());
        update.setCollectTaskError(cut(ex.getMessage(), 2000));
        try {
            taskMapper.updateById(update);
        } catch (Exception inner) {
            log.warn("[collect] 任务失败状态回写失败 task={} err={}", task.getCollectTaskId(), inner.getMessage());
        }
    }

    /**
     * 占用一个并发名额。
     *
     * @param flowId 流程ID
     * @param limit  并发上限
     */
    private void reserve(Integer flowId, int limit) {
        if (flowMapper.tryAcquireRunning(flowId, Math.max(limit, 1)) != 1) {
            throw new IllegalStateException("流程 " + flowId + " 并发任务数已达上限 " + limit + "，本次触发跳过");
        }
    }

    /**
     * 释放跨节点并发名额。
     *
     * @param flowId 流程ID
     */
    private void release(Integer flowId) {
        if (flowId == null) {
            return;
        }
        try {
            flowMapper.releaseRunning(flowId);
        } catch (Exception ex) {
            log.warn("[collect] 释放流程并发名额失败 flow={} cause={}", flowId, ex.getMessage());
        }
    }

    /**
     * 流程并发上限。
     *
     * @param flow 流程
     * @return 上限
     */
    private int concurrencyOf(CollectFlow flow) {
        Integer limit = flow.getCollectFlowConcurrency();
        return limit == null || limit <= 0 ? 1 : limit;
    }

    /**
     * 生成任务号。
     *
     * @return 任务号
     */
    private String newTaskNo() {
        return "CT" + LocalDateTime.now().format(TASK_NO_FORMAT)
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    /**
     * 实体转出参。
     *
     * @param task    任务
     * @param running 是否运行中
     * @return 出参
     */
    private CollectTaskVO toVo(CollectTask task, boolean running) {
        return new CollectTaskVO(
                task.getCollectTaskId(),
                task.getCollectTaskNo(),
                task.getCollectTaskFlowId(),
                task.getCollectTaskFlowName(),
                task.getCollectTaskStatus(),
                task.getCollectTaskTriggerType(),
                task.getCollectTaskParams(),
                task.getCollectTaskBeginTime(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                running,
                task.getCreateName());
    }

    /**
     * 文本截断到列宽。
     *
     * @param text 文本
     * @param max  列宽
     * @return 截断后的文本
     */
    private String cut(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }

    /**
     * 解析执行器标识。
     *
     * @return 主机名与进程号
     */
    private static String resolveExecutorId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            host = "local";
        }
        return host + ":" + ProcessHandle.current().pid();
    }
}
