package com.chua.starter.oauth.client.support.advice;

/**
 * 响应解释器
 *
 * @author CH
 */
public class Advice {

    public static Advice create() {
        return new Advice();
    }

    public AdviceResolver resolve(String accept, String contentType) {
        if (contentType != null && contentType.contains("html")) {
            return new HtmlAdviceResolver();
        }
        return new JsonAdviceResolver();
    }
}
