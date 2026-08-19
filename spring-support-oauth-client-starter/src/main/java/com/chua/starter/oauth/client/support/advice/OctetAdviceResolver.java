package com.chua.starter.oauth.client.support.advice;

import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * json
 *
 * @since 2022/7/29 10:24
 * @author CH
 */
public class OctetAdviceResolver implements AdviceResolver {

    @Override
    public String type() {
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    @Override
    public Object resolve(HttpServletResponse response, Integer status, String message) throws IOException {
        if (null == response) {
            return new byte[0];
        }
        response.setHeader("Content-Type", type());
        response.setStatus(status);
        response.getOutputStream().write(new byte[0]);
        return null;
    }

}
