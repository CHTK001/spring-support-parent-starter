package com.chua.com.chua.starter.unified.server.support.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chua.com.chua.starter.unified.server.support.properties.UnifiedServerProperties;
import com.chua.common.support.json.Json;
import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.ThreadUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.constant.CommonConstant.EMPTY;

/**
 * @author CH
 */
@RestController
@RequestMapping("/")
public class UnifiedController implements InitializingBean, DisposableBean, Runnable {

    private ScheduledExecutorService executorService;
    private List<String> ipPool = new CopyOnWriteArrayList<>();

    @Resource
    private UnifiedServerProperties unifiedServerProperties;


    /**
     * 注册
     *
     * @param request 要求
     * @return {@link BootResponse}
     */
    @PostMapping("register")
    public BootResponse register(@RequestBody BootRequest request) {
        return new BootResponse();
    }

    /**
     * 注册
     *
     * @param request 要求
     * @return {@link BootResponse}
     */
    @PostMapping("unregister")
    public BootResponse unregister(@RequestBody BootRequest request) {
        return new BootResponse();
    }

    /**
     * 心跳
     *
     * @param request 要求
     * @return {@link BootResponse}
     */
    @PostMapping({"heart", "ping"})
    public BootResponse heart(@RequestBody BootRequest request) {
        if(request.getCommandType() == CommandType.PING) {
            JSONObject jsonObject = Json.fromJson(request.getContent(), JSONObject.class);
            ipPool.add(jsonObject.getString("host") + ":" + jsonObject.getString("port"));
            return new BootResponse(CommandType.PONG, EMPTY);
        }
        return new BootResponse();
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
