package com.chua.starter.spider.executor;

import com.chua.spider.support.Spider;
import com.chua.spider.support.model.SpiderResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 爬虫执行上下文。
 *
 * <p>封装一次爬虫执行的全部运行时信息，由 {@link SpiderExecutor} 内部维护，
 * 通过 {@link SpiderExecutionRegistry} 暴露给上层查询。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Builder(toBuilder = true)
public class SpiderExecutionContext {

    /**
     * 执行记录 ID（自增主键）。
     */
    private Long executionRecordId;

    /**
     * 爬虫业务编码。
     */
    private String spiderCode;

    /**
     * 本次执行的批次号（UUID）。
     */
    private String executionNo;

    /**
     * 执行的 Spider 实例（运行中可调用 stop() 终止）。
     */
    private Spider spider;

    /**
     * 已抓取 URL 数量。
     */
    private volatile int completedCount;

    /**
     * 抓取总 URL 数量。
     */
    private volatile int totalCount;

    /**
     * 已采集数据条数。
     */
    private volatile int collectedCount;

    /**
     * 已达到的最大深度。
     */
    private volatile int maxDepthReached;

    /**
     * 执行状态。
     */
    private volatile String status;

    /**
     * 已抓取的结果列表。
     */
    private final List<SpiderResult> results = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 触发方式：MANUAL / CRON。
     */
    private String triggerType;

    /**
     * 启动时间（毫秒）。
     */
    private long startedAt;
}
