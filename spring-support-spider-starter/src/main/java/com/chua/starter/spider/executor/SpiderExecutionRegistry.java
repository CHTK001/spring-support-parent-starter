package com.chua.starter.spider.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 爬虫执行注册表。
 *
 * <p>维护正在运行中的爬虫实例，按执行批次号 {@code executionNo} 索引，
 * 供 {@link SpiderExecutionService} 启动/停止/查询时使用。</p>
 *
 * <p>使用 {@link ConcurrentHashMap} 保证线程安全。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class SpiderExecutionRegistry {

    /**
     * 执行批次号 -> 上下文映射。
     */
    private final Map<String, SpiderExecutionContext> contexts = new ConcurrentHashMap<>();

    /**
     * 注册一个执行上下文。
     *
     * @param context 上下文
     */
    public void register(SpiderExecutionContext context) {
        if (context == null || context.getExecutionNo() == null) {
            return;
        }
        contexts.put(context.getExecutionNo(), context);
        log.info("[Spider] 注册执行上下文: executionNo={}, spiderCode={}",
                context.getExecutionNo(), context.getSpiderCode());
    }

    /**
     * 注销一个执行上下文（执行结束或停止后调用）。
     *
     * @param executionNo 批次号
     */
    public void unregister(String executionNo) {
        if (executionNo == null) {
            return;
        }
        SpiderExecutionContext removed = contexts.remove(executionNo);
        if (removed != null) {
            log.info("[Spider] 注销执行上下文: executionNo={}", executionNo);
        }
    }

    /**
     * 获取指定批次的执行上下文。
     *
     * @param executionNo 批次号
     * @return 上下文，未找到返回 null
     */
    public SpiderExecutionContext get(String executionNo) {
        return contexts.get(executionNo);
    }

    /**
     * 获取全部正在执行的上下文（快照）。
     *
     * @return 上下文集合
     */
    public Collection<SpiderExecutionContext> all() {
        return contexts.values();
    }

    /**
     * 当前正在执行的爬虫数量。
     *
     * @return 数量
     */
    public int runningCount() {
        return contexts.size();
    }
}
