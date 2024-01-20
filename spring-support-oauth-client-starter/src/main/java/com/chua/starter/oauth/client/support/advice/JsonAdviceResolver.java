package com.chua.starter.oauth.client.support.advice;

import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ResultCode;
import com.chua.common.support.lang.code.ReturnResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * json
 *
 * @author CH
 * @since 2022/7/29 10:24
 */
@Slf4j
public class JsonAdviceResolver implements AdviceResolver {

    @Override
    public String type() {
        return MediaType.APPLICATION_JSON_VALUE;
    }

    @Override
    public Object resolve(HttpServletResponse response, Integer status, String message) {
        ReturnResult rs = ReturnResult.newBuilder().code(ResultCode.transferForHttpCode(status).getCode()).msg(message).build();
        if (null == response) {
            return rs;
        }
        response.setStatus(status);
        try {
            response.setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(Json.toJSONBytes(rs));
        } catch (IOException e) {
            log.error("", e);
        }

        return null;
    }
}
