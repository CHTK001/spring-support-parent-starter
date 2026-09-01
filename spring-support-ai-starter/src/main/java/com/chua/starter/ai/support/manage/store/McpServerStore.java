package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.manage.mapper.SysAiMcpServerMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务器档案的数据库存储服务。
 *
 * <p>基于 {@link SysAiMcpServerMapper} 持久化到 {@code sys_ai_mcp_server} 表，
 * 并把实体与前端契约的 {@code Map<String, Object>} 之间做 JSON 序列化转换
 * （transport 字段存 JSON 文本）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpServerStore {

    private final SysAiMcpServerMapper mcpServerMapper;
    private final ObjectMapper objectMapper;

    /**
     * 列出全部 MCP 服务器（按创建时间升序）
     */
    public List<Map<String, Object>> listAll() {
        List<SysAiMcpServer> entities = mcpServerMapper.selectList(
                new LambdaQueryWrapper<SysAiMcpServer>()
                        .orderByAsc(SysAiMcpServer::getCreatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiMcpServer entity : entities) {
            result.add(toContract(entity));
        }
        return result;
    }

    /**
     * 按 id 查询
     */
    public Map<String, Object> get(String serverId) {
        SysAiMcpServer entity = mcpServerMapper.selectById(serverId);
        return entity == null ? null : toContract(entity);
    }

    /**
     * 新增（由调用方预先填充 id 与时间戳）
     */
    public Map<String, Object> insert(Map<String, Object> contract) {
        SysAiMcpServer entity = fromContract(contract);
        mcpServerMapper.insert(entity);
        return toContract(entity);
    }

    /**
     * 更新（按 id，非 null 字段覆盖）
     */
    public Map<String, Object> update(Map<String, Object> contract) {
        SysAiMcpServer entity = fromContract(contract);
        mcpServerMapper.updateById(entity);
        return toContract(mcpServerMapper.selectById(entity.getMcpServerId()));
    }

    /**
     * 删除
     */
    public void delete(String serverId) {
        mcpServerMapper.deleteById(serverId);
    }

    /**
     * 契约 Map → 实体
     */
    private SysAiMcpServer fromContract(Map<String, Object> c) {
        SysAiMcpServer e = new SysAiMcpServer();
        e.setMcpServerId(str(c.get("mcp_server_id")));
        e.setName(str(c.get("name")));
        e.setDescription(str(c.get("description")));
        e.setTransportJson(writeJson(c.get("transport")));
        e.setOriginalJson(c.get("original_json") == null ? null : str(c.get("original_json")));
        e.setBuiltin(bool(c.get("builtin")));
        e.setEnabled(bool(c.get("enabled")));
        e.setLastTestStatus(str(c.get("last_test_status")));
        e.setLastConnectedAt(longVal(c.get("last_connected")));
        e.setCreatedAt(longVal(c.get("created_at")));
        e.setUpdatedAt(longVal(c.get("updated_at")));
        return e;
    }

    /**
     * 实体 → 契约 Map（前端 IMcpServer 结构）
     */
    private Map<String, Object> toContract(SysAiMcpServer e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mcp_server_id", e.getMcpServerId());
        m.put("name", e.getName());
        if (e.getDescription() != null) m.put("description", e.getDescription());
        m.put("transport", parseJson(e.getTransportJson()));
        m.put("original_json", e.getOriginalJson() == null ? "{}" : e.getOriginalJson());
        m.put("builtin", Boolean.TRUE.equals(e.getBuiltin()));
        m.put("enabled", !Boolean.FALSE.equals(e.getEnabled()));
        m.put("last_test_status", e.getLastTestStatus() == null ? "disconnected" : e.getLastTestStatus());
        if (e.getLastConnectedAt() != null) m.put("last_connected", e.getLastConnectedAt());
        m.put("created_at", e.getCreatedAt());
        m.put("updated_at", e.getUpdatedAt());
        return m;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Boolean bool(Object v) {
        return v instanceof Boolean ? (Boolean) v : (v == null ? null : Boolean.parseBoolean(String.valueOf(v)));
    }

    private Long longVal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String writeJson(Object v) {
        if (v == null) return "{}";
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            log.warn("[McpServerStore] transport 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[McpServerStore] transport 反序列化失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}
