package com.chua.starter.unified.server.support.controller;

import com.chua.common.support.annotations.Permission;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.sse.support.Emitter;
import com.chua.starter.sse.support.SseTemplate;
import com.chua.starter.unified.server.support.service.uniform.Uniform;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 统一中心
 *
 * @author CH
 * @since 2022/8/1 14:54
 */
@RequestMapping("/v1/message")
@RestController
public class MessageController {

    @Resource
    private SseTemplate sseTemplate;

    @Resource
    private Uniform uniform;

    /**
     * 注册监听
     *
     * @param mode 任务ID
     * @return 任务ID
     */
    @Permission(role = {"ADMIN", "OPS"})
    @GetMapping(value = "subscribe/{mode}/{appName}")
    public SseEmitter subscribe(@PathVariable String mode, @PathVariable String appName, HttpServletResponse response) throws IOException {
        if (StringUtils.isBlank(mode)) {
            throw new RuntimeException("订阅的任务不存在");
        }
        return sseTemplate.createSseEmitter(Emitter.builder().clientId(appName).event(mode.toUpperCase()).build());
    }
}
