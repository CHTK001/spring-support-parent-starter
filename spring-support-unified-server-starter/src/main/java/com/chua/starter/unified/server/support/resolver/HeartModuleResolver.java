package com.chua.starter.unified.server.support.resolver;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedExecuterItemService;

import javax.annotation.Resource;

import static com.chua.common.support.lang.code.ReturnCode.OK;

/**
 * 心脏模块分解器
 *
 * @author CH
 */
@Spi("HEART")
public class HeartModuleResolver implements ModuleResolver{

    @Resource
    private UnifiedServerProperties unifiedServerProperties;

    @Resource
    private UnifiedExecuterItemService unifiedExecuterItemService;

    @Override
    public BootResponse resolve(BootRequest request) {
        CommandType commandType = request.getCommandType();
        if(commandType == CommandType.PING) {
            ThreadUtils.newStaticThreadPool().execute(() -> {
                try {
                    unifiedExecuterItemService.checkHeart(request);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return BootResponse.builder()
                    .code(OK.getCode())
                    .data(BootResponse.DataDTO.builder().commandType(CommandType.PONG).build()).build();
        }
        return BootResponse.empty();
    }




}
