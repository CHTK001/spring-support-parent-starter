package com.chua.starter.unified.server.support.resolver;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.server.support.entity.UnifiedMybatis;
import com.chua.starter.unified.server.support.service.UnifiedMybatisService;

import javax.annotation.Resource;
import java.util.List;

/**
 * 配置模块解析器
 *
 * @author CH
 * @since 2023/11/16
 */
@Spi("mybatis")
public class MaybatisModuleResolver implements ModuleResolver{

    @Resource
    private UnifiedMybatisService unifiedMybatisService;

    @Override
    public BootResponse resolve(BootRequest request) {
        CommandType commandType = request.getCommandType();
        if(commandType != CommandType.SUBSCRIBE) {
            return BootResponse.notSupport();
        }

        String content = request.getContent();
        if(StringUtils.isBlank(content)) {
            return BootResponse.empty();
        }
        List<UnifiedMybatis> list = unifiedMybatisService.list(Wrappers.<UnifiedMybatis>lambdaQuery()
                .eq(UnifiedMybatis::getUnifiedMybatisProfile, request.getProfile())
                .in(UnifiedMybatis::getUnifiedAppname, Splitter.on(',').trimResults().omitEmptyStrings().splitToSet(content))
        );
        return BootResponse.builder()
                .data(BootResponse.DataDTO.builder()
                        .commandType(CommandType.RESPONSE)
                        .content(Json.toJson(list))
                        .build())
                .build();
    }
}
