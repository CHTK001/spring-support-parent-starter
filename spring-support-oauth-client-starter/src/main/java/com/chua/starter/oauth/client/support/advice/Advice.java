package com.chua.starter.oauth.client.support.advice;

/**
* 响应解释器
*
* @author CH
* @since 4.0.0
 */
public class Advice {

    /**
    * 创建
    *
    * @return 创建的结果
    */
    public static Advice create() {
        /**
        * Advice。
        */
        return new Advice();
    }

    /**
    * 解析
    *
    * @param accept accept
    * @param contentType 内容类型
    * @return resolve的结果
    */
    public AdviceResolver resolve(String accept, String contentType) {
        if (contentType != null && contentType.contains("html")) {
            /**
            * htmladvice解析器。
            * @return htmladvice解析器的结果
            */
            return new HtmlAdviceResolver();
        }
        /**
        * jsonadvice解析器。
        * @return jsonadvice解析器的结果
        */
        return new JsonAdviceResolver();
    }
}
