package com.chua.starter.oauth.client.support.advice;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.code.ResultCode;
import com.chua.common.support.lang.code.ReturnResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * json
 *
 * @since 2022/7/29 10:24
 * @author CH
 */
@Slf4j
public class JsonAdviceResolver implements AdviceResolver {

    @Override
    /**
     * 类型
    */
    public String type() {
        return MediaType.APPLICATION_JSON_VALUE;
    }

    @Override
    /**
     * 解析
    */
    public Object resolve(HttpServletResponse response, Integer status, String message) {
        ReturnResult<Object> rs = ReturnResult.error(ResultCode.transferForHttpCode(status).getCode(), message);
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