package com.chua.report.server.starter.controller;

import com.chua.common.support.http.HttpClient;
import com.chua.common.support.http.HttpMethod;
import com.chua.common.support.http.HttpResponse;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.server.starter.pojo.ActuatorRequest;
import com.chua.starter.common.support.filter.ActuatorAuthenticationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * actuator接口
 * @author CH
 * @since 2024/9/13
 */
@RestController
@RequestMapping("v1/actuator")
@Tag(name = "actuator接口")
@RequiredArgsConstructor
public class ActuatorController {


    /**
     * 获取actuator信息
     * @param request 请求
     * @return ReturnResult
     */
    @Operation(summary = "获取actuator信息")
    @PostMapping("call")
    public ReturnResult<String> post(@RequestBody ActuatorRequest request) {
        HttpResponse response = HttpClient.newHttpMethod(HttpMethod.valueOf(request.getMethod()))
                .body(request.getBody())
                .header("Authorization", ActuatorAuthenticationFilter.getKey(
                        StringUtils.defaultString(request.getUsername(), "actuator"),
                        StringUtils.defaultString(request.getUsername(), "actuator")))
                .url(request.getUrl())
                .newInvoker().execute();
        return ReturnResult.of(response);
    }
}
