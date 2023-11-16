package com.chua.starter.unified.server.support.controller;

import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.protocol.boot.ModuleType;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.unified.server.support.entity.RemoteRequest;
import com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.starter.unified.server.support.resolver.ModuleResolver;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author CH
 */
@RestController
public class UnifiedController implements InitializingBean, DisposableBean, Runnable {

    private ScheduledExecutorService executorService;


    @Resource
    private UnifiedServerProperties unifiedServerProperties;


    /**
     * 注册
     *
     * @param remoteRequest 请求
     * @return {@link BootResponse}
     */
    @PostMapping
    public BootResponse home(@RequestBody RemoteRequest remoteRequest) {
        BootRequest request = remoteRequest.getRequest(unifiedServerProperties);
        if(null == request) {
            return BootResponse.notSupport();
        }

        CommandType commandType = request.getCommandType();
        if(null == commandType) {
            return BootResponse.notSupport();
        }

        ModuleType moduleType = request.getModuleType();
        if(null == moduleType) {
            return BootResponse.notSupport();
        }

        return Optional.ofNullable(ServiceProvider.of(ModuleResolver.class).getNewExtension(moduleType)
                .resolve(request)).orElse(BootResponse.notSupport());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        executorService = ThreadUtils.newScheduledThreadPoolExecutor(1, "protocol-heart");
        executorService.scheduleAtFixedRate(this, 0, 1, TimeUnit.MINUTES);
    }

    @Override
    public void run() {

    }

    @Override
    public void destroy() throws Exception {
        IoUtils.closeQuietly(executorService);
    }
}
