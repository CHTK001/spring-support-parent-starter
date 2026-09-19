package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.ai.support.manage.store.McpServerStore;
import com.chua.starter.ai.support.spi.McpOfflineProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 默认本地 MCP 离线 Provider：从 {@link McpServerStore}（{@code sys_ai_mcp_server} 表）读取。
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbMcpOfflineProvider implements McpOfflineProvider {

    private final McpServerStore mcpServerStore;

    @Override
    public String name() {
        return "本地数据库 MCP 服务器";
    }

    @Override
    public List<Map<String, Object>> listMcpServers() {
        try {
            List<Map<String, Object>> servers = mcpServerStore.listAll();
            return servers != null ? servers : List.of();
        } catch (Exception e) {
            log.warn("[McpSPI] 读取本地数据库 MCP 服务器失败: {}", e.getMessage());
            return List.of();
        }
    }
}
