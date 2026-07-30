package com.chua.starter.oauth.client.support.advice;

import org.springframework.http.MediaType;

/**
 * json
 *
 * @author CH
 * @since 2022/7/29 10:24
 */
public class AnyAdviceResolver extends JsonAdviceResolver {

    @Override
    public String type() {
        return MediaType.APPLICATION_JSON_VALUE;
    }

}
