package com.chua.starter.monitor.server.resolver.adator;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
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
    public BootResponse resolve(BootRequest request) {
        String content = request.getContent();
        if(StringUtils.isEmpty(content)) {
            return BootResponse.empty();
        }

        List<MonitorLimit> list = monitorLimitService.list(Wrappers.<MonitorLimit>lambdaQuery()
                .in(MonitorLimit::getLimitProfile, Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(request.getProfile()))
                .eq(MonitorLimit::getLimitStatus, 1)
                .in(StringUtils.isNotEmpty(content), MonitorLimit::getLimitApp, Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(content))
        );
        return BootResponse.builder()
                .data(BootResponse.DataDTO.builder()
                        .commandType(CommandType.RESPONSE)
                        .content(Json.toJson(list))
                        .build())
                .build();
    }
}
