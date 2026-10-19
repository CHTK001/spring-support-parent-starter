package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnCode;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.network.server.Server;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.DescribeOptional;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.starter.monitor.support.entity.MsServerNode;
import com.chua.starter.monitor.support.enums.ServerNodeProtocol;
import com.chua.starter.monitor.support.mapper.MsServerNodeMapper;
import com.chua.starter.monitor.support.server.ServerNodeRegistry;
import com.chua.starter.monitor.support.server.ServerNodeRegistry.RuntimeServer;
import com.chua.starter.monitor.support.server.model.ServerFilterDescriptor;
import com.chua.starter.monitor.support.server.model.ServerFilterTypeMetaVO;
import com.chua.starter.monitor.support.server.model.ServerHotUpdateResult;
import com.chua.starter.monitor.support.server.model.ServerNodeRequest;
import com.chua.starter.monitor.support.server.model.ServerNodeView;
import com.chua.starter.monitor.support.server.model.ServerPluginParamVO;
import com.chua.starter.monitor.support.server.model.ServerProtocolMetaVO;
import com.chua.starter.monitor.support.service.ServerManageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务管理 —— 协议服务器节点管理服务实现。
 *
 * <p>定义持久化到 {@code ms_server_node}（MyBatis-Plus），运行期实例托管在
 * {@link ServerNodeRegistry}。更新语义：先停运行实例 → 全量替换定义 →
 * 启用=1 时按新配置重新拉起。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerManageServiceImpl implements ServerManageService {

    /**
     * 节点 映射器
    */
    private final MsServerNodeMapper nodeMapper;
    /**
     * 运行时注册表
    */
    private final ServerNodeRegistry nodeRegistry;
    /**
     * JSON
    */
    private final ObjectMapper objectMapper;

    @Override
    public ReturnResult<List<ServerNodeView>> list() {
        try {
            List<MsServerNode> nodes = nodeMapper.selectList(
                    new LambdaQueryWrapper<MsServerNode>().orderByAsc(MsServerNode::getId));
            List<ServerNodeView> views = new ArrayList<>();
            for (MsServerNode node : nodes) {
                views.add(toView(node));
            }
            return ReturnResult.ok(views);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<List<ServerNodeView>> proxies() {
        try {
            List<MsServerNode> nodes = nodeMapper.selectList(
                    new LambdaQueryWrapper<MsServerNode>().orderByAsc(MsServerNode::getId));
            List<ServerNodeView> views = new ArrayList<>();
            for (MsServerNode node : nodes) {
                if (isProxyProtocol(node.getProtocol())) {
                    views.add(toView(node));
                }
            }
            return ReturnResult.ok(views);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 判断协议是否属于代理类（反向代理 / TCP 代理 / socks 等）。
     *
     * @param protocol 协议编码
     * @return 是代理类返回 真
     */
    private boolean isProxyProtocol(String protocol) {
        if (protocol == null) {
            return false;
        }
        String p = protocol.toLowerCase();
        return p.contains("proxy") || p.contains("socks") || p.contains("tunnel");
    }

    @Override
    public ReturnResult<ServerNodeView> detail(Long id) {
        try {
            MsServerNode node = nodeMapper.selectById(id);
            if (node == null) {
                return ReturnResult.error("节点不存在: " + id);
            }
            return ReturnResult.ok(toView(node));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<ServerNodeView> create(ServerNodeRequest request) {
        try {
            ServerNodeProtocol protocol = requireProtocol(request);
            /**
             * requireunique名称。
             * @param request.getName( 请求.获取名称(
             */
            requireUniqueName(request.getName(), null);
            MsServerNode node = new MsServerNode();
            /**
             * apply请求。
             * @param node 节点
             * @param request 请求
             * @param protocol 协议
             */
            applyRequest(node, request, protocol);
            nodeMapper.insert(node);
            return startIfEnabled(node);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<ServerNodeView> update(Long id, ServerNodeRequest request) {
        try {
            MsServerNode exists = nodeMapper.selectById(id);
            if (exists == null) {
                return ReturnResult.error("节点不存在: " + id);
            }
            ServerNodeProtocol protocol = requireProtocol(request);
            /**
             * requireunique名称。
             * @param request.getName( 请求.获取名称(
             */
            requireUniqueName(request.getName(), id);
            // 先停运行实例
            nodeRegistry.stop(id);
            MsServerNode node = new MsServerNode();
            node.setId(id);
            /**
             * apply请求。
             * @param node 节点
             * @param request 请求
             * @param protocol 协议
             */
            applyRequest(node, request, protocol);
            node.setCreateName(exists.getCreateName());
            node.setCreateBy(exists.getCreateBy());
            node.setCreateTime(exists.getCreateTime());
            nodeMapper.updateById(node);
            return startIfEnabled(node);
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<Void> delete(Long id) {
        try {
            MsServerNode node = nodeMapper.selectById(id);
            if (node == null) {
                return ReturnResult.error("节点不存在: " + id);
            }
            nodeRegistry.remove(id);
            nodeMapper.deleteById(id);
            log.info("[ServerManage] 节点已删除: {} [{}]", node.getName(), node.getProtocol());
            return ReturnResult.success();
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<ServerNodeView> start(Long id) {
        try {
            MsServerNode node = nodeMapper.selectById(id);
            if (node == null) {
                return ReturnResult.error("节点不存在: " + id);
            }
 // 幂等: 已在运行则直接返回当前视图, 避免重复 bind 导致 bind异常
            RuntimeServer existing = nodeRegistry.get(id);
            if (existing != null && existing.isRunning()) {
                log.info("[ServerManage] 节点已在运行, 幂等跳过: id={} name={}", id, node.getName());
                return ReturnResult.ok(toView(node));
            }
            nodeRegistry.start(node);
            return ReturnResult.ok(toView(node));
        } catch (Exception e) {
            log.error("[ServerManage] 节点启动失败: id={} - {}", id, e.getMessage(), e);
            return ReturnResult.error("节点启动失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<ServerNodeView> stop(Long id) {
        try {
            MsServerNode node = nodeMapper.selectById(id);
            if (node == null) {
                return ReturnResult.error("节点不存在: " + id);
            }
            nodeRegistry.stop(id);
            return ReturnResult.ok(toView(node));
        } catch (Exception e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    @Override
    public ReturnResult<ServerHotUpdateResult> hotUpdate(Long id, ServerNodeRequest request) {
        try {
            MsServerNode exists = nodeMapper.selectById(id);
            if (exists == null) {
                return ReturnResult.error("节点不存在: " + id);
            }
            ServerNodeProtocol protocol = requireProtocol(request);
            requireUniqueName(request.getName(), id);

            boolean listeningChanged = listeningChanged(exists, request, protocol);
            RuntimeServer current = nodeRegistry.get(id);
            boolean wasRunning = current != null && current.isRunning();

            MsServerNode node = new MsServerNode();
            node.setId(id);
            applyRequest(node, request, protocol);
            node.setCreateName(exists.getCreateName());
            node.setCreateBy(exists.getCreateBy());
            node.setCreateTime(exists.getCreateTime());

            ServerHotUpdateResult result = new ServerHotUpdateResult();
            if (!wasRunning) {
                // 未运行：先把定义落库
                nodeMapper.updateById(node);
                // 修复：此前未运行的节点一律「只保存、不启动」，于是对报错(ERROR)节点
                // 点「保存并热更新」等于什么都没做 —— 用户把端口从被占改成空闲后节点依旧
                // ERROR，必须再点一次「启动」，而验收（service-lifecycle-ui.spec E1）明确
                // 要求改端口后自愈为 RUNNING。这里与 create/update 的 startIfEnabled 对齐：
                // 节点处于启用态且（监听参数变更 或 上次启动失败）时按新配置拉起；
                // 纯备注/过滤器改动且节点本就未运行时仍只保存，避免把用户主动停止的节点
                // （enabled 未变、仍为 1）意外唤醒。
                boolean errored = current != null && current.getError() != null;
                if (isEnabledNode(node) && (listeningChanged || errored)) {
                    return bringUpAfterHotUpdate(id, node, result, listeningChanged);
                }
                result.setHotApplied(false);
                result.setRestarted(false);
                result.addAppliedItem("定义已保存(节点未运行, 下次启动生效)");
                result.setMessage("节点未运行, 配置已保存, 启动后生效");
                result.setNode(toView(node));
                return ReturnResult.ok(result);
            }

            // 运行中：先落到运行态、成功后再落库。运行态构造/装配失败（如过滤器参数非法）
            // 时抛错，此时尚未持久化，不会留下「库里存着非法值、运行态却是旧值」的分叉；
            // 那种分叉要等下次启动才暴露，而启动路径同样会因非法值报错、把节点直接打成 ERROR。
            // start/hotUpdateFilters 均使用内存中的 node，不回读数据库，故后移落库是安全的。
            if (listeningChanged) {
                // 监听参数变更：先停后起
                nodeRegistry.stop(id);
                nodeRegistry.start(node);
                nodeMapper.updateById(node);
                result.setHotApplied(false);
                result.setRestarted(true);
                result.addAppliedItem("监听参数变更, 服务器已重启");
                result.addAppliedItem("过滤器链已按新配置重建");
                result.setMessage("监听地址/端口/引擎已变更, 服务器已按新配置重启");
                result.setNode(toView(node));
                return ReturnResult.ok(result);
            }

            // 监听参数未变：仅热更新过滤器链
            nodeRegistry.hotUpdateFilters(node);
            nodeMapper.updateById(node);
            result.setHotApplied(true);
            result.setRestarted(false);
            result.addAppliedItem("过滤器链已热更新");
            result.setMessage("监听参数未变, 仅热更新过滤器链");
            result.setNode(toView(node));
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("[ServerManage] 热更新失败: id={} - {}", id, e.getMessage(), e);
            // 同类问题：过滤器参数此时已 updateById 落库，热应用失败只影响运行态。
            // 回传节点视图（含 id），调用方才能自行决定重试热更新还是回滚配置。
            // hotUpdate 的载荷是 ServerHotUpdateResult（不是 ServerNodeView），要单独包一层，
            // 并如实标记 hotApplied=false：调用方不能从「有载荷」推断成「已热生效」
            return hotUpdateFailure(id, e.getMessage(), view -> {
                ServerHotUpdateResult payload = new ServerHotUpdateResult();
                payload.setHotApplied(false);
                payload.setRestarted(false);
                payload.setMessage("配置已保存但热更新未生效");
                payload.setNode(view);
                return payload;
            });
        }
    }

    @Override
    public ReturnResult<ServerNodeView> hotToggleFilter(Long id, Integer index, Boolean enabled) {
        try {
            MsServerNode node = nodeMapper.selectById(id);
            if (node == null) {
                return ReturnResult.error("节点不存在: " + id);
            }
            if (index == null || index < 0) {
                return ReturnResult.error("过滤器下标非法: " + index);
            }
            List<ServerFilterDescriptor> descriptors = nodeRegistry.parseFilters(node);
            if (index >= descriptors.size()) {
                return ReturnResult.error("过滤器下标越界: " + index);
            }
            boolean target = enabled == null || enabled;
            ServerFilterDescriptor old = descriptors.get(index);
            descriptors.set(index, new ServerFilterDescriptor(
                    old.name(), old.type(), old.order(), target, old.params()));
            node.setFilters(objectMapper.writeValueAsString(descriptors));
            nodeMapper.updateById(node);
            RuntimeServer runtime = nodeRegistry.get(id);
            if (runtime != null && runtime.isRunning()) {
                nodeRegistry.hotToggleFilter(node, index, target);
            }
            return ReturnResult.ok(toView(node));
        } catch (Exception e) {
            log.error("[ServerManage] 过滤器热启停失败: id={} index={} - {}", id, index, e.getMessage(), e);
            // 同上：enabled 已落库，热启停失败只影响运行态，回传节点视图供调用方处置
            return hotUpdateFailure(id, "过滤器热启停失败: " + e.getMessage(), java.util.function.Function.identity());
        }
    }

    @Override
    public ReturnResult<ServerNodeView> hotUpdateFilterParams(Long id, Integer index, Map<String, Object> params) {
        try {
            MsServerNode node = nodeMapper.selectById(id);
            if (node == null) {
                return ReturnResult.error("节点不存在: " + id);
            }
            if (index == null || index < 0) {
                return ReturnResult.error("过滤器下标非法: " + index);
            }
            List<ServerFilterDescriptor> descriptors = nodeRegistry.parseFilters(node);
            if (index >= descriptors.size()) {
                return ReturnResult.error("过滤器下标越界: " + index);
            }
            ServerFilterDescriptor descriptor = descriptors.get(index);
            Map<String, String> merged = new LinkedHashMap<>();
            if (descriptor.params() != null) {
                merged.putAll(descriptor.params());
            }
            if (params != null) {
                params.forEach((k, v) -> merged.put(k, v == null ? null : String.valueOf(v)));
            }
            descriptors.set(index, new ServerFilterDescriptor(
                    descriptor.name(), descriptor.type(), descriptor.order(), descriptor.enabled(), merged));
            node.setFilters(objectMapper.writeValueAsString(descriptors));
            nodeMapper.updateById(node);
            RuntimeServer runtime = nodeRegistry.get(id);
            if (runtime != null && runtime.isRunning()) {
                nodeRegistry.hotUpdateFilterParams(node, index, new LinkedHashMap<>(params == null ? Map.of() : params));
            }
            return ReturnResult.ok(toView(node));
        } catch (Exception e) {
            log.error("[ServerManage] 过滤器参数热更新失败: id={} index={} - {}", id, index, e.getMessage(), e);
            return ReturnResult.error("过滤器参数热更新失败: " + e.getMessage());
        }
    }

    /**
     * 判断本次更新是否触发了监听参数变更（需要重启服务器）。
     *
     * @param exists   原实体
     * @param request  新请求
     * @param protocol 协议枚举
     * @return 变更返回 真
     */
    private boolean listeningChanged(MsServerNode exists, ServerNodeRequest request, ServerNodeProtocol protocol) {
        if (!equalsText(exists.getProtocol(), request.getProtocol())) {
            return true;
        }
        String newEngine = request.getEngine() == null || request.getEngine().isBlank()
                ? (protocol == null ? request.getProtocol().trim().toLowerCase() : protocol.getDefaultEngine())
                : request.getEngine().trim().toLowerCase();
        if (!equalsText(exists.getEngine(), newEngine)) {
            return true;
        }
        String newHost = request.getHost() == null || request.getHost().isBlank()
                ? "0.0.0.0" : request.getHost().trim();
        if (!equalsText(exists.getHost(), newHost)) {
            return true;
        }
        Integer newPort = request.getPort() != null ? request.getPort()
                : (protocol == null ? null : protocol.getDefaultPort());
        return exists.getPort() == null ? newPort != null : !exists.getPort().equals(newPort);
    }

    /**
     * 忽略大小写的文本比较（双方均视为小写比较，避免仅大小写差异触发重启）。
     *
     * @param left  左值
     * @param right 右值
     * @return 相等返回 真
     */
    private boolean equalsText(String left, String right) {
        String l = left == null ? null : left.trim().toLowerCase();
        String r = right == null ? null : right.trim().toLowerCase();
        return l == null ? r == null : l.equals(r);
    }

    /**
     * HTTP 引擎变体（SPI 注册但不应出现在协议下拉中）。
     *
     * <p>它们是「引擎名」不是「协议名」：{@code jdk/nio/aio} 只是
     * {@code jdk-http/nio-http/aio-http} 的短名，故从协议列表剔除，
     * 改由 {@link #httpEngines()} 作为 HTTP 协议的可选引擎下发。</p>
     */
    private static final Set<String> HTTP_ENGINE_ALIASES =
            Set.of("jdk", "nio", "aio", "jdk-http", "nio-http", "aio-http");

    /**
     * HTTP 协议的可选引擎（来源：Server SPI 实际注册名）。
     *
     * <p>原先引擎候选在前端硬编码为 jdk/nio/aio，新增 HTTP 实现时必须同步改前端；
     * 现在改为问 SPI 注册表 —— 谁注册了 {@code *-http} 家族名，谁就是候选引擎。</p>
     *
     * @return 引擎名清单（去重、已排序）；无匹配时返回仅默认引擎的兜底列表
     */
    private List<String> httpEngines() {
        String fallback = ServerNodeProtocol.HTTP.getDefaultEngine();
        List<String> found = new ArrayList<>();
        ServiceProvider.of(Server.class).forDefinitionEach(definition -> {
            String name = definition.getName();
            if (name == null || name.isBlank()) {
                return;
            }
            String alias = name.toLowerCase();
            // 引擎家族：短名（jdk/nio/aio）与显式 -http 变体（jdk-http/nio-http/aio-http），
            // 外挂的 iocp 别名也属 AioHttpServer，一并纳入。
            boolean isHttpFamily = HTTP_ENGINE_ALIASES.contains(alias) || "iocp".equals(alias);
            if (isHttpFamily && !found.contains(alias)) {
                found.add(alias);
            }
        });
        if (found.isEmpty()) {
            return List.of(fallback);
        }
        // 默认引擎排首位，其余按字典序，保证下拉顺序稳定
        found.remove(fallback);
        found.sort(String::compareTo);
        found.add(0, fallback);
        return found;
    }

    @Override
    public ReturnResult<List<ServerProtocolMetaVO>> protocols() {
        List<ServerProtocolMetaVO> result = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        List<String> engines = httpEngines();
        // 1. 内置四类基础协议（含默认端口/引擎等富元数据）
        for (ServerNodeProtocol protocol : ServerNodeProtocol.values()) {
            result.add(new ServerProtocolMetaVO(protocol.getCode(), protocol.getAlias(), protocol.getDesc(),
                    protocol.getDefaultPort(), protocol.getDefaultEngine(), protocol.isRequiresBackend(), false, null,
                    // HTTP 的引擎候选来自 SPI 注册表；其余协议引擎唯一，无需列表
                    protocol == ServerNodeProtocol.HTTP ? engines : List.of(protocol.getDefaultEngine())));
            covered.add(protocol.getAlias().toLowerCase());
        }
        // 2. SPI 动态发现的其它 Server 实现（去重、剔除 HTTP 引擎变体）
        ServiceProvider.of(Server.class).forDefinitionEach(definition -> {
            String name = definition.getName();
            if (name == null || name.isBlank()) {
                return;
            }
            String alias = name.toLowerCase();
            if (covered.contains(alias) || HTTP_ENGINE_ALIASES.contains(alias)) {
                return;
            }
            result.add(new ServerProtocolMetaVO(name, alias, describeOf(definition, alias), null, alias,
                    alias.contains("proxy") && !alias.contains("socks"), true, paramsOf(definition),
                    // SPI 协议自身即单一实现，引擎就是它自己的注册名
                    List.of(alias)));
            covered.add(alias);
        });
        return ReturnResult.ok(result);
    }

    /**
     * 过滤器类型 → 深度查看页路由（前端校验路由存在后才跳转）。
     *
     * <p>仅适用于「深度页面由当前宿主应用托管」的插件。file-storage 不在此列：
     * 其 SYSTEM 布局由 FileStorageServerFilter 自身在 {@code /{bucket}} 上直接托管 Vue 独立构建，
     * 与宿主应用无关，因此改走 {@link #FILTER_NODE_DETAIL_TYPES} 的节点地址直开。</p>
     */
    private static final Map<String, String> FILTER_DETAIL_PAGES = Map.of(
            "service-discovery", "/discovery");
    /**
     * 过滤器类型 → 深度查看目标为「节点自身访问地址」。
     *
     * <p>这些插件的深度页面由插件自己的 HTTP 服务提供（FileStorageServerFilter 的 SYSTEM 布局
     * 在 {@code {serverUrl}{bucket}} 上返回 classpath {@code static/index.html}），
     * 因此前端应新窗口直开节点地址，而不是跳宿主路由——宿主未挂载该页时同样可用，不会死链。</p>
     */
    private static final Set<String> FILTER_NODE_DETAIL_TYPES = Set.of("file-storage");
    /**
     * 过滤器类型 → 深度查看入口文案
    */
    private static final Map<String, String> FILTER_DETAIL_LABELS = Map.of(
            "file-storage", "打开文件管理器",
            "service-discovery", "查看服务实例");
    /**
     * 过滤器类型 → 前端分组（左栏归类）
    */
    private static final Map<String, String> FILTER_GROUPS = Map.of(
            "file-storage", "storage",
            "service-discovery", "discovery",
            "url-mapping", "routing",
            "shell-url-mapping", "routing");

    @Override
    public ReturnResult<List<ServerFilterTypeMetaVO>> filterTypes() {
        List<ServerFilterTypeMetaVO> result = new ArrayList<>();
        // 1. 内置预设: 文件服务器(FileStorageServerFilter)
        result.add(new ServerFilterTypeMetaVO("file-storage",
                "文件服务器插件(FileStorageServerFilter), 将 HTTP 服务器变为文件服务器",
                fileStorageParams(),
                "classpath 需包含 spring-support-filesystem-starter; 存储桶自动取容器中的 FileStorage Bean",
                groupOf(null, "file-storage"), detailPageOf("file-storage"), detailKindOf("file-storage"),
                detailLabelOf("file-storage"), false));
        // 2. 内置预设: 服务发现(ServiceDiscoveryServerFilter, 未注册为 ServerFilter SPI, 按别名装配)
        result.add(new ServerFilterTypeMetaVO("service-discovery",
                "服务发现插件(ServiceDiscoveryServerFilter), 按服务名把请求路由到已注册实例",
                serviceDiscoveryParams(),
                "classpath 需含 ServiceDiscovery SPI(内置 peerMesh); discoveryName 留空取 peerMesh",
                groupOf(null, "service-discovery"), detailPageOf("service-discovery"),
                detailKindOf("service-discovery"), detailLabelOf("service-discovery"), false));
        // 3. SPI 动态发现的 ServerFilter(仅暴露带 @SpiDescribe 元数据的对外过滤器)
        ServiceProvider.of(ServerFilter.class).forDefinitionEach(definition -> {
            String describe = definition.getDescribe();
            if (describe == null || describe.isBlank()) {
                // 无元数据的同包内部过滤器(如 Ip/Cors/Gzip)不暴露
                return;
            }
            String name = definition.getName();
            if (name == null || name.isBlank()) {
                return;
            }
            result.add(new ServerFilterTypeMetaVO(name, describe, paramsOf(definition), null,
                    groupOf(definition, name), detailPageOf(name), detailKindOf(name), detailLabelOf(name), true));
        });
        // 4. 通用插件: ServerFilter 全限定类名
        result.add(new ServerFilterTypeMetaVO("ServerFilter 全限定类名",
                "任意 ServerFilter 实现(优先无参构造, 其次 (ServerSetting) 构造); params 经 init(ServerFilterConfig) 注入",
                List.of(), null, "custom", null, null, null, false));
        return ReturnResult.ok(result);
    }

    /**
     * 文件服务器预设参数（含枚举选项，前端可直接渲染下拉）。
     *
     * <p>与 {@code ServerFilterFactory} 的 {@code SETTING_BOOLEAN_KEYS} /
     * {@code SETTING_STRING_KEYS} 白名单严格一致：该白名单之外的键只会被
     * {@code applySettingParams} 记一条 WARN 后丢弃，即「声明了但不生效」。
     * 反之像 {@code layout} 这类必填开关若漏声明，前端 schema 驱动表单就渲染不出它，
     * 只能改数据库，用户无从开启（目录列表页曾因此恒为 404）。</p>
     *
     * <p>包级可见以便 {@code FileStorageParamSchemaTest} 直接断言这份清单，
     * 无需构造本服务（依赖 mapper/registry）。</p>
     *
     * @return 参数列表
     */
    static List<ServerPluginParamVO> fileStorageParams() {
        List<ServerPluginParamVO> params = new ArrayList<>();
        params.add(param("openPreview", "true", "是否允许内联预览", "boolean"));
        params.add(param("openDownload", "true", "是否允许附件下载", "boolean"));
        params.add(param("openRange", "true", "是否支持 Range 分段", "boolean"));
        params.add(param("openWatermark", "false", "是否启用水印", "boolean"));
        params.add(param("openRemoteFile", "false", "是否允许远程文件透传", "boolean"));
        params.add(paramEnum("thirdUrlPreview", "NONE", "三方 URL 预览能力（选择后台后 ?url= 才生效）", "enum",
                Arrays.asList("NONE", "SERVER")));
        params.add(param("openWebjars", "true", "是否开放 webjars", "boolean"));
        params.add(param("watermark", "", "水印文本", "string"));
        params.add(param("layout", "false", "是否开启目录列表页（关闭时访问目录返回 404）", "boolean"));
        params.add(param("showFormat", "true", "目录列表显示文件格式", "boolean"));
        params.add(param("showSize", "true", "目录列表显示文件大小", "boolean"));
        params.add(param("showTime", "true", "目录列表显示修改时间", "boolean"));
        params.add(paramEnum("layoutType", "SIMPLE", "目录列表布局", "enum",
                Arrays.asList("SIMPLE", "SYSTEM")));
        params.add(paramEnum("imageFilter", "NONE", "图片处理滤镜", "enum",
                Arrays.asList("NONE", "BLUR", "GRAYSCALE", "INVERT", "SATURATE", "SEPIA")));
        return params;
    }

    /**
     * 服务发现预设参数（与 {@code ServerFilterFactory} 实际落位的 setter 一致）。
     *
     * @return 参数列表
     */
    private static List<ServerPluginParamVO> serviceDiscoveryParams() {
        List<ServerPluginParamVO> params = new ArrayList<>();
        params.add(param("discoveryName", "peerMesh", "ServiceDiscovery SPI 名", "string"));
        params.add(param("protocol", "", "限定发现协议(留空不限)", "string"));
        params.add(param("balance", "weight", "负载均衡策略", "string"));
        params.add(param("scatterId", "", "分组/散列标识", "string"));
        params.add(param("excludeServerId", "", "排除的服务标识", "string"));
        params.add(param("routes", "", "路由映射, 形如 /api/order=ORDER-SVC,/api/user=USER-SVC", "string"));
        return params;
    }

    /**
     * 构造一个普通参数描述。
     *
     * @param name         参数名
     * @param defaultValue 默认值
     * @param desc         说明
     * @param type         类型(boolean/string/number/enum)
     * @return 参数描述
     */
    private static ServerPluginParamVO param(String name, String defaultValue, String desc, String type) {
        return new ServerPluginParamVO(name, defaultValue, desc, type, null);
    }

    /**
     * 构造一个枚举参数描述（携带 options）。
     *
     * @param name         参数名
     * @param defaultValue 默认值
     * @param desc         说明
     * @param type         类型
     * @param options      可选项
     * @return 参数描述
     */
    private static ServerPluginParamVO paramEnum(String name, String defaultValue, String desc, String type,
                                          List<String> options) {
        return new ServerPluginParamVO(name, defaultValue, desc, type, options);
    }

    /**
     * 过滤器类型 → 深度查看页路由。
     *
     * @param type 过滤器类型
     * @return 路由，无则 空
     */
    private String detailPageOf(String type) {
        return type == null ? null : FILTER_DETAIL_PAGES.get(normalizeType(type));
    }

    /**
     * 过滤器类型 → 深度查看目标类型。
     *
     * <p>{@code node} 表示深度页面由插件自身 HTTP 服务提供，前端应新窗口直开节点访问地址；
     * {@code route} 表示深度页面由宿主应用托管，前端应校验路由存在后跳转；
     * 空 表示该插件没有深度查看入口。</p>
     *
     * @param type 过滤器类型
     * @return node / route / 空
     */
    private String detailKindOf(String type) {
        if (type == null) {
            return null;
        }
        String key = normalizeType(type);
        if (FILTER_NODE_DETAIL_TYPES.contains(key)) {
            return "node";
        }
        return FILTER_DETAIL_PAGES.containsKey(key) ? "route" : null;
    }

    /**
     * 过滤器类型 → 深度查看入口文案。
     *
     * @param type 过滤器类型
     * @return 文案，无则 空
     */
    private String detailLabelOf(String type) {
        return type == null ? null : FILTER_DETAIL_LABELS.get(normalizeType(type));
    }

    /**
     * 过滤器类型 → 前端分组：优先内置映射，其次 @SpiDescribe.type，最后 other。
     *
     * @param definition SPI 定义（内置预设传 空）
     * @param type       过滤器类型
     * @return 分组标识
     */
    private String groupOf(ServiceDefinition definition, String type) {
        String group = type == null ? null : FILTER_GROUPS.get(normalizeType(type));
        if (group != null) {
            return group;
        }
        if (definition != null) {
            String describeType = definition.getDescribeType();
            if (describeType != null && !describeType.isBlank()) {
                return describeType;
            }
        }
        return "other";
    }

    /**
     * 归一化过滤器类型，用于映射查找。
     *
     * <p>SPI 框架会把 {@code @Spi} 别名统一转成大写
     * （{@code ServiceDefinitionUtils} 中 {@code setName(alias.toUpperCase())}），
     * 而内置预设别名是小写（如 {@code file-storage}），因此查找前统一转小写，
     * 否则 SPI 发现的插件（SERVICE-DISCOVERY / REVERSE-PROXY 等）会全部匹配不上，
     * 导致分组落到 other、深度查看入口直接消失。</p>
     *
     * @param type 过滤器类型
     * @return 归一化后的类型
     */
    private static String normalizeType(String type) {
        return type.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
 * 「已落库但运行态操作失败」的业务码
 *
 * <p>刻意走三参重载 {@code error(code, msg, data)} 而非两参形式：两参的
 * {@code error(String, String)} 与「携带载荷」的语义在签名上无法区分 ——
 * 载荷类型恰好是 {@code String} 时，编译器会选中前者，把错误信息当成业务码、
 * 同时静默丢掉载荷。这是实测踩到的，不是推演。</p>
     *
     * @return 业务码
     */
    private static String partialFailureCode() {
        return ReturnCode.SYSTEM_SERVER_BUSINESS_ERROR.getCode();
    }

    /**
     * 构造「已落库但运行态操作失败」的失败结果，并把节点视图一并回传。
     *
     * <p>抽成方法是因为热更新与热启停两条路径犯的是同一个错：都先把变更
     * {@code updateById} 写进库，再执行运行态操作；后者失败时若只返回文字，
     * 调用方拿不到 id，既无法回滚配置、也无法定位是哪条记录出了问题。
     * 两条路径各写一遍的话，下一次新增路径又会漏掉。</p>
     *
     * <p>做成泛型 + 载荷构造器，是因为两条路径的载荷类型本就不一样：
     * {@code hotUpdate} 返回 {@link ServerHotUpdateResult}，而
     * {@code hotToggleFilter} 返回 {@link ServerNodeView}。曾试过把泛型写死成其中
     * 一个，编译期立刻报了另一个方法无法转换 —— 这正是把类型差异显式化的好处：
     * 写错当场编译失败，而不是等到线上序列化时才暴露。</p>
     *
     * <p>刻意按 id 重新查库，而不是复用 catch 块附近的局部变量：那些 node 可能已不是
     * 最新一次落库后的状态，重新查更贴近调用方随后实际会读到的内容。</p>
     *
     * @param id      节点 id
     * @param reason  失败原因
     * @param payload 由节点视图构造载荷的函数
     * @param <T>     载荷类型
     * @return 带载荷的失败结果；节点已查不到时退回不带载荷的形式
     */
    private <T> ReturnResult<T> hotUpdateFailure(Long id, String reason, java.util.function.Function<ServerNodeView, T> payload) {
        MsServerNode node = nodeMapper.selectById(id);
        if (node == null) {
            // 查不到说明记录已被回滚，此时没有半成品需要交代，按普通失败返回
            return ReturnResult.error(reason);
        }
        return ReturnResult.error(partialFailureCode(), reason, payload.apply(toView(node)));
    }

    /**
     * 启用=1 时按定义拉起节点并返回视图。
     *
     * <p>启动失败时<b>仍把已落库的节点视图放进 data 返回</b>：定义已经写进库了，
     * 调用方必须拿得到 id 才能自行决定是回滚删除、还是保留下来改配置后重试。
     * 此前这里只返回一段文字，于是响应里 {@code data=null} —— 而此时库里明明多了一条
     * 记录，调用方按 id 清理时会直接漏掉它，留下的僵尸节点只能靠人按名字猜着删。
     * 这正是「失败但留下半成品」时最需要把处置权交出去的地方。</p>
     *
     * @param node 节点定义
     * @return 节点视图
     */
    private ReturnResult<ServerNodeView> startIfEnabled(MsServerNode node) {
        if (node.getEnabled() != null && node.getEnabled() == 1) {
            try {
                nodeRegistry.start(node);
            } catch (Exception e) {
                // 定义已落库（用户能在列表里看到并修正），但运行态没起来。
                // 此时必须让 success=false：原先只记一条 WARN 后一律返回 ok，于是出现
                // 「HTTP 200 + success=true」而载荷里 status=ERROR 的自相矛盾结果，
                // 只看 success 的调用方（含前端列表刷新逻辑）会把失败当成成功。
                log.warn("[ServerManage] 节点 [{}] 启动失败(定义已保存): {}", node.getName(), e.getMessage());
                // 必须把已落库的节点视图放进 data：定义已经写进库了，调用方拿不到 id
                // 就无法自行回滚。此前只返回文字，响应里 data=null，而库里明明多了一条，
                // 调用方按 id 清理会直接漏掉它，留下只能靠人按名字猜着删的僵尸节点。
                return ReturnResult.error(partialFailureCode(), "节点已保存但启动失败: " + e.getMessage(), toView(node));
            }
            // start 未抛异常不等于真的起来了：SPI 内部会把实例化异常吞成 null，
            // 或服务器在 doStart 阶段静默失败。这里再核一次运行态，避免漏判。
            RuntimeServer runtime = nodeRegistry.get(node.getId());
            if (runtime == null || !"RUNNING".equals(runtime.status())) {
                String reason = runtime != null && runtime.getError() != null
                        ? runtime.getError() : "运行态未进入 RUNNING 状态";
                log.warn("[ServerManage] 节点 [{}] 启动未成功(定义已保存): {}", node.getName(), reason);
                // 同上：运行态没起来时定义仍在库里，必须把 id 回给调用方
                return ReturnResult.error(partialFailureCode(), "节点已保存但启动失败: " + reason, toView(node));
            }
        }
        return ReturnResult.ok(toView(node));
    }

    /**
     * 节点是否为启用态（{@code enabled=1}）。
     *
     * @param node 节点定义
     * @return true 表示启用
     */
    private boolean isEnabledNode(MsServerNode node) {
        return node.getEnabled() != null && node.getEnabled() == 1;
    }

    /**
     * 热更新时把「已落库但未运行」的启用节点按新配置拉起。
     *
     * <p>与 {@link #startIfEnabled(MsServerNode)} 同口径：拉起成功返回 {@code ok}
     * 并把 {@code restarted=true}；失败时定义仍在库里，故用
     * {@link #hotUpdateFailure(Long, String, java.util.function.Function)} 把节点视图
     * 一并回传给调用方（否则响应 {@code data=null}，调用方按 id 回滚/重试会漏掉这条记录）。</p>
     *
     * @param id               节点 id
     * @param node             已应用新配置的节点定义
     * @param result           预置的结果对象（复用其文案字段）
     * @param listeningChanged 本次是否为监听参数变更
     * @return 热更新结果
     */
    private ReturnResult<ServerHotUpdateResult> bringUpAfterHotUpdate(Long id, MsServerNode node,
                                                                      ServerHotUpdateResult result,
                                                                      boolean listeningChanged) {
        try {
            nodeRegistry.start(node);
            // start 未抛异常不等于真的起来了：SPI 可能把实例化异常吞成 null，
            // 或服务器在 doStart 阶段静默失败，这里再核一次运行态。
            RuntimeServer started = nodeRegistry.get(id);
            if (started != null && "RUNNING".equals(started.status())) {
                result.setHotApplied(false);
                result.setRestarted(true);
                result.addAppliedItem(listeningChanged
                        ? "监听参数变更且节点未运行, 已按新配置启动"
                        : "节点处于错误态, 已按新配置重新启动");
                result.addAppliedItem("过滤器链已按新配置重建");
                result.setMessage("节点未运行, 已按新配置启动");
                result.setNode(toView(node));
                return ReturnResult.ok(result);
            }
            String reason = started != null && started.getError() != null
                    ? started.getError() : "运行态未进入 RUNNING 状态";
            log.warn("[ServerManage] 节点 [{}] 热更新自拉起未成功: {}", node.getName(), reason);
            return hotUpdateFailure(id, "节点已保存但启动失败: " + reason,
                    partialHotUpdatePayload("配置已保存但启动失败"));
        } catch (Exception e) {
            log.error("[ServerManage] 节点 [{}] 热更新自拉起失败: {}", node.getName(), e.getMessage(), e);
            return hotUpdateFailure(id, "节点已保存但启动失败: " + e.getMessage(),
                    partialHotUpdatePayload("配置已保存但启动失败"));
        }
    }

    /**
     * 构造「已保存但热生效失败」的热更新载荷。
     *
     * @param message 提示文案
     * @return 载荷构造器
     */
    private static java.util.function.Function<ServerNodeView, ServerHotUpdateResult> partialHotUpdatePayload(String message) {
        return view -> {
            ServerHotUpdateResult payload = new ServerHotUpdateResult();
            payload.setHotApplied(false);
            payload.setRestarted(false);
            payload.setMessage(message);
            payload.setNode(view);
            return payload;
        };
    }

    /**
     * 解析并校验协议类型。
     *
     * @param request 请求体
     * @return 协议枚举（SPI 动态协议返回 空）
     */
    private ServerNodeProtocol requireProtocol(ServerNodeRequest request) {
        ServerNodeProtocol protocol = ServerNodeProtocol.of(request.getProtocol());
        if (protocol != null) {
            return protocol;
        }
        if (ServiceProvider.of(Server.class).isSupport(request.getProtocol())) {
            return null; // SPI 动态协议
        }
        throw new IllegalArgumentException("不支持的协议类型: " + request.getProtocol());
    }

    /**
     * 校验节点名称唯一。
     *
     * @param name      名称
     * @param excludeId 排除的节点 标识(更新场景)
     */
    private void requireUniqueName(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("节点名称不能为空");
        }
        LambdaQueryWrapper<MsServerNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsServerNode::getName, name.trim());
        if (excludeId != null) {
            wrapper.ne(MsServerNode::getId, excludeId);
        }
        if (nodeMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("节点名称已存在: " + name);
        }
    }

    /**
     * 请求体 → 实体(全量替换)。
     *
     * @param node     目标实体
     * @param request  请求体
     * @param protocol 协议枚举
     */
    private void applyRequest(MsServerNode node, ServerNodeRequest request, ServerNodeProtocol protocol) {
        node.setName(request.getName().trim());
        node.setProtocol(request.getProtocol().trim().toLowerCase());
        String engine = request.getEngine() == null || request.getEngine().isBlank()
                ? (protocol == null ? request.getProtocol().trim().toLowerCase() : protocol.getDefaultEngine())
                : request.getEngine().trim().toLowerCase();
        node.setEngine(engine);
        node.setHost(request.getHost() == null || request.getHost().isBlank() ? "0.0.0.0" : request.getHost().trim());
        node.setPort(request.getPort() != null ? request.getPort() : (protocol == null ? null : protocol.getDefaultPort()));
        node.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        node.setRemark(request.getRemark());
        try {
            node.setConfig(objectMapper.writeValueAsString(
                    request.getConfig() == null ? Map.of() : request.getConfig()));
            node.setFilters(objectMapper.writeValueAsString(
                    request.getFilters() == null ? List.of() : request.getFilters()));
        } catch (Exception e) {
            throw new IllegalArgumentException("config/filters JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 实体 + 运行时 → 视图。
     *
     * @param node 节点定义
     * @return 视图
     */
    private ServerNodeView toView(MsServerNode node) {
        String protocolDesc;
        ServerNodeProtocol protocol = ServerNodeProtocol.of(node.getProtocol());
        if (protocol != null) {
            protocolDesc = protocol.getDesc();
        } else {
            // SPI 动态协议：取 @SpiDescribe 或类简单名
            ServiceDefinition def = ServiceProvider.of(Server.class).getDefinition(node.getProtocol());
            protocolDesc = def != null && def.getDescribe() != null
                    ? def.getDescribe() : node.getProtocol();
        }
        RuntimeServer runtime = nodeRegistry.get(node.getId());
        Map<String, Object> config;
        List<ServerFilterDescriptor> filters;
        try {
            config = nodeRegistry.parseConfig(node);
            filters = nodeRegistry.parseFilters(node);
        } catch (Exception e) {
            log.warn("[ServerManage] 节点 [{}] 配置解析失败: {}", node.getName(), e.getMessage());
            config = Map.of();
            filters = List.of();
        }
        return new ServerNodeView(
                node.getId(),
                node.getName(),
                node.getProtocol(),
                protocolDesc,
                node.getEngine(),
                node.getHost(),
                node.getPort(),
                node.getEnabled(),
                node.getRemark(),
                config,
                filters,
                runtime == null ? "STOPPED" : runtime.status(),
                runtime == null ? null : runtime.getError(),
                runtime == null ? null : runtime.getServerUrl());
    }

    /**
     * SPI 定义的描述文案：优先 @SpiDescribe，其次类简单名。
     *
     * @param definition SPI 定义
     * @param alias      小写别名
     * @return 描述
     */
    private String describeOf(ServiceDefinition definition, String alias) {
        String describe = definition.getDescribe();
        if (describe != null && !describe.isBlank()) {
            return describe;
        }
        Class<?> implClass = definition.getImplClass();
        return implClass != null ? implClass.getSimpleName() : alias;
    }

    /**
     * SPI 定义的配置模板（来自 @SpiDescribe.optional / @SpiParam）。
     *
     * @param definition SPI 定义
     * @return 参数列表（name/defaultValue/desc/type）
     */
    private List<ServerPluginParamVO> paramsOf(ServiceDefinition definition) {
        List<ServerPluginParamVO> params = new ArrayList<>();
        List<DescribeOptional> optional = definition.getDescribeOptional();
        if (optional == null || optional.isEmpty()) {
            return params;
        }
        for (DescribeOptional o : optional) {
            params.add(new ServerPluginParamVO(o.getName(), o.getDefaultValue(), o.getDescribe(), o.getType(), null));
        }
        return params;
    }
}
