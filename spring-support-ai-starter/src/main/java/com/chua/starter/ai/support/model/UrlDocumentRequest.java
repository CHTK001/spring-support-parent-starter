package com.chua.starter.ai.support.model;

import lombok.Data;

/**
 * URL 文档抓取请求
 *
 * @author CH
 */
@Data
public class UrlDocumentRequest {
    /** 要抓取的网页 URL */
    private String url;
}
