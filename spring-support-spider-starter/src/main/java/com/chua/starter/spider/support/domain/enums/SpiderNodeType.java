package com.chua.starter.spider.support.domain.enums;

/**
 * 爬虫编排节点类型枚举
 */
public enum SpiderNodeType {
    /** 起始节点，不可删除 */
    START,
    /** 下载器节点，负责 HTTP 请求/浏览器驱动 */
    DOWNLOADER,
    /** 解析器节点，负责 XPath/CSS 选择器解析 */
    PARSER,
    /** 过滤器节点，负责脏数据过滤和去重 */
    FILTER,
    /** 管道节点，负责数据输出 */
    PIPELINE,
    /** 错误处理节点，表达失败链路 */
    ERROR_HANDLER,
    /** 结束节点，不可删除 */
    END
}
