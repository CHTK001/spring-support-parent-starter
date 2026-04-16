package com.chua.starter.spider.support.domain.enums;

/**
 * 爬虫任务状态枚举
 */
public enum SpiderTaskStatus {
    /** 草稿，尚未持久化保存 */
    DRAFT,
    /** 已保存，等待触发执行 */
    READY,
    /** 正在执行中 */
    RUNNING,
    /** 已暂停 */
    PAUSED,
    /** 执行失败 */
    FAILED,
    /** 执行完成 */
    FINISHED,
    /** 等待人工介入输入 */
    WAITING_INPUT
}
