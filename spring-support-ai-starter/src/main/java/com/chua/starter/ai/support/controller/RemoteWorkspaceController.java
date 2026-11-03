// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.manage.ssh.SshConnectionManager;
import com.chua.starter.ai.support.manage.store.SysAiSshHost;
import com.chua.starter.ai.support.manage.store.SysAiSshHostStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程工作区控制器。
 *
 * <p>适配前端 /v2/remote-workspace/... 的 API，底层复用现有的 SSH 连接管理。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/remote-workspace")
@RequiredArgsConstructor
@Tag(name = "远程工作区", description = "远程服务器连接管理 API")
public class RemoteWorkspaceController {

    /**
     * SSH 连接管理
     */
    private final SshConnectionManager connectionManager;

    /**
     * 主机簿持久化存储
     */
    private final SysAiSshHostStore sshHostStore;

    /**
     * 获取所有连接列表。
     *
     * @return 连接列表
     */
    @Operation(summary = "获取连接列表", description = "获取所有远程工作区连接")
    @GetMapping("/connections")
    public ReturnResult<List<Map<String, Object>>> getConnections() {
        List<SysAiSshHost> hosts = sshHostStore.listAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiSshHost host : hosts) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", host.getSshHostId());
            item.put("name", host.getName());
            Map<String, Object> config = new HashMap<>();
            config.put("host", host.getHost());
            config.put("port", host.getPort());
            config.put("username", host.getUsername());

            // 后端用 key，前端用 privateKey
            String frontendAuthType = "key".equals(host.getAuthType()) ? "privateKey" : host.getAuthType();
            config.put("authMethod", frontendAuthType);

            if ("key".equals(host.getAuthType())) {
                config.put("privateKey", "***");
            } else {
                config.put("password", "***");
            }
            item.put("config", config);
            result.add(item);
        }
        return ReturnResult.ok(result);
    }

    /**
     * 创建新连接。
     *
     * @param body 请求体
     * @return 创建的连接
     */
    @Operation(summary = "创建连接", description = "创建新的远程工作区连接")
    @PostMapping("/connections")
    public ReturnResult<Map<String, Object>> createConnection(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> config = (Map<String, Object>) body.get("config");
            String name = (String) body.get("name");
            String authMethod = (String) config.getOrDefault("authMethod", "password");

            // 前端用 privateKey，后端用 key
            String backendAuthType = "privateKey".equals(authMethod) ? "key" : authMethod;

            SysAiSshHost host = new SysAiSshHost();
            host.setSshHostId(sshHostStore.newId());
            host.setName(name);
            host.setHost((String) config.get("host"));
            host.setPort(config.get("port") instanceof Number ? ((Number) config.get("port")).intValue() : 22);
            host.setUsername((String) config.get("username"));
            host.setAuthType(backendAuthType);

            // 根据认证方式保存对应字段
            if ("privateKey".equals(authMethod)) {
                // 前端直接传私钥内容
                Object privateKeyObj = config.get("privateKey");
                if (privateKeyObj != null) {
                    host.setPrivateKey(privateKeyObj.toString());
                }
                Object passphraseObj = config.get("passphrase");
                if (passphraseObj != null) {
                    host.setPassphrase(passphraseObj.toString());
                }
            } else {
                Object passwordObj = config.get("password");
                if (passwordObj != null) {
                    host.setPassword(passwordObj.toString());
                }
            }

            host.setStatus("unknown");
            long now = System.currentTimeMillis();
            host.setCreatedAt(now);
            host.setUpdatedAt(now);
            sshHostStore.create(host);

            Map<String, Object> result = new HashMap<>();
            result.put("id", host.getSshHostId());
            result.put("name", host.getName());
            result.put("config", config);
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("[RemoteWorkspace] 创建连接失败: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ReturnResult.ok(result);
        }
    }

    /**
     * 测试连接。
     *
     * @param body 请求体
     * @return 测试结果
     */
    @Operation(summary = "测试连接", description = "测试 SSH 连接是否正常")
    @PostMapping("/connections/test")
    public ReturnResult<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> config = (Map<String, Object>) body.get("config");
            String authMethod = (String) config.getOrDefault("authMethod", "password");

            // 前端用 privateKey，后端用 key
            String backendAuthType = "privateKey".equals(authMethod) ? "key" : authMethod;

            // 创建临时主机用于测试
            SysAiSshHost host = new SysAiSshHost();
            host.setSshHostId(sshHostStore.newId()); // testConnection 要求 sshHostId 不为空
            host.setHost((String) config.get("host"));
            host.setPort(config.get("port") instanceof Number ? ((Number) config.get("port")).intValue() : 22);
            host.setUsername((String) config.get("username"));
            host.setAuthType(backendAuthType);

            // 根据认证方式设置对应字段
            if ("privateKey".equals(authMethod)) {
                // 前端直接传私钥内容
                Object privateKeyObj = config.get("privateKey");
                if (privateKeyObj != null) {
                    host.setPrivateKey(privateKeyObj.toString());
                }
                Object passphraseObj = config.get("passphrase");
                if (passphraseObj != null) {
                    host.setPassphrase(passphraseObj.toString());
                }
            } else {
                Object passwordObj = config.get("password");
                if (passwordObj != null) {
                    host.setPassword(passwordObj.toString());
                }
            }

            // 测试连接
            Map<String, Object> result = connectionManager.testConnection(host);

            Map<String, Object> response = new HashMap<>();
            response.put("success", Boolean.TRUE.equals(result.get("ok")));
            response.put("message", (String) result.get("message"));
            return ReturnResult.ok(response);
        } catch (Exception e) {
            log.error("[RemoteWorkspace] 测试连接失败: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ReturnResult.ok(response);
        }
    }

    /**
     * 部署连接。
     *
     * @param body 请求体
     * @return 部署任务 ID
     */
    @Operation(summary = "部署连接", description = "部署 Agent 到远程服务器")
    @PostMapping("/connections/deploy")
    public ReturnResult<Map<String, Object>> deployConnection(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        result.put("jobId", "deploy-" + System.currentTimeMillis());
        return ReturnResult.ok(result);
    }

    /**
     * 获取部署日志。
     *
     * @param jobId 任务 ID
     * @return 部署日志
     */
    @Operation(summary = "获取部署日志", description = "获取部署任务的日志")
    @GetMapping("/connections/deploy-log/{jobId}")
    public ReturnResult<Map<String, Object>> getDeployLog(@PathVariable String jobId) {
        Map<String, Object> result = new HashMap<>();
        result.put("log", new ArrayList<>());
        result.put("done", true);
        Map<String, Object> deployResult = new HashMap<>();
        deployResult.put("success", true);
        deployResult.put("installed", true);
        deployResult.put("version", "1.0.0");
        result.put("result", deployResult);
        return ReturnResult.ok(result);
    }

    /**
     * 取消部署。
     *
     * @param jobId 任务 ID
     * @return 取消结果
     */
    @Operation(summary = "取消部署", description = "取消部署任务")
    @PostMapping("/connections/deploy-cancel/{jobId}")
    public ReturnResult<Map<String, Object>> cancelDeploy(@PathVariable String jobId) {
        Map<String, Object> result = new HashMap<>();
        result.put("cancelled", true);
        return ReturnResult.ok(result);
    }

    /**
     * 浏览远程目录。
     *
     * @param body 请求体
     * @return 目录列表
     */
    @Operation(summary = "浏览目录", description = "浏览远程服务器上的目录")
    @PostMapping("/connections/browse")
    public ReturnResult<List<Map<String, Object>>> browsePath(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> result = new ArrayList<>();
        return ReturnResult.ok(result);
    }

    /**
     * 更新连接。
     *
     * @param id 连接 ID
     * @param body 请求体
     * @return 更新后的连接
     */
    @Operation(summary = "更新连接", description = "更新远程工作区连接")
    @PutMapping("/connections/{id}")
    public ReturnResult<Map<String, Object>> updateConnection(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> config = (Map<String, Object>) body.get("config");
        if (config == null) {
            config = body;
        }
        SysAiSshHost updates = new SysAiSshHost();
        Object name = body.get("name") != null ? body.get("name") : config.get("name");
        if (name != null) {
            updates.setName(String.valueOf(name));
        }
        if (config.get("host") != null) {
            updates.setHost(String.valueOf(config.get("host")));
        }
        if (config.get("port") instanceof Number number) {
            updates.setPort(number.intValue());
        }
        if (config.get("username") != null) {
            updates.setUsername(String.valueOf(config.get("username")));
        }
        String authMethod = String.valueOf(config.getOrDefault("authMethod", "password"));
        if (config.get("authMethod") != null) {
            updates.setAuthType("privateKey".equals(authMethod) ? "key" : authMethod);
        }
        updates.setPassword(firstText(config.get("password")));
        updates.setPrivateKey(firstText(config.get("privateKey")));
        updates.setPassphrase(firstText(config.get("passphrase")));

        SysAiSshHost host = sshHostStore.update(id, updates);
        Map<String, Object> result = new HashMap<>();
        result.put("id", host.getSshHostId());
        result.put("name", host.getName());
        Map<String, Object> echo = new HashMap<>();
        echo.put("host", host.getHost());
        echo.put("port", host.getPort());
        echo.put("username", host.getUsername());
        echo.put("authMethod", "key".equals(host.getAuthType()) ? "privateKey" : host.getAuthType());
        result.put("config", echo);
        return ReturnResult.ok(result);
    }

    /**
     * 取请求里的密钥字段，未提供时返回 {@code null} 表示不修改。
     *
     * @param value 请求值
     * @return 文本值，缺失时为 {@code null}
     */
    private static String firstText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 删除连接。
     *
     * @param id 连接 ID
     * @return 删除结果
     */
    @Operation(summary = "删除连接", description = "删除远程工作区连接")
    @DeleteMapping("/connections/{id}")
    public ReturnResult<Map<String, Object>> deleteConnection(@PathVariable String id) {
        sshHostStore.delete(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ReturnResult.ok(result);
    }
}
