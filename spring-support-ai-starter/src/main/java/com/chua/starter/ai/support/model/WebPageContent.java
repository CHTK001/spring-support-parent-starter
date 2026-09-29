package com.chua.starter.ai.support.model;

import java.util.List;

/**
 * 抓取到的单个网页正文。
 *
 * @param url     实际抓取的 URL（跟随重定向后）
 * @param title   网页标题，取不到时为空
 * @param text    正文纯文本（已去脚本/样式/导航并还原 HTML 实体）
 * @param links   页面上发现的同域链接，用于继续展开
 * @author CH
 * @since 4.0.0.42
 */
public record WebPageContent(
        String url,
        String title,
        String text,
        List<String> links
) {

    /**
     * 紧凑构造器：链接列表做防御性拷贝并保留 null 语义。
     *
     * <p>链接可能为 null（未开启同域展开时），因此不加 requireNonNull；
     * 用允许 null 元素的不可变包装而不是 {@code List.copyOf}，因为解析器
     * 理论上可能吐出空串而不是 null 之外的脏值。</p>
     */
    public WebPageContent {
        links = links == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(links));
    }
}
