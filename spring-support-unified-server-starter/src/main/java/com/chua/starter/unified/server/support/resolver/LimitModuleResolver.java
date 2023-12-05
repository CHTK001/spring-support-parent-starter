package com.chua.starter.unified.server.support.resolver;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.JsonWriter;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.server.support.entity.UnifiedLimit;
import com.chua.starter.unified.server.support.service.UnifiedLimitService;

import javax.annotation.Resource;
import java.util.List;

/**
 * 配置模块解析器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("limit")
public class LimitModuleResolver implements ModuleResolver{

    @Resource
    private UnifiedLimitService unifiedLimitService;

    @Override
    public BootResponse resolve(BootRequest request) {
        String content = request.getContent();
        if(StringUtils.isBlank(content)) {
            return BootResponse.empty();
        }
        List<UnifiedLimit> list = unifiedLimitService.list(Wrappers.<UnifiedLimit>lambdaQuery()
                .eq(UnifiedLimit::getUnifiedLimitProfile, request.getProfile())
                .eq(UnifiedLimit::getUnifiedLimitStatus, 1)
                .in(UnifiedLimit::getUnifiedAppname, Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(content))
        );
        return BootResponse.builder()
                .data(BootResponse.DataDTO.builder()
                        .commandType(CommandType.RESPONSE)
                        .content(JsonWriter.builder()
                                .ignore("unifiedLimitId")
                                .ignore("unifiedLimitStatus")
                                .ignore("createTime")
                                .ignore("updateTime")
                                .ignore("unifiedAppname")
                                .toJsonString(list))
                        .build())
                .build();
    }
}
