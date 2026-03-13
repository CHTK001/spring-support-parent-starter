package com.chua.starter.sync.data.support.sync.concurrency;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 优先级任务队列
 * 根据任务优先级进行调度
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Component
public class PriorityTaskQueue {
    
    private final PriorityBlockingQueue<PriorityTask> queue;
    
    public PriorityTaskQueue() {
        this.queue = new PriorityBlockingQueue<>(100, 
                Comparator.comparingInt(PriorityTask::getPriority).reversed());
        log.info("优先级任务队列初始化完成");
    }
    
    /**
     * 提交任务
     *
     * @param task 任务
     * @return 是否提交成功
     */
    public boolean submit(PriorityTask task) {
        boolean result = queue.offer(task);
        if (result) {
            log.debug("任务已提交到队列: taskId={}, priority={}", task.getTaskId(), task.getPriority());
        } else {
            log.warn("任务提交失败: taskId={}", task.getTaskId());
        }
        return result;
    }
    
    /**
     * 获取下一个任务（阻塞）
     *
     * @return 任务
     * @throws InterruptedException 中断异常
     */
    public PriorityTask take() throws InterruptedException {
        return queue.take();
    }
    
    /**
     * 获取下一个任务（超时）
     *
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 任务，超时返回null
     * @throws InterruptedException 中断异常
     */
    public PriorityTask poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }
    
    /**
     * 获取队列大小
     *
     * @return 队列大小
     */
    public int size() {
        return queue.size();
    }
    
    /**
     * 清空队列
     */
    public void clear() {
        queue.clear();
        log.info("优先级任务队列已清空");
    }
    
    /**
     * 优先级任务
     */
    @Data
    public static class PriorityTask {
        /**
         * 任务ID
         */
        private Long taskId;
        
        /**
         * 任务名称
         */
        private String taskName;
        
        /**
         * 优先级（数值越大优先级越高）
         */
        private int priority;
        
        /**
         * 任务执行器
         */
        private Runnable runnable;
        
        /**
         * 提交时间
         */
        private long submitTime;
        
        public PriorityTask(Long taskId, String taskName, int priority, Runnable runnable) {
            this.taskId = taskId;
            this.taskName = taskName;
            this.priority = priority;
            this.runnable = runnable;
            this.submitTime = System.currentTimeMillis();
        }
        
        /**
         * 执行任务
         */
        public void execute() {
            if (runnable != null) {
                runnable.run();
            }
        }
        
        /**
         * 获取等待时间（毫秒）
         *
         * @return 等待时间
         */
        public long getWaitTime() {
            return System.currentTimeMillis() - submitTime;
        }
    }
}
