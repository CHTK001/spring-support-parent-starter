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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 接入码管理接口（远控管理台）。
 *
 * <p>接入码列表经 {@code GET /api/remote/access-codes} 读取、{@code POST} 保存，
 * 持久化到本地文件 远程-access-编码.json。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/api/remote/access-codes")
public class RemoteAccessCodesController {

    /** 配置文件路径（应用目录 远程-access-编码.json） */
    private static final Path CONFIG_FILE = Paths.get(System.getProperty("user.dir"), "remote-access-codes.json");

    private final ObjectMapper objectMapper = new ObjectMapper(); // 对象映射器

    /**
    * 读取接入码列表。
    *
    * @return 接入码列表（无则空数组）
     */
    @GetMapping
    public List<Map<String, Object>> list() throws IOException {
        if (Files.exists(CONFIG_FILE)) {
            return objectMapper.readValue(CONFIG_FILE.toFile(), objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        }
        return new ArrayList<>();
    }

    /**
     * 保存接入码列表（持久化到本地文件）。
     *
     * @param codes 接入码列表
     * @return 保存结果
     */
    @PostMapping
    public Map<String, Object> save(@RequestBody List<Map<String, Object>> codes) throws IOException {
        Files.write(CONFIG_FILE, objectMapper.writeValueAsBytes(codes));
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
