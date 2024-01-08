package com.chua.starter.unified.server.support.resolver.adator;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedExecuterService;
import com.chua.starter.unified.server.support.service.UnifiedLogService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

import static com.chua.common.support.protocol.boot.CommandType.RESPONSE;

/**
 * @author CH
 */
@Spi("register")
@Slf4j
public class ExecutorRegisterCommandAdaptor implements ExecutorCommandAdaptor{

    @Resource
    private UnifiedExecuterService unifiedExecuterService;

    @Resource
    private UnifiedLogService unifiedLogService;

    @Resource
    private UnifiedServerProperties unifiedServerProperties;
    @Override
    public BootResponse resolve(BootRequest request) {
        ThreadUtils.newStaticThreadPool()
                        .execute(() -> {
                            try {
                                unifiedExecuterService.createExecutor(request);
                            } catch (Exception e) {
                                log.error("", e);
                            }
                        });
        return BootResponse.builder()
                .data(BootResponse.DataDTO.builder()
                        .commandType(RESPONSE)
                        .content(Json.toJson(unifiedServerProperties.getEndpoint()))
                        .build())
                .build();
    }
}
