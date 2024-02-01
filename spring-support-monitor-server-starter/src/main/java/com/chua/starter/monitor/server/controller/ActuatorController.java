package com.chua.starter.monitor.server.controller;

import com.chua.common.support.http.HttpClient;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.FileUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.chua.common.support.http.HttpConstant.ACCEPT;
import static com.chua.common.support.http.HttpConstant.APPLICATION_JSON_UTF_8;
import static com.chua.common.support.http.HttpHeaders.CONTENT_TYPE;
import static com.chua.common.support.http.HttpMethod.POST;
import static com.chua.common.support.http.HttpMethod.valueOf;

/**
 * actuator
 * @author CH
 */
@Tag(name = "actuator数据接口")
@RequestMapping("/v1/monitor/actuator")
@RestController
public class ActuatorController {

    /**
     * 配置頁面
     *
     * @return 頁面
     */
    @GetMapping
    @Operation(summary = "分页查询actuator数据")
    @SuppressWarnings("ALL")
    public ReturnResult<JsonObject> command(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "dataId") String dataId,
            @RequestParam(value = "command") String command,
            @RequestParam(value = "isOtherServer", defaultValue = "false") Boolean isOtherServer,
            @RequestParam(value = "param", defaultValue = "{}", required = false) String param,
            @RequestParam(value = "method", defaultValue = "GET") String method,
            @RequestParam(value = "data", defaultValue = "{}", required = false) String data
    ) {
        MonitorRequest request = Json.fromJson(data, MonitorRequest.class);
        if(null == request || null == request.getData()) {
            return ReturnResult.illegal("数据不存在");
        }
        try {
            return ReturnResult.of(HttpClient.newHttpMethod(valueOf(method.toUpperCase()))
                    .header(ACCEPT, APPLICATION_JSON_UTF_8)
                    .when(POST.name().equals(method), it -> it.header(CONTENT_TYPE, APPLICATION_JSON_UTF_8))
                    .url("http://" + request.getServerHost() + ":" + request.getServerPort() + FileUtils.normalize(request.getContextPath(),request.getEndpointsUrl()) + "/" + command)
                    .body(Json.getJsonObject(param))
                    .newInvoker().execute().content(JsonObject.class));
        } catch (Throwable e) {
            return ReturnResult.of(ReturnCode.SYSTEM_SERVER_NOT_FOUND, null, "操作失败");
        }
    }

}
