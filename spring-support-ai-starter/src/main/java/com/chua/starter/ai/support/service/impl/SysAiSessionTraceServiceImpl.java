package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.agent.AgentTrace;
import com.chua.common.support.utils.IdUtils;
import com.chua.starter.ai.support.entity.SysAiSessionTrace;
import com.chua.starter.ai.support.mapper.SysAiSessionTraceMapper;
import com.chua.starter.ai.support.service.SysAiSessionTraceService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 会话执行轨迹 服务实现：内存队列 + 定时批量落库。
 *
 * <p>为什么不用 {@code @Async} 或每条直接写：一次工具密集的运行能在几秒内产生
 * 上百条轨迹，逐条同步写会把流式输出拖垮；裸 {@code @Async} 则会在流量突增时
 * 把线程池打满且无法背压。这里用有界队列兜底——队列满就丢轨迹并告警，
 * 保证「观测数据永远不拖垮业务流」。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiSessionTraceServiceImpl implements SysAiSessionTraceService {

    /**
     * 待落库轨迹队列容量：约可缓冲 100 次工具密集型运行。
     */
    private static final int QUEUE_CAPACITY = 20000;

    /**
     * 单次刷库的最大条数，避免一条超长 SQL 拖住数据库。
     */
    private static final int FLUSH_BATCH = 500;

    /**
     * 每个调度周期最多刷几批，防止积压时单次占用连接过久。
     */
    private static final int MAX_BATCHES_PER_TICK = 4;

    /**
     * 轨迹映射器
     */
    private final SysAiSessionTraceMapper traceMapper;

    /**
     * 待落库轨迹队列
     */
    private final ArrayBlockingQueue<SysAiSessionTrace> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    /**
     * 轨迹序号发号器。
     *
     * <p>读取时按 (event_time, seq) 排序；同一毫秒内可能有多条事件，
     * 仅靠时间戳无法稳定还原发生顺序，故补一个序号。
     * 这里用全局递增而非「每请求一个计数器」：后者需要在运行结束时清理计数表，
     * 而运行可能异常中断、清理不掉就等于泄漏；全局递增则无状态。</p>
     */
    private final AtomicInteger seq = new AtomicInteger();

    /**
     * 队列满时累计丢弃的条数，用于告警时说明影响面。
     */
    private final AtomicLong dropped = new AtomicLong();

    @Override
    public void append(String sessionId, String chatRequestId, AgentTrace trace) {
        if (trace == null || trace.getKind() == null) {
            return;
        }
        SysAiSessionTrace row = new SysAiSessionTrace();
        row.setUid(IdUtils.uuidv7());
        row.setSessionId(parseSessionId(sessionId));
        row.setChatRequestId(chatRequestId);
        row.setKind(trace.getKind());
        row.setAgentId(trace.getAgentId());
        row.setSpanId(trace.getSpanId());
        row.setSpanEnd(trace.isSpanEnd());
        row.setToolName(trace.getToolName());
        row.setText(trace.getText());
        row.setEventTime(trace.getTimestamp() > 0 ? trace.getTimestamp() : System.currentTimeMillis());
        row.setSpanEndTime(trace.getEndTimestamp() > 0 ? trace.getEndTimestamp() : 0L);
        row.setSeq(seq.getAndIncrement());
        if (!queue.offer(row)) {
            long total = dropped.incrementAndGet();
            // 每丢弃 100 条告警一次，避免刷屏
            if (total % 100 == 1) {
                log.warn("[SessionTrace] 轨迹队列已满，已丢弃 {} 条，观测数据可能不完整", total);
            }
        }
    }

    /**
     * 解析会话ID。
     *
     * <p>会话编排层拿到的是字符串形式的会话ID，而表里存的是数值主键；
     * 解析不出来就留空——轨迹本身仍有价值，只是挂不到会话上。</p>
     *
     * @param raw 原始字符串
     * @return 解析结果；为空或非法返回 null
     */
    private static Long parseSessionId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public int flush() {
        List<SysAiSessionTrace> batch = new ArrayList<>(FLUSH_BATCH);
        queue.drainTo(batch, FLUSH_BATCH);
        if (batch.isEmpty()) {
            return 0;
        }
        try {
            traceMapper.insertBatch(batch);
            return batch.size();
        } catch (Exception e) {
            // 轨迹落库失败不能影响会话本身
            log.warn("[SessionTrace] 轨迹刷库失败，本批丢弃 {} 条: {}", batch.size(), e.getMessage());
            log.debug("[SessionTrace] 轨迹刷库异常明细", e);
            return 0;
        }
    }

    @Override
    public List<SysAiSessionTrace> listBySession(Long sessionId, int limit) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SysAiSessionTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiSessionTrace::getSessionId, sessionId)
                .orderByAsc(SysAiSessionTrace::getEventTime)
                .orderByAsc(SysAiSessionTrace::getSeq);
        if (limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return traceMapper.selectList(wrapper);
    }

    /**
     * 定时把队列中的轨迹批量落库。
     *
     * <p>队列积压时连续刷若干批，把积压排空；每批仍有 {@link #FLUSH_BATCH} 上限。</p>
     */
    @Scheduled(initialDelay = 2000L, fixedDelay = 1000L)
    public void scheduledFlush() {
        for (int i = 0; i < MAX_BATCHES_PER_TICK && !queue.isEmpty(); i++) {
            if (flush() < FLUSH_BATCH) {
                return;
            }
        }
    }

    /**
     * 服务停止前把残留轨迹刷完，避免最后一批丢失。
     */
    @PreDestroy
    public void destroy() {
        int total = 0;
        int n;
        while ((n = flush()) > 0) {
            total += n;
        }
        if (total > 0) {
            log.info("[SessionTrace] 停止前补刷轨迹 {} 条", total);
        }
    }
}
