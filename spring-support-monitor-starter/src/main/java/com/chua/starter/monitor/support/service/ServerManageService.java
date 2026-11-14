package com.chua.starter.monitor.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.server.model.ServerFilterTypeMetaVO;
import com.chua.starter.monitor.support.server.model.ServerHotUpdateResult;
import com.chua.starter.monitor.support.server.model.ServerNodeRequest;
import com.chua.starter.monitor.support.server.model.ServerNodeSummary;
import com.chua.starter.monitor.support.server.model.ServerNodeView;
import com.chua.starter.monitor.support.server.model.ServerProtocolMetaVO;

import java.util.List;
import java.util.Map;

/**
 * 服务管理 —— 协议服务器节点管理服务。
 *
 * <p>基于 common-starter {@code Server} 接口实现 HTTP / HTTP_PROXY / TCP_PROXY / SIP
 * 四类服务器的创建、删除、更新、查询与启停，支持挂载 服务端过滤器 插件。</p>
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
     * 分页查询节点（含运行状态）。
     *
     * <p>过滤与排序口径见 {@code ServerNodePager}：关键字命中名称 / 协议 / 监听
     * / 后端 / 报错，协议与状态精确匹配，状态排序把异常排最前。</p>
     *
     * @param keyword  关键字，空则不过滤
     * @param protocol 协议编码，空则不过滤
     * @param status   运行状态（RUNNING / STOPPED / ERROR），空则不过滤
     * @param sort     排序口径：name / port / status
     * @param page     页码，从 1 开始
     * @param size     每页条数，服务端封顶 500
     * @return 分页结果
     */
    ReturnResult<Page<ServerNodeView>> page(String keyword, String protocol, String status,
                                            String sort, Integer page, Integer size);

    /**
     * 节点统计摘要与名称表。
     *
     * <p>分页后前端只拿到当前页，左栏计数与新建表单的重名校验需要全量规模，
     * 因此单独给出这份轻量摘要，避免为了两个数字再拉一遍全量视图。</p>
     *
     * @return 统计摘要
     */
    ReturnResult<ServerNodeSummary> summary();

    /**
     * 查询单个节点（含运行状态）。
     *
     * @param id 节点 标识
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> detail(Long id);

    /**
     * 创建节点（启用=1 时立即拉起）。
     *
     * @param request 请求体
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> create(ServerNodeRequest request);

    /**
     * 更新节点（全量替换；若运行中先停止，启用=1 时按新配置重新拉起）。
     *
     * @param id      节点 标识
     * @param request 请求体
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> update(Long id, ServerNodeRequest request);

    /**
     * 删除节点（先停止运行实例）。
     *
     * @param id 节点 标识
     * @return 空数据
     */
    ReturnResult<Void> delete(Long id);

    /**
     * 启动节点。
     *
     * @param id 节点 标识
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> start(Long id);

    /**
     * 停止节点（定义保留）。
     *
     * @param id 节点 标识
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> stop(Long id);

    /**
     * 热更新节点（配置与过滤器链）。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>监听地址/端口/引擎未变 → 不重启，仅按新 filters 重建过滤器链</li>
     *   <li>监听地址/端口/引擎变更 → 先停后起，按新配置重启</li>
     *   <li>节点未运行且 {@code enabled=1} 时，若监听参数变更或节点处于错误态 →
     *       按新配置拉起（与 create/update 对齐，报错节点改正后可自愈）</li>
     *   <li>节点未运行且无需拉起 → 仅持久化定义，等待下次启动生效</li>
     * </ul>
     *
     * @param id      节点 标识
     * @param request 请求体
     * @return 热更新结果
     */
    ReturnResult<ServerHotUpdateResult> hotUpdate(Long id, ServerNodeRequest request);

    /**
     * 热切换单个过滤器的启用状态（不重启服务器）。
     *
     * @param id      节点 标识
     * @param index   过滤器下标（与 filters 数组一致）
     * @param enabled 是否启用
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> hotToggleFilter(Long id, Integer index, Boolean enabled);

    /**
     * 热更新单个过滤器的参数（不重启服务器、不重建实例）。
     *
     * @param id     节点 标识
     * @param index  过滤器下标（与 filters 数组一致）
     * @param params 新的参数键值（与现有参数合并）
     * @return 节点视图
     */
    ReturnResult<ServerNodeView> hotUpdateFilterParams(Long id, Integer index, Map<String, Object> params);

    /**
     * 支持的协议类型元数据。
     *
     * @return 协议描述列表
     */
    ReturnResult<List<ServerProtocolMetaVO>> protocols();

    /**
     * 支持的 服务端过滤器 插件类型元数据。
     *
     * @return 插件描述列表
     */
    ReturnResult<List<ServerFilterTypeMetaVO>> filterTypes();

    /**
     * 反向代理类节点视图（HTTP_PROXY / TCP_PROXY / socks 等）。
     *
     * <p>代理服务需要独立的运维视图：它不提供页面，而是转发流量，
     * 因此单独列出便于排查上游后端与负载情况。</p>
     *
     * @return 代理节点视图列表
     */
    ReturnResult<List<ServerNodeView>> proxies();
}
