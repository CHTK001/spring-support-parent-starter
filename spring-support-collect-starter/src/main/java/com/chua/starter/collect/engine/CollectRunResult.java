package com.chua.starter.collect.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次流程执行的结果。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class CollectRunResult {

    /**
     * 任务ID。
     */
    private Long taskId;

    /**
     * 任务号。
     */
    private String taskNo;

    /**
     * 结束状态 RUNNING/SUCCESS/FAILURE/STOPPED。
     */
    private String status;

    /**
     * 耗时毫秒。
     */
    private long costMs;

    /**
     * 节点执行次数。
     */
    private int nodeCount;

    /**
     * 抓取次数。
     */
    private int requestCount;

    /**
     * 产出记录数。
     */
    private int recordCount;

    /**
     * 失败原因。
     */
    private String errorMessage;

    /**
     * 结果预览，仅调试执行填充。
     */
    private List<CollectRecordEntry> preview = new ArrayList<>();

    /**
     * 执行结束时的全局变量快照，子流程节点据此把变量写回父作用域。
     */
    private Map<String, Object> variables = new LinkedHashMap<>();
}
