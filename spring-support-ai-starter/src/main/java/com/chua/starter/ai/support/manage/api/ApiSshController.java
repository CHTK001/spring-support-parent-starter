package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.ssh.SshConnectionManager;
import com.chua.starter.ai.support.manage.store.SysAiSshHost;
import com.chua.starter.ai.support.manage.store.SysAiSshHostStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
* 远程主机（SSH 主机簿）控制器，对齐前端 {@code /api/ssh-hosts} 契约。
*
* <p>提供可复用远程主机连接档案（{@code ssh_host_id} 主键）的增删改查，
* 密钥字段写入后不回显明文（响应恒为掩码 {@code "***"}）；链接实时状态
* 通过 {@code ssh.status} WebSocket 事件推送，快照见 {@code /statuses}。</p>
*
* <p>响应字段为 camelCase（对齐前端 {@code IApiSshHost}），
* 与 {@code SysAiConversation} 等 manage 域的 snake_大小写 契约不同——这是 SSH
* 主机簿独有的契约，前端 {@code fromApiSshHost} 仅做主键 brand 化转换。</p>
*
* @since 4.0.0.42
* @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/ssh-hosts")
@RequiredArgsConstructor
@Slf4j
public class ApiSshController {

    /**
    * 主机簿持久化存储
     */
    private final SysAiSshHostStore sshHostStore;

    /**
    * SSH 连接管理（真实握手 / 状态机 / WS 推送）
     */
    private final SshConnectionManager connectionManager;

    /**
    * 查询全部主机。
    *
    * @return 主机列表（密钥字段脱敏）
     */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(sshHostStore.listAll().stream().map(this::toResponse).toList());
    }

    /**
    * 查询单台主机。
    *
    * @param sshHostId 主机 标识
    * @return 主机（密钥字段脱敏）
     */
    @GetMapping("/{sshHostId}")
    public ApiResult<Map<String, Object>> get(@PathVariable String sshHostId) {
        return ApiResult.ok(toResponse(sshHostStore.get(sshHostId)));
    }

    /**
    * 新增主机并落库。
    *
    * @param body 请求体（camel大小写，密钥为明文）
    * @return 创建的主机（密钥字段脱敏）
     */
    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        SysAiSshHost host = new SysAiSshHost();
        host.setSshHostId(sshHostStore.newId());
        host.setName(asString(body.get("name"), "未命名主机"));
        host.setHost(asString(body.get("host"), ""));
        host.setPort(body.get("port") instanceof Number n ? n.intValue() : 22);
        host.setUsername(asString(body.get("username"), ""));
        host.setAuthType(asString(body.get("authType"), "password"));
        host.setPassword(asString(body.get("password"), null));
        host.setPrivateKey(asString(body.get("privateKey"), null));
        host.setPassphrase(asString(body.get("passphrase"), null));
        host.setCertificate(asString(body.get("certificate"), null));
        host.setSudoPassword(asString(body.get("sudoPassword"), null));
        host.setStatus("unknown");
        long now = System.currentTimeMillis();
        host.setLastConnectedAt(null);
        host.setCreatedAt(now);
        host.setUpdatedAt(now);
        return ApiResult.ok(toResponse(sshHostStore.create(host)));
    }

    /**
    * 部分更新主机（密钥字段带掩码哨兵则保留原值）。
    *
    * @param sshHostId 主机 标识
    * @param body      请求体（camel大小写，密钥为明文或掩码哨兵）
    * @return 更新后的主机（密钥字段脱敏）
     */
    @PutMapping("/{sshHostId}")
    public ApiResult<Map<String, Object>> update(@PathVariable String sshHostId,
                                                 @RequestBody Map<String, Object> body) {
        SysAiSshHost updates = new SysAiSshHost();
        if (body.containsKey("name")) {
            updates.setName(asString(body.get("name"), null));
        }
        if (body.containsKey("host")) {
            updates.setHost(asString(body.get("host"), null));
        }
        if (body.containsKey("port")) {
            updates.setPort(body.get("port") instanceof Number n ? n.intValue() : null);
        }
        if (body.containsKey("username")) {
            updates.setUsername(asString(body.get("username"), null));
        }
        if (body.containsKey("authType")) {
            updates.setAuthType(asString(body.get("authType"), null));
        }
        if (body.containsKey("password")) {
            updates.setPassword(asString(body.get("password"), null));
        }
        if (body.containsKey("privateKey")) {
            updates.setPrivateKey(asString(body.get("privateKey"), null));
        }
        if (body.containsKey("passphrase")) {
            updates.setPassphrase(asString(body.get("passphrase"), null));
        }
        if (body.containsKey("certificate")) {
            updates.setCertificate(asString(body.get("certificate"), null));
        }
        if (body.containsKey("sudoPassword")) {
            updates.setSudoPassword(asString(body.get("sudoPassword"), null));
        }
        return ApiResult.ok(toResponse(sshHostStore.update(sshHostId, updates)));
    }

    /**
    * 删除主机（同时关闭其关联链路）。
    *
    * @param sshHostId 主机 标识
    * @return 空结果
     */
    @DeleteMapping("/{sshHostId}")
    public ApiResult<Void> delete(@PathVariable String sshHostId) {
        sshHostStore.delete(sshHostId);
        connectionManager.forgetHost(sshHostId);
        return ApiResult.ok();
    }

    /**
    * 真实测试主机连通性（SSH 握手 + 探活）。
    *
    * @param sshHostId 主机 标识
    * @return 测试结果 {@code {ok, message}}
     */
    @PostMapping("/{sshHostId}/test-connection")
    public ApiResult<Map<String, Object>> testConnection(@PathVariable String sshHostId) {
        SysAiSshHost host = sshHostStore.get(sshHostId);
        return ApiResult.ok(connectionManager.testConnection(host));
    }

    /**
    * 查询全部链路实时状态快照（与 {@code ssh.status} 事件同一形状）。
    *
    * @return 状态列表（camelCase）
     */
    @GetMapping("/statuses")
    public ApiResult<List<Map<String, Object>>> statuses() {
        return ApiResult.ok(connectionManager.statuses());
    }

    /**
    * 扫描本机 {@code ~/.ssh/config} 提供的候选主机（当前返回空占位）。
    *
    * @return 扫描结果
     */
    @GetMapping("/import-candidates")
    public ApiResult<Map<String, Object>> importCandidates() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configPath", null);
        result.put("hosts", List.of());
        return ApiResult.ok(result);
    }

    /**
    * 将候选主机批量导入主机簿（当前返回空占位）。
    *
    * @param body 请求体：aliases
    * @return 导入结果
     */
    @PostMapping("/import")
    public ApiResult<Map<String, Object>> importHosts(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", List.of());
        result.put("skipped", List.of());
        return ApiResult.ok(result);
    }

    /**
    * 实体转前端契约响应（密钥字段脱敏为 {@code "***"}）。
    *
    * @param host 主机实体
    * @return camelCase 响应 映射
     */
    private Map<String, Object> toResponse(SysAiSshHost host) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sshHostId", host.getSshHostId());
        map.put("name", host.getName());
        map.put("host", host.getHost());
        map.put("port", host.getPort());
        map.put("username", host.getUsername());
        map.put("authType", host.getAuthType());
        map.put("password", mask(host.getPassword()));
        map.put("privateKey", mask(host.getPrivateKey()));
        map.put("passphrase", mask(host.getPassphrase()));
        map.put("certificate", mask(host.getCertificate()));
        map.put("sudoPassword", mask(host.getSudoPassword()));
        map.put("hostFingerprint", host.getHostFingerprint());
        map.put("status", host.getStatus());
        map.put("lastConnectedAt", host.getLastConnectedAt());
        map.put("createdAt", host.getCreatedAt());
        map.put("updatedAt", host.getUpdatedAt());
        return map;
    }

    /**
    * 密钥脱敏：存在则返回掩码哨兵，否则返回 空。
    *
    * @param value 原始值
    * @return 脱敏值
     */
    private static String mask(String value) {
        return (value == null || value.isEmpty()) ? null : "***";
    }

    /**
    * 安全取字符串（含 数字 转字符串的兜底）。
    *
    * @param value        原始值
    * @param defaultValue 缺失或空时的默认值
    * @return 字符串值
     */
    private static String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String str = String.valueOf(value);
        return str.isEmpty() ? defaultValue : str;
    }
}
