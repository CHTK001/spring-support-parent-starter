package com.chua.starter.unified.server.support.adator;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedExecuterService;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;

import static com.chua.common.support.protocol.boot.CommandType.RESPONSE;

/**
 * @author CH
 */
@Spi("register")
public class ExecutorRegisterCommandAdaptor implements ExecutorCommandAdaptor{

    @Resource
    private UnifiedExecuterService unifiedExecuterService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private UnifiedServerProperties unifiedServerProperties;
    @Override
    public BootResponse resolve(BootRequest request) {
        ThreadUtils.newStaticThreadPool()
                        .execute(() -> {
                            try {
                                unifiedExecuterService.createExecutor(request);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
        return BootResponse.builder()
                .data(BootResponse.DataDTO.builder().commandType(RESPONSE).build())
                .build();
    }
}
