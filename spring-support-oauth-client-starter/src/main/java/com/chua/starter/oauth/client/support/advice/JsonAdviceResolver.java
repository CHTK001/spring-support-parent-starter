package com.chua.starter.oauth.client.support.advice;

import com.alibaba.fastjson2.JSON;
import com.chua.common.support.lang.code.ResultCode;
import com.chua.common.support.lang.code.ReturnResult;
import org.springframework.http.MediaType;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
        ReturnResult rs = ReturnResult.newBuilder().code(ResultCode.transferForHttpCode(status).getCode()).msg(message).build();
        if (null == response) {
            return rs;
        }
        response.setStatus(status);
        try {
            response.setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(JSON.toJSONBytes(rs));
        } catch (IOException e) {
            log.error("", e);
        }

        return null;
    }
}
