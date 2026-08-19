package com.chua.starter.oauth.client.support.advice;

/**
 * 响应解释器
 *
 * @author CH
 */
public class Advice {

    /** 创建 */
    public static Advice create() {
        return new Advice();
    }

    /** 解析 */
    public AdviceResolver resolve(String accept, String contentType) {
        if (contentType != null && contentType.contains("html")) {
            return new HtmlAdviceResolver();
        }
        return new JsonAdviceResolver();
    }
}
