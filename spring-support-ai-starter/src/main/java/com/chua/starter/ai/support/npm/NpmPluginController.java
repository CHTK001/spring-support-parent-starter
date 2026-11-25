package com.chua.starter.ai.support.npm;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.common.support.annotations.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * npm 插件管理 Controller。
 *
 * <p>提供 npm 安装型插件的安装、卸载、清单查询与运行环境状态查询。
 * 安装/卸载真实执行 npm 命令，台账持久化到 {@code sys_ai_global_setting}。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@RestController
@RequestMapping("/v2/plugins/npm")
@RequiredArgsConstructor
public class NpmPluginController {

    private final NpmPluginManagerService managerService;
    private final NpmPluginHostService hostService;

    /**
     * 已安装插件清单。
     *
     * @return 插件条目列表（name/version/description/enabled/installedAt）
     */
    @Permission("admin")
    @GetMapping("/list")
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(managerService.listInstalled());
    }

    /**
     * 安装 npm 插件。
     *
     * @param body {name, version?}；version 空则装最新版
     * @return success/version/output/message
     */
    @Permission("admin")
    @PostMapping("/install")
    public ApiResult<Map<String, Object>> install(@RequestBody Map<String, Object> body) {
        String name = body == null ? null : Objects.toString(body.get("name"), null);
        String version = body == null ? null : Objects.toString(body.get("version"), null);
        if (version != null && version.isBlank()) {
            version = null;
        }
        log.info("安装 npm 插件: name={}, version={}", name, version);
        return ApiResult.ok(managerService.install(name, version));
    }

    /**
     * 卸载 npm 插件。
     *
     * @param name npm 包名（scoped 包用 URL 编码，如 %40scope%2Fname）
     * @return success/output/message
     */
    @Permission("admin")
    @DeleteMapping
    public ApiResult<Map<String, Object>> uninstall(@RequestParam("name") String name) {
        log.info("卸载 npm 插件: name={}", name);
        return ApiResult.ok(managerService.uninstall(name));
    }

    /**
     * npm 插件运行环境状态。
     *
     * @return enabled/running/responsive/node/installDir 等
     */
    @Permission("admin")
    @GetMapping("/status")
    public ApiResult<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>(hostService.status());
        status.put("installed", managerService.listInstalled().size());
        return ApiResult.ok(status);
    }
}
