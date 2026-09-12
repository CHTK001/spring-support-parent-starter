package com.chua.starter.test.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
* 远控管理台配置接口（保存配置到后台）。
*
* <p>远控页面的连接配置（网关地址/接入令牌/目标被控端/验证码/画面源）经
* {@code POST /api/remote/config} 持久化到本地文件，启动时经 {@code GET} 回显。</p>
*
* @author CH
* @since 4.0.0.42
 */
@RestController
@RequestMapping("/api/remote/config")
public class RemoteConfigController {

    /** 配置文件路径（应用目录 远程-配置.json） */
    private static final Path CONFIG_FILE = Paths.get(System.getProperty("user.dir"), "remote-config.json");

    private final ObjectMapper objectMapper = new ObjectMapper(); // 对象映射器

    /**
    * 回显配置。
    *
    * @return 已保存的配置（无则空对象）
     */
    @GetMapping
    public Map<String, Object> getConfig() throws IOException {
        if (Files.exists(CONFIG_FILE)) {
            return objectMapper.readValue(CONFIG_FILE.toFile(), Map.class);
        }
        return new HashMap<>();
    }

    /**
    * 保存配置（持久化到本地文件）。
    *
    * @param config 远控连接配置
    * @return 保存结果
     */
    @PostMapping
    public Map<String, Object> saveConfig(@RequestBody Map<String, Object> config) throws IOException {
        Files.write(CONFIG_FILE, objectMapper.writeValueAsBytes(config));
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
