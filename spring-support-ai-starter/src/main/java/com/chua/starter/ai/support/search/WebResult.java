package com.chua.starter.ai.support.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联网搜索单条结果。
 *
 * @author CH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebResult {

    /**
     * 标题
     */
    private String title;

    /**
     * 链接
     */
    private String url;

    /**
     * 摘要
     */
    private String snippet;
}
