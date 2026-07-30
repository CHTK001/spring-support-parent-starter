package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.OsConfigApplyRequest;
import com.chua.starter.server.support.model.OsConfigApplyResult;
import com.chua.starter.server.support.model.ServerOsConfigItem;
import com.chua.starter.server.support.service.ServerOsConfigService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server/hosts/{id}/os-config")
public class ServerOsConfigController {

    private final ServerOsConfigService serverOsConfigService;

    /**
     * 获取所有 OS 配置项清单。
     */
    @GetMapping
    public ReturnResult<List<ServerOsConfigItem>> listConfigs(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverOsConfigService.listConfigs(id));
    }

    /**
     * 获取指定配置项详情。
     */
    @GetMapping("/{key}")
    public ReturnResult<ServerOsConfigItem> getConfig(
            @PathVariable Integer id,
            @PathVariable String key) throws Exception {
        return ReturnResult.ok(serverOsConfigService.getConfig(id, key));
    }

    /**
     * 应用 OS 配置变更。
     */
    @PostMapping("/apply")
    public ReturnResult<OsConfigApplyResult> applyConfig(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body) throws Exception {
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || value == null) {
            return ReturnResult.error("参数 key 和 value 不能为空");
        }
        return ReturnResult.ok(serverOsConfigService.applyConfig(id, key, value));
    }
}
