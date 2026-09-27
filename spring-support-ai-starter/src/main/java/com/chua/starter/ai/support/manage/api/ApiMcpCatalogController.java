package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.McpCatalogService;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MCP 来源清单控制器：内置推荐与在线检索的配置、检索。
 *
 * <p>与 {@link ApiMcpController}（{@code /api/mcp/servers} 服务器 CRUD）配合：
 * 本控制器只负责「发现」——内置推荐（自带可直连 transport，一键安装走
 * {@code POST /api/mcp/servers}）与在线第三方仓库检索（需手动配置传输）。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/mcp/catalog")
@RequiredArgsConstructor
@Slf4j
public class ApiMcpCatalogController {

    private final McpCatalogService mcpCatalogService;

    /**
     * 获取全部来源配置与开关。
     *
     * @return 分组配置
     */
    @GetMapping("/config")
    public ApiResult<Map<String, Object>> config() {
        return ApiResult.ok(mcpCatalogService.getConfig());
    }

    /**
     * 更新来源开关（仅管理员）。
     *
     * @param config 键为来源键、值为开关
     * @return 更新后的配置
     */
    @Permission("admin")
    @PutMapping("/config")
    public ApiResult<Map<String, Object>> updateConfig(@RequestBody Map<String, Boolean> config) {
        mcpCatalogService.updateConfig(config);
        return ApiResult.ok(mcpCatalogService.getConfig());
    }

    /**
     * 列出内置推荐 MCP（自带可直连 transport，可一键安装）。
     *
     * @return 内置推荐条目
     */
    @GetMapping("/builtin")
    public ApiResult<List<Map<String, Object>>> builtin() {
        return ApiResult.ok(mcpCatalogService.listBuiltin());
    }

    /**
     * 在已启用在线来源中检索 MCP（真实第三方仓库引用，需手动配置传输）。
     *
     * @param keyword 关键词
     * @return 在线引用条目（installable=false）
     */
    @GetMapping("/online")
    public ApiResult<List<Map<String, Object>>> online(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResult.ok(mcpCatalogService.searchOnline(keyword));
    }
}
