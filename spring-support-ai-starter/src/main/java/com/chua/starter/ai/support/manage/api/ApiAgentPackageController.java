package com.chua.starter.ai.support.manage.api;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.manage.agent.AgentPackageService;
import com.chua.starter.ai.support.manage.api.market.AgentMarketSourceProvider;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体安装包控制器。
 *
 * <p>对标技能的「安装」能力：智能体以目录 + {@code AGENT.md}（YAML frontmatter + 正文）
 * 分发，子智能体位于 {@code agents/&lt;name&gt;/AGENT.md}。安装后写入
 * {@code {nomifun.agents-root}/user/&lt;name&gt;} 并落库到 {@code sys_ai_agent}
 * （复用 {@link AgentPackageService}，含运行时重建）。</p>
 *
 * <p>市场来源通过 {@link AgentMarketSourceProvider} SPI 枚举，默认提供内置源；
 * 新增远程源只需按同一 SPI 加实现与注册文件，无需改动本控制器。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/agents/packages")
@RequiredArgsConstructor
public class ApiAgentPackageController {

    /**
     * 智能体安装包服务
     */
    private final AgentPackageService packageService;

    /**
     * 列出磁盘上已安装的智能体包。
     *
     * @return 包列表
     */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(packageService.listInstalled());
    }

    /**
     * 智能体包相关目录。
     *
     * @return {root, user, builtin}
     */
    @GetMapping("/paths")
    public ApiResult<Map<String, Object>> paths() {
        return ApiResult.ok(packageService.paths());
    }

    /**
     * 导入智能体包（multipart）：上传 zip，或以表单字段 {@code path} 指定本地目录。
     *
     * @param file zip 文件（与 path 二选一）
     * @param path 本地目录（与 file 二选一）
     * @return 安装结果
     */
    @Permission("admin")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> importPackage(@RequestParam(value = "file", required = false) MultipartFile file,
                                                       @RequestParam(value = "path", required = false) String path) {
        if (file != null && !file.isEmpty()) {
            try {
                return ApiResult.ok(packageService.importFromZip(file.getInputStream()));
            } catch (IOException e) {
                throw new ApiException(ApiErrorCode.INVALID_PARAM, "读取上传文件失败: " + e.getMessage());
            }
        }
        if (path == null || path.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "请提供 file 或 path");
        }
        return ApiResult.ok(packageService.importFromDirectory(path));
    }

    /**
     * 导入智能体包（JSON）：前端「按路径导入」以 {@code {path}} 提交。
     *
     * @param body {path}
     * @return 安装结果
     */
    @Permission("admin")
    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<Map<String, Object>> importPackageJson(@RequestBody Map<String, Object> body) {
        String path = str(body.get("path"));
        if (path == null || path.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "请提供 file 或 path");
        }
        return ApiResult.ok(packageService.importFromDirectory(path));
    }

    /**
     * 从市场源安装智能体包（内置源按本地目录安装）。
     *
     * @param body {source, path|url}
     * @return 安装结果
     */
    @Permission("admin")
    @PostMapping("/install")
    public ApiResult<Map<String, Object>> installFromMarket(@RequestBody Map<String, Object> body) {
        String path = str(body.get("path"));
        if (path == null || path.isBlank()) {
            path = str(body.get("url"));
        }
        if (path == null || path.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "缺少可安装的 path/url");
        }
        return ApiResult.ok(packageService.importFromDirectory(path));
    }

    /**
     * 启用/禁用已安装的智能体包。
     *
     * @param body {name, enabled}
     * @return 更新结果
     */
    @Permission("admin")
    @PostMapping("/toggle")
    public ApiResult<Map<String, Object>> toggle(@RequestBody Map<String, Object> body) {
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "name 不能为空");
        }
        boolean enabled = !Boolean.FALSE.equals(body.get("enabled"));
        return ApiResult.ok(packageService.toggle(name, enabled));
    }

    /**
     * 卸载智能体包（删库 + 删目录）。
     *
     * @param name 包名
     * @return 卸载结果
     */
    @Permission("admin")
    @DeleteMapping("/{name}")
    public ApiResult<Map<String, Object>> uninstall(@PathVariable("name") String name) {
        return ApiResult.ok(packageService.uninstall(name));
    }

    /**
     * 市场源列表（SPI 枚举）。
     *
     * @return 来源列表
     */
    @GetMapping("/market/sources")
    public ApiResult<List<Map<String, Object>>> marketSources() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, AgentMarketSourceProvider> entry
                : ServiceProvider.of(AgentMarketSourceProvider.class).list().entrySet()) {
            AgentMarketSourceProvider provider = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>(6);
            item.put("source", entry.getKey());
            item.put("display_name", provider.displayName());
            item.put("url", provider.url());
            item.put("default_enabled", provider.defaultEnabled());
            items.add(item);
        }
        return ApiResult.ok(items);
    }

    /**
     * 按关键词检索可安装的智能体包（聚合全部市场源）。
     *
     * @param keyword 关键词
     * @param limit   条数上限
     * @return {items, sources}
     */
    @GetMapping("/market/search")
    public ApiResult<Map<String, Object>> marketSearch(@RequestParam(value = "keyword", required = false) String keyword,
                                                       @RequestParam(value = "limit", defaultValue = "50") int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Map.Entry<String, AgentMarketSourceProvider> entry
                : ServiceProvider.of(AgentMarketSourceProvider.class).list().entrySet()) {
            sources.add(entry.getKey());
            try {
                items.addAll(entry.getValue().search(keyword, limit));
            } catch (Exception e) {
                errors.add(entry.getKey() + ": " + e.getMessage());
                log.warn("[AgentMarket] 来源 {} 检索失败: {}", entry.getKey(), e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("items", items);
        result.put("sources", sources);
        result.put("errors", errors);
        return ApiResult.ok(result);
    }

    /**
     * 空安全取字符串。
     *
     * @param value 值
     * @return 字符串，空返回 空
     */
    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
