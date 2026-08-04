package com.chua.starter.spider.executor;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.spider.entity.SpiderExecutionRecord;
import com.chua.starter.spider.mapper.SpiderExecutionRecordMapper;

/**
 * 异步流程执行器（替代 SpiderFlowExecutionService.run() 的同步阻塞为后台线程）。
 *
 * <p>启动流程后立即返回 executionNo，前端可轮询 {@link #status(String)} 获取进度。
 * 进度更新：每完成 5 个节点批量写一次 execution_record。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Service
public class SpiderFlowAsyncExecutor {

    /**
     * 节点日志批量写回阈值
     */
    private static final int LOG_BATCH_SIZE = 5;

    private final SpiderExecutionRecordMapper executionMapper;

    /**
     * 异步执行线程池（仅用于运行流程）
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(
            4, r -> {
                Thread t = new Thread(r, "spider-flow-async");
                t.setDaemon(true);
                return t;
            });

    /**
     * 运行中流程上下文（key = executionNo）
     */
    private final ConcurrentHashMap<String, AsyncContext> contexts = new ConcurrentHashMap<>();

    public SpiderFlowAsyncExecutor(SpiderExecutionRecordMapper executionMapper) {
        this.executionMapper = executionMapper;
    }

    /**
     * 启动流程（异步）并返回 executionNo。
     *
     * @param runSync 待包装的同步执行方法引用（lambda）
     * @param flowCode 流程编码（用于写日志）
     * @return executionNo
     */
    public String start(Runnable runSync, String flowCode) {
        String executionNo = UUID.randomUUID().toString().replace("-", "");
        AsyncContext ctx = new AsyncContext(executionNo, flowCode);
        ctx.setStartedAt(System.currentTimeMillis());
        contexts.put(executionNo, ctx);

        // 写执行记录
        SpiderExecutionRecord record = new SpiderExecutionRecord();
        record.setSpiderExecutionNo(executionNo);
        record.setSpiderDefinitionCode(flowCode);
        record.setSpiderTriggerType("ASYNC_FLOW");
        record.setSpiderExecutionStatus("RUNNING");
        record.setSpiderStartedAt(LocalDateTime.now());
        record.setCreateTime(LocalDateTime.now());
        executionMapper.insert(record);

        // 后台线程执行（捕获异常并标记 FAILED）
        executor.submit(() -> {
            try {
                runSync.run();
                ctx.setStatus("SUCCESS");
            } catch (Throwable t) {
                ctx.setStatus("FAILED");
                ctx.setErrorMessage(t.getClass().getSimpleName() + ": " + t.getMessage());
            } finally {
                ctx.setFinishedAt(System.currentTimeMillis());
                updateRecordTerminal(ctx);
            }
        });

        return executionNo;
    }

    /**
     * 查询流程执行状态（内存有则返回内存，否则查 DB）。
     *
     * @param executionNo 批次号
     * @return 异步上下文，未找到返回 null
     */
    public AsyncContext status(String executionNo) {
        if (executionNo == null) {
            return null;
        }
        AsyncContext ctx = contexts.get(executionNo);
        if (ctx != null) {
            return ctx;
        }
        // 从 DB 恢复
        LambdaQueryWrapper<SpiderExecutionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderExecutionRecord::getSpiderExecutionNo, executionNo);
        SpiderExecutionRecord r = executionMapper.selectOne(wrapper);
        if (r == null) {
            return null;
        }
        AsyncContext recovered = new AsyncContext(executionNo, r.getSpiderDefinitionCode(), r.getSpiderExecutionStatus());
        recovered.setStartedAt(r.getSpiderStartedAt() == null ? 0L
                : r.getSpiderStartedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        return recovered;
    }

    /**
     * 列出所有正在执行的流程。
     *
     * @return 异步上下文列表
     */
    public java.util.List<AsyncContext> listRunning() {
        return java.util.List.copyOf(contexts.values());
    }

    /**
     * 写执行记录的终态。
     */
    private void updateRecordTerminal(AsyncContext ctx) {
        try {
            SpiderExecutionRecord update = new SpiderExecutionRecord();
            update.setSpiderExecutionStatus(ctx.getStatus());
            update.setSpiderFinishedAt(LocalDateTime.now());
            update.setSpiderErrorMessage(ctx.getErrorMessage() == null ? null
                    : (ctx.getErrorMessage().length() > 65000 ? ctx.getErrorMessage().substring(0, 65000) : ctx.getErrorMessage()));
            LambdaQueryWrapper<SpiderExecutionRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SpiderExecutionRecord::getSpiderExecutionNo, ctx.getExecutionNo());
            executionMapper.update(update, wrapper);
        } catch (Exception e) {
            // 静默
        }
    }

    /**
     * 异步执行上下文。
     *
     * @param executionNo 批次号
     * @param flowCode     流程编码
     * @param status       当前状态
     * @param errorMessage 异常信息（成功时为 null）
     * @param startedAt    开始时间戳（毫秒）
     * @param finishedAt   结束时间戳（毫秒）
     * @param logCount     已记录的节点日志数
     * @author CH
     * @since 4.0.0.42
     */
    @Data
    public static class AsyncContext {
        /**
         * 批次号
         */
        private String executionNo;
        /**
         * 流程编码
         */
        private String flowCode;
        /**
         * 当前状态
         */
        private String status = "RUNNING";
        /**
         * 异常信息
         */
        private String errorMessage;
        /**
         * 开始时间戳
         */
        private long startedAt;
        /**
         * 结束时间戳
         */
        private long finishedAt;
        /**
         * 已记录的节点日志数
         */
        private int logCount;

        /**
         * 默认构造。
         */
        public AsyncContext() {
        }

        /**
         * 简化的 2 参构造（用于启动时）。
         */
        public AsyncContext(String executionNo, String flowCode) {
            this.executionNo = executionNo;
            this.flowCode = flowCode;
            this.startedAt = System.currentTimeMillis();
        }

        /**
         * 简化的 2 参构造（用于从 DB 恢复）。
         *
         * @param executionNo 批次号
         * @param status      状态（参数名保留以区分上面那个构造）
         */
        public AsyncContext(String executionNo, String flowCode, String status) {
            this.executionNo = executionNo;
            this.flowCode = flowCode;
            this.status = status;
        }
    }
}
