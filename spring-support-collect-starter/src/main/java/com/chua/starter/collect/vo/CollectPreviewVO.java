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
 * <p>{@code rendered} 说明这份 HTML 的来源：{@code false} 是 HTTP 直取的原始响应，
 * {@code true} 是真实浏览器执行完 JS 之后的 DOM。两者差别很大——SPA 的原始响应里
 * 通常一个业务元素都没有，点选器在那种页面上什么也选不到，所以前端必须知道自己在看哪一种，
 * 才能给出「这个页面需要开启 JS 渲染」这类正确提示。</p>
 *
 * @param status      HTTP 状态码
 * @param contentType 响应 Content-Type，可能为空
 * @param length      正文字符数，便于前端提示「抓回来多少内容」而不必自己算
 * @param html        响应正文原文（{@code rendered=true} 时为渲染后的 outerHTML）
 * @param rendered    是否经过真实浏览器渲染（执行了 JS）
 * @author CH
 * @since 4.0.0.42
 */
public record CollectPreviewVO(int status, String contentType, int length, String html, boolean rendered) {

    /**
     * 兼容旧调用：按「HTTP 直取」构造。
     *
     * @param status      HTTP 状态码
     * @param contentType 响应 Content-Type
     * @param length      正文字符数
     * @param html        正文原文
     */
    public CollectPreviewVO(int status, String contentType, int length, String html) {
        this(status, contentType, length, html, false);
    }
}
