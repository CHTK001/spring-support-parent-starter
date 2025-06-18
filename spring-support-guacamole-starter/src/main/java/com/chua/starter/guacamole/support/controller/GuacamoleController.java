package com.chua.starter.guacamole.support.controller;

import com.chua.starter.guacamole.support.service.GuacamoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Guacamole控制器
 *
 * @author CH
 * @since 2024/7/24
 */
@Slf4j
@Controller
@RequestMapping("/guacamole")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.guacamole", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GuacamoleController {

    private final GuacamoleService guacamoleService;

    /**
     * 返回Guacamole客户端页面
     */
    @GetMapping("/client")
    public String client() {
        return "guacamole/client";
    }

    /**
     * 获取Guacamole客户端配置
     */
    @GetMapping("/config")
    @ResponseBody
    public ResponseEntity<?> getConfig(
            @RequestParam String protocol,
            @RequestParam String host,
            @RequestParam int port,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password) {

        try {
            // 构建连接参数
            String connectionString = protocol + "," + host + "," + port;
            if (username != null && !username.isEmpty()) {
                connectionString += "," + username;
            }
            if (password != null && !password.isEmpty()) {
                connectionString += "," + password;
            }

            return ResponseEntity.ok().body(connectionString);
        } catch (Exception e) {
            log.error("获取Guacamole配置时发生错误", e);
            return ResponseEntity.badRequest().body("配置错误: " + e.getMessage());
        }
    }

    /**
     * 获取Guacamole客户端JS库
     */
    @GetMapping("/js/guacamole.js")
    @ResponseBody
    public ResponseEntity<Resource> getGuacamoleJs() {
        Resource resource = new ClassPathResource("static/guacamole/guacamole-common-js/all.min.js");
        return ResponseEntity.ok().body(resource);
    }
} 