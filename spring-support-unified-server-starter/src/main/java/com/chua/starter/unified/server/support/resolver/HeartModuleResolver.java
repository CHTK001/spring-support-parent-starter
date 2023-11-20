package com.chua.starter.unified.server.support.resolver;

import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.annotations.Spi;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.chua.starter.unified.server.support.constant.UnifiedConstant.EXECUTOR_NAME;

/**
 * 心脏模块分解器
 *
 * @author CH
 */
@Spi("HEART")
public class HeartModuleResolver implements ModuleResolver{

    @Resource
    private RedisTemplate<String, BootRequest> redisTemplate;

    @Resource
    private UnifiedServerProperties unifiedServerProperties;
    @Override
    public BootResponse resolve(BootRequest request) {
        CommandType commandType = request.getCommandType();
        if(commandType == CommandType.PING) {
            String appName = request.getAppName();
            JSONObject jsonObject = JSONObject.parseObject(request.getContent());
            redisTemplate.opsForValue()
                    .set(EXECUTOR_NAME + appName + ":" + jsonObject.getString("host") + "_" + jsonObject.getString("port"), request, unifiedServerProperties.getKeepAliveTimeout(), TimeUnit.SECONDS);
            return BootResponse.builder().data(BootResponse.DataDTO.builder().commandType(CommandType.PONG).build()).build();
        }
        return null;
    }
}
