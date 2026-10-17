package com.chua.starter.ai.support.search;

/**
 * 联网搜索单条结果。
 *
 * @param title   标题
 * @param url     链接
 * @param snippet 摘要
 * @author CH
 */
public record WebResult(String title, String url, String snippet) {
}
