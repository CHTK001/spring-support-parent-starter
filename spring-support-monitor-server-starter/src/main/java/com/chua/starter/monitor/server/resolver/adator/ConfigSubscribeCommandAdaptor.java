package com.chua.starter.monitor.server.resolver.adator;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.entity.MonitorConfig;
import com.chua.starter.monitor.server.service.MonitorConfigService;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * config-subscribe命令适配器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("config_subscribe")
public class ConfigSubscribeCommandAdaptor implements CommandAdaptor{


    @Resource
    private MonitorConfigService monitorConfigService;


    @Override
    public BootResponse resolve(BootRequest request) {
        String content = request.getContent();
        if(StringUtils.isEmpty(content)) {
            return BootResponse.empty();
        }

        List<MonitorConfig> list = monitorConfigService.list(Wrappers.<MonitorConfig>lambdaQuery()
                .eq(MonitorConfig::getConfigProfile, Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(request.getProfile()))
                .eq(MonitorConfig::getConfigStatus, 1)
                .in(StringUtils.isNotEmpty(content), MonitorConfig::getConfigAppname,
                        Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(content))
        );
        return BootResponse.builder()
                .data(BootResponse.DataDTO.builder()
                        .commandType(CommandType.RESPONSE)
                        .content(Json.toJson(list))
                        .build())
                .build();
    }
}
