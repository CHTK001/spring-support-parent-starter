package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.ZookeeperPanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ZooKeeper 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/zookeeper")
@Tag(name = "Panel Zookeeper", description = "Panel ZooKeeper 管理接口")
public class PanelZookeeperController {

    private final ZookeeperPanelService zookeeperPanelService;

    /**
     * 打开 ZooKeeper 连接。
     *
     * @param body 连接参数
     * @return 连接标识
     */
    @Operation(summary = "打开连接")
    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 2181)));
        int sessionTimeout = Integer.parseInt(String.valueOf(body.getOrDefault("sessionTimeout", 30000)));
        try {
            return ReturnResult.ok(zookeeperPanelService.open(host, port, sessionTimeout));
        } catch (Exception e) {
            return ReturnResult.error("ZooKeeper 连接失败: " + e.getMessage());
        }
    }

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     * @return 关闭结果
     */
    @Operation(summary = "关闭连接")
    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@Parameter(description = "连接标识") @PathVariable String connectionId) {
        zookeeperPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    /**
     * 列出子节点。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @return 子节点名称列表
     */
    @Operation(summary = "列出子节点")
    @GetMapping("/{connectionId}/ls")
    public ReturnResult<List<String>> ls(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "节点路径") @RequestParam(defaultValue = "/") String path) {
        try {
            return ReturnResult.ok(zookeeperPanelService.ls(connectionId, path));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 读取节点数据。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @return 节点数据
     */
    @Operation(summary = "读取节点")
    @GetMapping("/{connectionId}/get")
    public ReturnResult<String> get(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "节点路径") @RequestParam String path) {
        try {
            return ReturnResult.ok(zookeeperPanelService.get(connectionId, path));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 写入节点数据。
     *
     * @param connectionId 连接标识
     * @param body 节点写入参数
     * @return 写入结果
     */
    @Operation(summary = "写入节点")
    @PostMapping("/{connectionId}/set")
    public ReturnResult<Void> set(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @RequestBody Map<String, String> body) {
        try {
            zookeeperPanelService.set(connectionId, body.get("path"), body.get("data"));
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 删除节点。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @param recursive 是否递归删除
     * @return 删除结果
     */
    @Operation(summary = "删除节点")
    @DeleteMapping("/{connectionId}/delete")
    public ReturnResult<Void> delete(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "节点路径") @RequestParam String path,
            @Parameter(description = "是否递归删除") @RequestParam(defaultValue = "false") boolean recursive) {
        try {
            zookeeperPanelService.delete(connectionId, path, recursive);
            return ReturnResult.ok(null);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 查询节点元信息。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @return 元信息
     */
    @Operation(summary = "查询节点元信息")
    @GetMapping("/{connectionId}/stat")
    public ReturnResult<Map<String, Object>> stat(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "节点路径") @RequestParam String path) {
        try {
            return ReturnResult.ok(zookeeperPanelService.stat(connectionId, path));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 判断节点是否存在。
     *
     * @param connectionId 连接标识
     * @param path 节点路径
     * @return 是否存在
     */
    @Operation(summary = "判断节点是否存在")
    @GetMapping("/{connectionId}/exists")
    public ReturnResult<Boolean> exists(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @Parameter(description = "节点路径") @RequestParam String path) {
        try {
            return ReturnResult.ok(zookeeperPanelService.exists(connectionId, path));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 创建节点。
     *
     * @param connectionId 连接标识
     * @param body 创建参数
     * @return 创建后的节点路径
     */
    @Operation(summary = "创建节点")
    @PostMapping("/{connectionId}/create")
    public ReturnResult<String> create(
            @Parameter(description = "连接标识") @PathVariable String connectionId,
            @RequestBody Map<String, String> body) {
        try {
            String created = zookeeperPanelService.create(
                    connectionId,
                    body.get("path"),
                    body.get("data"),
                    body.getOrDefault("mode", "PERSISTENT"));
            return ReturnResult.ok(created);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }
}
