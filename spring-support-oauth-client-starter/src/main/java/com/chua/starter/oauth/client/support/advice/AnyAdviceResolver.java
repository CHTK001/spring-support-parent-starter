package com.chua.starter.oauth.client.support.advice;

import org.springframework.http.MediaType;

/**
 * json
 *
 * @since 2022/7/29 10:24
 * @author CH
 */
public class AnyAdviceResolver extends JsonAdviceResolver {

    @Override
    /**
     * 类型
     * @return 返回值
    */
    public String type() {
        return MediaType.APPLICATION_JSON_VALUE;
    }

}
