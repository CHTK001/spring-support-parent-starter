package com.chua.starter.collect.vo;

/**
 * 预览抓取出参。
 *
 * <p>给「元素拾取器」用：设计期把上游要抓的页面取回来渲染在 iframe 里，用户像 F12 那样
 * 点选元素来生成 CSS / XPath 表达式。抓取结果在流程运行期只活在变量里，设计期拿不到，
 * 所以需要这个出参。</p>
 *
 * <p>{@code html} 是**原样**返回的响应正文，不做任何清洗 —— 剥 {@code <script>} 由前端在
 * 渲染进 iframe 之前做（后端不承担这份职责，免得同一个页面在「落库」与「预览」两条路上
 * 拿到不同的内容）。</p>
 *
 * @param status      HTTP 状态码
 * @param contentType 响应 Content-Type，可能为空
 * @param length      正文字符数，便于前端提示「抓回来多少内容」而不必自己算
 * @param html        响应正文原文
 * @author CH
 * @since 4.0.0.42
 */
public record CollectPreviewVO(int status, String contentType, int length, String html) {
}
