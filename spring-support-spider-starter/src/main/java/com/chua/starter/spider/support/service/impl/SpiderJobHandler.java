package com.chua.starter.spider.support.service.impl;

import com.chua.starter.spider.support.domain.SpiderExecutionRecord;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.engine.SpiderExecutionEngine;
import com.chua.starter.spider.support.repository.SpiderExecutionRecordRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * job-starter 调度触发回调处理器。
 *
 * <p>当 job-starter 触发调度时，此处理器接收回调，
 * 创建新的 {@link SpiderExecutionRecord} 并异步启动执行链路。</p>
 *
 * <p>Bean 名称 {@code spiderJobHandler} 与 {@link SpiderScheduledJobServiceImpl#registerJob} 中注册的 beanName 一致。</p>
 *
 * @author CH
 */
@Slf4j
@Component("spiderJobHandler")
@RequiredArgsConstructor
public class SpiderJobHandler {

    private final SpiderTaskRepository taskRepository;
    private final SpiderExecutionRecordRepository executionRecordRepository;
    private final SpiderExecutionEngine executionEngine;

    /**
     * 接收 job-starter 调度触发，创建执行记录并异步执行。
     *
     * @param taskId 爬虫任务 ID（由 job-starter 传入的 param）
     */
    @Async
    public void onScheduledTrigger(Long taskId) {
        SpiderTaskDefinition task = taskRepository.getById(taskId);
        if (task == null) {
            log.warn("[Spider] 调度触发：任务 [{}] 不存在，跳过执行", taskId);
            return;
        }

        // 创建执行记录
        SpiderExecutionRecord record = SpiderExecutionRecord.builder()
                .taskId(taskId)
                .executionType(SpiderExecutionType.SCHEDULED)
                .startTime(LocalDateTime.now())
                .triggerSource("SCHEDULED")
                .successCount(0L)
                .failureCount(0L)
                .build();
        executionRecordRepository.save(record);

        log.info("[Spider] 调度触发执行, taskId={}, recordId={}", taskId, record.getId());

        // 异步启动执行链路
        try {
            executionEngine.execute(taskId);
        } catch (Exception e) {
            log.error("[Spider] 调度执行失败, taskId={}", taskId, e);
        }
    }
}
