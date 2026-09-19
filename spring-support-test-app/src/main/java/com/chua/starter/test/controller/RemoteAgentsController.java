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
 * 被控端管理接口（远控管理台）。
 *
 * <p>被控端列表经 {@code GET /api/remote/agents} 读取、{@code POST} 保存，
 * 持久化到本地文件 远程-智能体.json。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/api/remote/agents")
public class RemoteAgentsController {

    /** 配置文件路径（应用目录 远程-智能体.json） */
    private static final Path CONFIG_FILE = Paths.get(System.getProperty("user.dir"), "remote-agents.json");

    private final ObjectMapper objectMapper = new ObjectMapper(); // 对象映射器

    /**
    * 读取被控端列表。
    *
    * @return 被控端列表（无则空数组）
     */
    @GetMapping
    public List<Map<String, Object>> list() throws IOException {
        if (Files.exists(CONFIG_FILE)) {
            return objectMapper.readValue(CONFIG_FILE.toFile(), objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        }
        return seed();
    }

    /**
     * 保存被控端列表（持久化到本地文件）。
     *
     * @param agents 被控端列表
     * @return 保存结果
     */
    @PostMapping
    public Map<String, Object> save(@RequestBody List<Map<String, Object>> agents) throws IOException {
        Files.write(CONFIG_FILE, objectMapper.writeValueAsBytes(agents));
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /**
     * 默认种子数据（首次访问无文件时返回）
     *
     * @return 参见的结果
     */
    private List<Map<String, Object>> seed() {
        List<Map<String, Object>> agents = new ArrayList<>();
        Map<String, Object> a1 = new HashMap<>();
        a1.put("id", "shell-agent1");
        a1.put("agentType", "SHELL");
        a1.put("platform", "Linux");
        a1.put("desktopSupported", false);
        a1.put("status", "在线");
        a1.put("sshHost", "124.221.230.112:22");
        agents.add(a1);
        Map<String, Object> a2 = new HashMap<>();
        a2.put("id", "push-agent1");
        a2.put("agentType", "PUSH");
        a2.put("platform", "Windows");
        a2.put("desktopSupported", true);
        a2.put("status", "在线");
        a2.put("sshHost", "-");
        agents.add(a2);
        return agents;
    }
}
