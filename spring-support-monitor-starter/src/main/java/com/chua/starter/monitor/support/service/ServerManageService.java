package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.server.model.ServerNodeRequest;
import com.chua.starter.monitor.support.server.model.ServerNodeView;

import java.util.List;
import java.util.Map;

/**
 * 服务管理 —— 协议服务器节点管理服务。
 *
 * <p>基于 common-starter {@code Server} 接口实现 HTTP / HTTP_PROXY / TCP_PROXY / SIP
 * 四类服务器的创建、删除、更新、查询与启停，支持挂载 ServerFilter 插件。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ServerManageService {

    /**
     * 查询全部节点（含运行状态）。
     *
     * @return 节点视图列表
     */
    ReturnResult<List<ServerNodeView>> list();

    /**
     * 查询单个节点（含运行状态）。
     *
     * @param id 节点 ID
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> detail(Long id);

    /**
     * 创建节点（enabled=1 时立即拉起）。
     *
     * @param request 请求体
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> create(ServerNodeRequest request);

    /**
     * 更新节点（全量替换；若运行中先停止，enabled=1 时按新配置重新拉起）。
     *
     * @param id      节点 ID
     * @param request 请求体
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> update(Long id, ServerNodeRequest request);

    /**
     * 删除节点（先停止运行实例）。
     *
     * @param id 节点 ID
     * @return 空数据
     */
    ReturnResult<Void> delete(Long id);

    /**
     * 启动节点。
     *
     * @param id 节点 ID
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> start(Long id);

    /**
     * 停止节点（定义保留）。
     *
     * @param id 节点 ID
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> stop(Long id);

    /**
     * 支持的协议类型元数据。
     *
     * @return 协议描述列表
     */
    ReturnResult<List<Map<String, Object>>> protocols();

    /**
     * 支持的 ServerFilter 插件类型元数据。
     *
     * @return 插件描述列表
     */
    ReturnResult<List<Map<String, Object>>> filterTypes();
}
