package com.chua.starter.unified.server.support.controller;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ResultCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.code.ReturnResultCode;
import com.chua.starter.unified.server.support.pojo.ActuatorQuery;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.http.HttpConstant.ACCEPT;
import static com.chua.common.support.http.HttpConstant.APPLICATION_JSON_UTF_8;
import static com.chua.common.support.http.HttpHeaders.CONTENT_TYPE;

/**
 * actuator
 * @author CH
 */
@RequestMapping("/v1/app")
@RestController
public class UnifiedActuatorController {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private UnifiedExecuterItemService unifiedExecuterItemService;

    private final Cache<String, ActuatorQuery> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();
    /**
     * 配置頁面
     *
     * @return 頁面
     */
    @GetMapping("/actuator")
    @ResponseBody
    @SuppressWarnings("ALL")
    public ReturnResult<JSONObject> command(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "dataId") String dataId,
            @RequestParam(value = "command") String command,
            @RequestParam(value = "isOtherServer", defaultValue = "false") Boolean isOtherServer,
            @RequestParam(value = "param", defaultValue = "{}", required = false) String param,
            @RequestParam(value = "method", defaultValue = "GET") String method
    ) {
        ActuatorQuery actuatorQuery = unifiedExecuterItemService.getActuatorQuery(dataId);
        if(null == actuatorQuery) {
            return ReturnResult.illegal("数据不存在");
        }

        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(ACCEPT, APPLICATION_JSON_UTF_8);
        if (httpMethod == HttpMethod.POST) {
            httpHeaders.add(CONTENT_TYPE, APPLICATION_JSON_UTF_8);
        }
        HttpEntity httpEntity = new HttpEntity<>(Json.toMapStringObject(param), httpHeaders);
        ResponseEntity<JSONObject> exchange = null;
        try {
            if(!isOtherServer) {
                exchange = restTemplate.exchange(
                        "http://" + detail.getAppHost() + ":" + detail.getAppSpringPort() + "" + detail.getAppContextPath() + "" + detail.getAppActuator() + "/" + command,
                        httpMethod, httpEntity, JSONObject.class
                );
            } else {
                if (detail.getAppPort() == 0) {
                    return ReturnResult.ok();
                }
                exchange = restTemplate.exchange(
                        "http://" + detail.getAppHost() + ":" + detail.getAppPort() + "/config/listener/" + command,
                        httpMethod, httpEntity, JSONObject.class
                );
            }
        } catch (Throwable e) {
            return ReturnResult.of(ReturnResultCode.SYSTEM_SERVER_NOT_FOUND, null, "操作失败");
        }
        JSONObject exchangeBody = exchange.getBody();
        if (null != exchangeBody) {
            return ReturnResult.of(exchangeBody);
        }
        return ReturnResult.of(ResultCode.transferForHttpCode(exchange.getStatusCodeValue()));
    }

}
