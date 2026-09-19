package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.server.model.ServerHotUpdateResult;
import com.chua.starter.monitor.support.server.model.ServerNodeRequest;
import com.chua.starter.monitor.support.server.model.ServerNodeView;
import com.chua.starter.monitor.support.service.ServerManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务管理 —— 协议服务器节点管理端点。
 *
 * <p>基于 common-starter {@code Server} 接口提供 HTTP / HTTP_PROXY / TCP_PROXY / SIP
 * 四类服务器的全生命周期管理：创建、删除、更新、查询、启停，以及 服务端过滤器
 * 插件挂载（例如 HTTP 服务器挂 文件-storage 变文件服务器）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控-服务管理", description = "协议服务器节点的创建/删除/更新/查询与启停")
@RestController
@RequestMapping("/ms/monitor/server")
@RequiredArgsConstructor
public class ServerManageController {

    /**
     * 服务管理
    */
    private final ServerManageService serverManageService;

    /**
     * 查询全部节点。
     *
     * @return 节点视图列表
     */
    @Operation(summary = "查询全部服务器节点", description = "返回定义与运行状态(RUNNING/STOPPED/ERROR)")
    @GetMapping
    public ReturnResult<List<ServerNodeView>> list() {
        return serverManageService.list();
    }

    /**
     * 查询单个节点。
     *
     * @param id 节点 标识
     * @return 节点视图
     */
    @Operation(summary = "查询服务器节点详情")
    @GetMapping("/{id}")
    public ReturnResult<ServerNodeView> detail(@PathVariable Long id) {
        return serverManageService.detail(id);
    }

    /**
     * 创建节点。
     *
     * @param request 请求体
     * @return 节点视图
     */
    @Operation(summary = "创建服务器节点", description = "enabled=1 时创建后立即拉起；代理类型需 config.backendHost/backendPort")
    @PostMapping
    public ReturnResult<ServerNodeView> create(@RequestBody ServerNodeRequest request) {
        return serverManageService.create(request);
    }

    /**
     * 更新节点。
     *
     * @param id      节点 标识
     * @param request 请求体
     * @return 节点视图
     */
    @Operation(summary = "更新服务器节点", description = "全量替换；运行中的节点先停止，enabled=1 时按新配置重新拉起")
    @PutMapping("/{id}")
    public ReturnResult<ServerNodeView> update(@PathVariable Long id, @RequestBody ServerNodeRequest request) {
        return serverManageService.update(id, request);
    }

    /**
     * 热更新节点（配置 + 过滤器链）。
     *
     * @param id      节点 标识
     * @param request 请求体
     * @return 热更新结果
     */
    @Operation(summary = "热更新服务器节点",
            description = "监听参数未变则仅重建过滤器链(不重启)；监听参数变更则按新配置重启；未运行则仅保存定义")
    @PostMapping("/{id}/hot-update")
    public ReturnResult<ServerHotUpdateResult> hotUpdate(@PathVariable Long id,
                                                         @RequestBody ServerNodeRequest request) {
        return serverManageService.hotUpdate(id, request);
    }

    /**
     * 热切换单个过滤器启用状态。
     *
     * @param id      节点 标识
     * @param index   过滤器下标
     * @param enabled 是否启用
     * @return 节点视图
     */
    @Operation(summary = "热切换过滤器启用状态", description = "不重启服务器，直接启用/停用托管链路上的第 index 个过滤器")
    @PostMapping("/{id}/filters/{index}/toggle")
    public ReturnResult<ServerNodeView> hotToggleFilter(@PathVariable Long id,
                                                        @PathVariable Integer index,
                                                        @RequestParam(defaultValue = "true") Boolean enabled) {
        return serverManageService.hotToggleFilter(id, index, enabled);
    }

    /**
     * 热更新单个过滤器参数。
     *
     * @param id     节点 标识
     * @param index  过滤器下标
     * @param params 新的参数键值
     * @return 节点视图
     */
    @Operation(summary = "热更新过滤器参数", description = "不重启服务器、不重建实例，直接对第 index 个过滤器调用 updateConfig")
    @PostMapping("/{id}/filters/{index}/params")
    public ReturnResult<ServerNodeView> hotUpdateFilterParams(@PathVariable Long id,
                                                              @PathVariable Integer index,
                                                              @RequestBody Map<String, Object> params) {
        return serverManageService.hotUpdateFilterParams(id, index, params);
    }

    /**
     * 删除节点。
     *
     * @param id 节点 标识
     * @return 空数据
     */
    @Operation(summary = "删除服务器节点", description = "先停止运行实例再删除定义")
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id) {
        return serverManageService.delete(id);
    }

    /**
     * 启动节点。
     *
     * @param id 节点 标识
     * @return 节点视图
     */
    @Operation(summary = "启动服务器节点")
    @PostMapping("/{id}/start")
    public ReturnResult<ServerNodeView> start(@PathVariable Long id) {
        return serverManageService.start(id);
    }

    /**
     * 停止节点。
     *
     * @param id 节点 标识
     * @return 节点视图
     */
    @Operation(summary = "停止服务器节点", description = "仅停止运行实例，定义保留")
    @PostMapping("/{id}/stop")
    public ReturnResult<ServerNodeView> stop(@PathVariable Long id) {
        return serverManageService.stop(id);
    }

    /**
     * 支持的协议类型。
     *
     * @return 协议元数据
     */
    @Operation(summary = "查询支持的协议类型", description = "http / http-proxy / tcp-proxy / sip 元数据")
    @GetMapping("/protocols")
    public ReturnResult<List<Map<String, Object>>> protocols() {
        return serverManageService.protocols();
    }

    /**
     * 支持的 服务端过滤器 插件类型。
     *
     * @return 插件元数据
     */
    @Operation(summary = "查询支持的 ServerFilter 插件类型", description = "file-storage 预设与 FQCN 通用插件说明")
    @GetMapping("/filters")
    public ReturnResult<List<Map<String, Object>>> filterTypes() {
        return serverManageService.filterTypes();
    }

    /**
     * 反向代理类节点。
     *
     * @return 代理节点视图列表
     */
    @Operation(summary = "查询反向代理节点", description = "HTTP_PROXY / TCP_PROXY / socks 等代理类服务器节点")
    @GetMapping("/proxies")
    public ReturnResult<List<ServerNodeView>> proxies() {
        return serverManageService.proxies();
    }
}
