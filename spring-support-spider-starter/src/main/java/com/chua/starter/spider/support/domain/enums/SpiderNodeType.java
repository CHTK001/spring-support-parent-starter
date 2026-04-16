package com.chua.starter.spider.support.domain.enums;

/**
 * 爬虫编排节点类型枚举
 */
public enum SpiderNodeType {
    /** 起始节点，不可删除 */
    START,
    /** 下载器节点，负责 HTTP 请求/浏览器驱动 */
    DOWNLOADER,
    /** 链接提取器节点，从页面中提取待爬 URL */
    URL_EXTRACTOR,
    /** 数据采集器节点，负责 XPath/CSS 选择器解析 */
    DATA_EXTRACTOR,
    /** 详情下钻节点，对列表中每条记录发起详情请求 */
    DETAIL_FETCH,
    /** 处理器节点，负责数据清洗和转换 */
    PROCESSOR,
    /** 过滤器节点，负责脏数据过滤和去重 */
    FILTER,
    /** 人工介入节点，挂起执行等待用户输入 */
    HUMAN_INPUT,
    /** 管道节点，负责数据输出 */
    PIPELINE,
    /** 结束节点，不可删除 */
    END,
    /** 条件分支节点，根据条件走 true/false 双端口 */
    CONDITION,
    /** 错误处理节点，表达失败链路 */
    ERROR_HANDLER,
    /** 延迟节点，在执行链路中插入等待时间 */
    DELAY,
    /** 合并节点，将多条上游数据流合并为一条 */
    MERGE,
    /** 转换节点，对数据进行字段映射和格式转换 */
    TRANSFORMER,
    /**
     * @deprecated 已由 {@link #DATA_EXTRACTOR} 替代，保留以兼容旧数据
     */
    @Deprecated
    PARSER
}
