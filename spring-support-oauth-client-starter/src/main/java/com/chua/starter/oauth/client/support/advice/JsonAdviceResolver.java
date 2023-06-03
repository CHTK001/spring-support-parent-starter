package com.chua.starter.oauth.client.support.advice;

import com.chua.starter.common.support.result.ReturnResult;
import org.springframework.http.MediaType;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * json
 *
 * @author CH
 * @since 2022/7/29 10:24
 */
public class JsonAdviceResolver implements AdviceResolver {

    @Override
    public String type() {
        return MediaType.APPLICATION_JSON_VALUE;
    }

    @Override
    public Object resolve(HttpServletResponse response, Integer status, String message) {
        ReturnResult rs = ReturnResult.newBuilder().code(status).msg(message).build();
        if (null == response) {
            return rs;
        }
        response.setStatus(status);
        try {
            response.setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(rs.toString().getBytes(UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
