package com.chua.starter.unified.server.support.adator;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.server.support.entity.UnifiedConfig;
import com.chua.starter.unified.server.support.service.UnifiedConfigService;

import javax.annotation.Resource;
import java.util.List;

/**
 * config-subscribe命令适配器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("subscribe")
public class ConfigSubscribeCommandAdaptor implements ConfigCommandAdaptor{


    @Resource
    private UnifiedConfigService unifiedConfigService;

    @Override
    public BootResponse resolve(BootRequest request) {
        String content = request.getContent();
        if(StringUtils.isBlank(content)) {
            return BootResponse.empty();
        }
        List<UnifiedConfig> list = unifiedConfigService.list(Wrappers.<UnifiedConfig>lambdaQuery()
                .eq(UnifiedConfig::getUnifiedConfigProfile, request.getProfile())
                .eq(UnifiedConfig::getUnifiedConfigStatus, 1)
                .in(UnifiedConfig::getUnifiedAppname, Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(content))
        );
        return BootResponse.builder()
                .commandType(CommandType.RESPONSE)
                .content(Json.toJson(list))
                .build();
    }
}
