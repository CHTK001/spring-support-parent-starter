package com.chua.starter.monitor.server.resolver.adator;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.request.BadResponse;
import com.chua.common.support.protocol.request.OkResponse;
import com.chua.common.support.protocol.request.Request;
import com.chua.common.support.protocol.request.Response;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.entity.MonitorLimit;
import com.chua.starter.monitor.server.service.MonitorLimitService;
import jakarta.annotation.Resource;

import java.util.List;


/**
 * config-subscribe命令适配器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("limit_subscribe")
public class LimitSubscribeCommandAdaptor implements CommandAdaptor{

    @Resource
    private MonitorLimitService monitorLimitService;


    @Override
    public Response resolve(Request request) {
        JsonObject requestBody = request.getBody(JsonObject.class);
        if(null == requestBody) {
            return new BadResponse(request, "content is empty");
        }

        List<MonitorLimit> list = monitorLimitService.list(Wrappers.<MonitorLimit>lambdaQuery()
                .in(MonitorLimit::getLimitProfile, Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(requestBody.getString("profileName")))
                .eq(MonitorLimit::getLimitStatus, 1)
                .in(StringUtils.isNotEmpty(requestBody.getString("appName")), MonitorLimit::getLimitApp, Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(requestBody.getString("appName")))
        );
        return new OkResponse(request, Json.toJson(list));
    }
}
