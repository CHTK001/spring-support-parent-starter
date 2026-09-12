package com.chua.starter.spider.dto;

import lombok.Data;

/**
* 爬虫执行聚合统计。
*
* <p>按爬虫编码汇总：总执行次数、成功次数、失败次数、运行中次数及最近执行号。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
public class DefinitionStatsVO {
    /**
    * 爬虫编码
     */
    private String definitionCode;
    /**
    * 总执行次数
     */
    private long totalRuns;
    /**
    * 成功次数
     */
    private long successCount;
    /**
    * 失败次数
     */
    private long failCount;
    /**
    * 运行中次数
     */
    private long runningCount;
    /**
    * 最近一次执行号
     */
    private String latestExecutionNo;
}