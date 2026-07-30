package com.chua.starter.panel.controller;

import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.NacosPanelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Nacos 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/nacos")
public class PanelNacosController {

    /**
     * Nacos 面板服务。
     */
    private final NacosPanelService nacosPanelService;

    /**
     * 打开 Nacos 连接。
     *
     * @param body 连接参数
     * @return 连接标识
     */
    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 8848)));
        String username = body.containsKey("username") ? String.valueOf(body.get("username")) : null;
        String password = body.containsKey("password") ? String.valueOf(body.get("password")) : null;
        String protocol = body.containsKey("protocol") ? String.valueOf(body.get("protocol")) : "http";
        try {
            return ReturnResult.ok(nacosPanelService.open(host, port, username, password, protocol));
        } catch (Exception e) {
            return ReturnResult.error("Nacos 连接失败: " + e.getMessage());
        }
    }

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     * @return 关闭结果
     */
    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@PathVariable String connectionId) {
        nacosPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    /**
     * 查询命名空间列表。
     *
     * @param connectionId 连接标识
     * @return 命名空间结果
     */
    @GetMapping("/{connectionId}/namespaces")
    public ReturnResult<Map<String, Object>> namespaces(@PathVariable String connectionId) {
        return ReturnResult.ok(nacosPanelService.namespaces(connectionId));
    }

    /**
     * 创建命名空间。
     *
     * @param connectionId 连接标识
     * @param body 创建参数
     * @return 创建结果
     */
    @PostMapping("/{connectionId}/namespace")
    public ReturnResult<Boolean> createNamespace(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String namespaceId = body.containsKey("namespaceId") ? String.valueOf(body.get("namespaceId")) : null;
        String namespaceName = body.containsKey("namespaceName") ? String.valueOf(body.get("namespaceName")) : null;
        String namespaceDesc = body.containsKey("namespaceDesc") ? String.valueOf(body.get("namespaceDesc")) : null;
        if (StringUtils.isBlank(namespaceId)) {
            return ReturnResult.error("namespaceId 不能为空");
        }
        if (StringUtils.isBlank(namespaceName)) {
            return ReturnResult.error("namespaceName 不能为空");
        }
        return ReturnResult.ok(
                nacosPanelService.createNamespace(connectionId, namespaceId, namespaceName, namespaceDesc));
    }

    /**
     * 删除命名空间。
     *
     * @param connectionId 连接标识
     * @param namespaceId 命名空间 ID
     * @return 删除结果
     */
    @DeleteMapping("/{connectionId}/namespace")
    public ReturnResult<Boolean> deleteNamespace(
            @PathVariable String connectionId,
            @RequestParam String namespaceId) {
        if (StringUtils.isBlank(namespaceId)) {
            return ReturnResult.error("namespaceId 不能为空");
        }
        return ReturnResult.ok(
                nacosPanelService.deleteNamespace(connectionId, namespaceId));
    }

    /**
     * 查询配置列表。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @return 配置列表
     */
    @GetMapping("/{connectionId}/configs")
    public ReturnResult<Map<String, Object>> listConfigs(
            @PathVariable String connectionId,
            @RequestParam(required = false) String tenant,
            @RequestParam(required = false) String dataId,
            @RequestParam(required = false) String group,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ReturnResult.ok(
                nacosPanelService.listConfigs(connectionId, tenant, dataId, group, pageNo, pageSize));
    }

    /**
     * 查询单个配置详情。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @return 配置详情
     */
    @GetMapping("/{connectionId}/config")
    public ReturnResult<Map<String, Object>> getConfig(
            @PathVariable String connectionId,
            @RequestParam(required = false) String tenant,
            @RequestParam String dataId,
            @RequestParam(defaultValue = "DEFAULT_GROUP") String group) {
        return ReturnResult.ok(
                nacosPanelService.getConfig(connectionId, tenant, dataId, group));
    }

    /**
     * 发布配置。
     *
     * @param connectionId 连接标识
     * @param body 配置内容
     * @return 发布结果
     */
    @PostMapping("/{connectionId}/config")
    public ReturnResult<Boolean> publishConfig(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String tenant = body.containsKey("tenant") ? String.valueOf(body.get("tenant")) : null;
        String dataId = body.containsKey("dataId") ? String.valueOf(body.get("dataId")) : null;
        String group = body.containsKey("group") ? String.valueOf(body.get("group")) : "DEFAULT_GROUP";
        String content = body.containsKey("content") ? String.valueOf(body.get("content")) : "";
        String type = body.containsKey("type") ? String.valueOf(body.get("type")) : null;
        if (StringUtils.isBlank(dataId)) {
            return ReturnResult.error("dataId 不能为空");
        }
        return ReturnResult.ok(
                nacosPanelService.publishConfig(connectionId, tenant, dataId, group, content, type));
    }

    /**
     * 删除配置。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @return 删除结果
     */
    @DeleteMapping("/{connectionId}/config")
    public ReturnResult<Boolean> deleteConfig(
            @PathVariable String connectionId,
            @RequestParam(required = false) String tenant,
            @RequestParam String dataId,
            @RequestParam(defaultValue = "DEFAULT_GROUP") String group) {
        if (StringUtils.isBlank(dataId)) {
            return ReturnResult.error("dataId 不能为空");
        }
        return ReturnResult.ok(
                nacosPanelService.deleteConfig(connectionId, tenant, dataId, group));
    }

    /**
     * 克隆配置。
     *
     * @param connectionId 连接标识
     * @param body 克隆参数
     * @return 克隆结果
     */
    @PostMapping("/{connectionId}/config/clone")
    public ReturnResult<Boolean> cloneConfig(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String sourceTenant = body.containsKey("sourceTenant") ? String.valueOf(body.get("sourceTenant")) : null;
        String sourceDataId = body.containsKey("sourceDataId") ? String.valueOf(body.get("sourceDataId")) : null;
        String sourceGroup = body.containsKey("sourceGroup") ? String.valueOf(body.get("sourceGroup")) : "DEFAULT_GROUP";
        String targetTenant = body.containsKey("targetTenant") ? String.valueOf(body.get("targetTenant")) : null;
        String targetDataId = body.containsKey("targetDataId") ? String.valueOf(body.get("targetDataId")) : null;
        String targetGroup = body.containsKey("targetGroup") ? String.valueOf(body.get("targetGroup")) : "DEFAULT_GROUP";
        String type = body.containsKey("type") ? String.valueOf(body.get("type")) : null;
        if (StringUtils.isBlank(sourceDataId)) {
            return ReturnResult.error("sourceDataId 不能为空");
        }
        if (StringUtils.isBlank(targetDataId)) {
            return ReturnResult.error("targetDataId 不能为空");
        }
        return ReturnResult.ok(
                nacosPanelService.cloneConfig(
                        connectionId,
                        sourceTenant,
                        sourceDataId,
                        sourceGroup,
                        targetTenant,
                        targetDataId,
                        targetGroup,
                        type));
    }

    /**
     * 查询配置历史列表。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @return 历史分页结果
     */
    @GetMapping("/{connectionId}/config/history")
    public ReturnResult<Map<String, Object>> listConfigHistory(
            @PathVariable String connectionId,
            @RequestParam(required = false) String tenant,
            @RequestParam String dataId,
            @RequestParam(defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "30") int pageSize) {
        if (StringUtils.isBlank(dataId)) {
            return ReturnResult.error("dataId 不能为空");
        }
        return ReturnResult.ok(
                nacosPanelService.listConfigHistory(connectionId, tenant, dataId, group, pageNo, pageSize));
    }

    /**
     * 查询历史详情。
     *
     * @param connectionId 连接标识
     * @param tenant 命名空间
     * @param dataId dataId
     * @param group group
     * @param historyId 历史 ID
     * @return 历史详情
     */
    @GetMapping("/{connectionId}/config/history/detail")
    public ReturnResult<Map<String, Object>> historyDetail(
            @PathVariable String connectionId,
            @RequestParam(required = false) String tenant,
            @RequestParam String dataId,
            @RequestParam(defaultValue = "DEFAULT_GROUP") String group,
            @RequestParam String historyId) {
        if (StringUtils.isBlank(dataId)) {
            return ReturnResult.error("dataId 不能为空");
        }
        if (StringUtils.isBlank(historyId)) {
            return ReturnResult.error("historyId 不能为空");
        }
        return ReturnResult.ok(
                nacosPanelService.getHistoryDetail(connectionId, tenant, dataId, group, historyId));
    }

    /**
     * 回滚配置到指定历史版本。
     *
     * @param connectionId 连接标识
     * @param body 回滚参数
     * @return 回滚结果
     */
    @PostMapping("/{connectionId}/config/rollback")
    public ReturnResult<Boolean> rollbackConfig(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String tenant = body.containsKey("tenant") ? String.valueOf(body.get("tenant")) : null;
        String dataId = body.containsKey("dataId") ? String.valueOf(body.get("dataId")) : null;
        String group = body.containsKey("group") ? String.valueOf(body.get("group")) : "DEFAULT_GROUP";
        String historyId = body.containsKey("historyId") ? String.valueOf(body.get("historyId")) : null;
        String type = body.containsKey("type") ? String.valueOf(body.get("type")) : null;
        if (StringUtils.isBlank(dataId)) {
            return ReturnResult.error("dataId 不能为空");
        }
        if (StringUtils.isBlank(historyId)) {
            return ReturnResult.error("historyId 不能为空");
        }
        return ReturnResult.ok(
                nacosPanelService.rollbackToHistory(connectionId, tenant, dataId, group, historyId, type));
    }
}
