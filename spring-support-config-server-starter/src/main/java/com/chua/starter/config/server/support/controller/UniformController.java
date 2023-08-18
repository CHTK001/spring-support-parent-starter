package com.chua.starter.config.server.support.controller;

import com.chua.common.support.annotations.Permission;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.uniform.Uniform;
import com.chua.starter.sse.support.SseTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 统一中心
 *
 * @author CH
 * @since 2022/8/1 14:54
 */
@RequestMapping("/v1/uniform")
@RestController
public class UniformController {

    @Resource
    private SseTemplate sseTemplate;

    @Resource
    private Uniform uniform;

    @GetMapping(value = "search")
    public Result<List<Map<String, Object>>> search(String keyword) {
        return Result.success(uniform.search(keyword));
    }
    /**
     * 注册监听
     *
     * @param mode 任务ID
     * @return 任务ID
     */
    @Permission(role = {"ADMIN", "OPS"})
    @GetMapping(value = "subscribe/{mode}")
    public SseEmitter subscribe(@PathVariable String mode, HttpServletResponse response) throws IOException {
        if (StringUtils.isBlank(mode)) {
            throw new RuntimeException("订阅的任务不存在");
        }
        return sseTemplate.createSseEmitter(ConfigConstant.SUBSCRIBE_SSE, mode);
    }
}
