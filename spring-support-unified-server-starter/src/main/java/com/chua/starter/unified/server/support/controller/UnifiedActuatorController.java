package com.chua.starter.unified.server.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.http.HttpClient;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.server.support.entity.UnifiedExecuter;
import com.chua.starter.unified.server.support.entity.UnifiedExecuterItem;
import com.chua.starter.unified.server.support.pojo.ActuatorQuery;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.http.HttpConstant.ACCEPT;
import static com.chua.common.support.http.HttpConstant.APPLICATION_JSON_UTF_8;
import static com.chua.common.support.http.HttpHeaders.CONTENT_TYPE;
import static com.chua.common.support.http.HttpMethod.POST;
import static com.chua.common.support.http.HttpMethod.valueOf;

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


    @GetMapping("oshi")
    public ReturnResult<JsonObject> oshi(@RequestParam(value = "dataId") String dataId) {
        return ReturnResult.success(unifiedExecuterItemService.getOshi(dataId));
    }

    /**
     * 进程
     *
     * @param dataId 数据id
     * @return {@link ReturnResult}<{@link JsonObject}>
     */
    @GetMapping("process")
    public ReturnPageResult<JsonObject> process( @RequestParam(value = "dataId") String dataId,
                                             @RequestParam(value = "status", required = false) String status,
                                             @RequestParam(value = "keyword", required = false) String keyword,
                                             @RequestParam(value = "page", defaultValue = "1", required = false) Integer page,
                                             @RequestParam(value = "pageSize", defaultValue = "10", required = false) Integer pageSize
                                             ) {
        return ReturnPageResult.ok(unifiedExecuterItemService.getProcess(dataId, status, keyword, page, pageSize));
    }
    /**
     * 配置頁面
     *
     * @return 頁面
     */
    @GetMapping("/actuator")
    @SuppressWarnings("ALL")
    public ReturnResult<JsonObject> command(
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
        try {
            return ReturnResult.of(HttpClient.newHttpMethod(valueOf(method.toUpperCase()))
                    .header(ACCEPT, APPLICATION_JSON_UTF_8)
                    .when(POST.name().equals(method), it -> it.header(CONTENT_TYPE, APPLICATION_JSON_UTF_8))
                    .url("http://" + actuatorQuery.getHost() + ":" + actuatorQuery.getPort() + StringUtils.startWithAppend(actuatorQuery.getContextPath(), "/") + "" + StringUtils.startWithAppend(actuatorQuery.getEndpointsUrl(), "/") + "/" + command)
                    .body(Json.toMapStringObject(param))
                    .newInvoker().execute().content(JsonObject.class));
        } catch (Throwable e) {
            return ReturnResult.of(ReturnCode.SYSTEM_SERVER_NOT_FOUND, null, "操作失败");
        }
    }
    /**
     * 配置頁面
     *
     * @return 頁面
     */
    @GetMapping("/page")
    @SuppressWarnings("ALL")
    public ReturnPageResult<UnifiedExecuterItem> configList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "profile", required = false) String profile
    ) {

        return ReturnPageResult.ok(unifiedExecuterItemService.page(new Page<>(page, pageSize), new MPJLambdaWrapper<UnifiedExecuterItem>()
                        .selectAll(UnifiedExecuterItem.class)
                        .selectAs(UnifiedExecuter::getUnifiedExecuterName, UnifiedExecuterItem::getUnifiedExecuterName)
                        .selectAs(UnifiedExecuter::getUnifiedAppname, UnifiedExecuterItem::getUnifiedAppname)
                        .innerJoin(UnifiedExecuter.class, UnifiedExecuter::getUnifiedExecuterId, UnifiedExecuterItem::getUnifiedExecuterId)
                .eq(StringUtils.isNotEmpty(profile), UnifiedExecuterItem::getUnifiedExecuterItemProfile, profile)
        ));
    }
}
