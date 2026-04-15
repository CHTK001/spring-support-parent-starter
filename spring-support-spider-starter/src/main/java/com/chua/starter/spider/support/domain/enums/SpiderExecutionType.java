package com.chua.starter.spider.support.domain.enums;

/**
 * 爬虫任务执行类型枚举
 */
public enum SpiderExecutionType {
    /** 仅执行一次 */
    ONCE,
    /** 按固定次数重复执行 */
    REPEAT_N,
    /** 定时调度执行 */
    SCHEDULED
}
