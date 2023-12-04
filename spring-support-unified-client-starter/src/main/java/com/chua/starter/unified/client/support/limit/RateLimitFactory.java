package com.chua.starter.unified.client.support.limit;

import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.server.annotations.ServiceMapping;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.task.limit.RateLimitOption;
import com.chua.common.support.task.limit.resolver.RateLimitResolver;
import com.chua.common.support.utils.ThreadUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流
 * @author CH
 */
public class RateLimitFactory {

    private static final RateLimitFactory INSTANCE = new RateLimitFactory();

    private final Map<String, RateLimitResolver> urlAndResolver = new ConcurrentHashMap<>();
    RateLimitFactory() {
    }


    /**
     * 获取实例
     *
     * @return {@link RateLimitFactory}
     */
    public static RateLimitFactory getInstance() {
        return INSTANCE;
    }


    /**
     * 限制配置
     *
     * @param request 请求
     * @return {@link BootResponse}
     */
    @ServiceMapping("limiter")
    public BootResponse limitConfig(BootRequest request) {
        String content = request.getContent();
        JsonObject jsonObject = Json.getJsonObject(content);
        if(null != jsonObject && !jsonObject.isEmpty()) {
            ThreadUtils.newStaticThreadPool().execute(() -> {
                for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                    RateLimitOption value = (RateLimitOption) entry.getValue();
                    try {
                        urlAndResolver.put(entry.getKey(), ServiceProvider.of(RateLimitResolver.class).getNewExtension(value.getResolver(), value, entry.getKey()));
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        return BootResponse.ok();
    }

}
