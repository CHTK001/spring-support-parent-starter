package com.chua.starter.unified.server.support.adator;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.service.UnifiedExecuterService;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.protocol.boot.CommandType.RESPONSE;
import static com.chua.starter.unified.server.support.constant.UnifiedConstant.EXECUTOR_NAME;

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
        String appName = request.getAppName();
        unifiedExecuterService.createExecutor(request);
        JSONObject jsonObject = JSONObject.parseObject(request.getContent());
        redisTemplate.opsForValue()
                .set(EXECUTOR_NAME + appName + ":" + jsonObject.getString("host") + "_" + jsonObject.getString("port"), request, unifiedServerProperties.getKeepAliveTimeout(), TimeUnit.SECONDS);

        return BootResponse.builder().commandType(RESPONSE).build();
    }
}
