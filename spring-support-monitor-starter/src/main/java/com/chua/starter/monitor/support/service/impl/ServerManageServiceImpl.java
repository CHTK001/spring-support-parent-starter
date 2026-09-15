package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.chua.starter.monitor.support.server.model.ServerHotUpdateResult;
import com.chua.starter.monitor.support.server.model.ServerNodeRequest;
import com.chua.starter.monitor.support.server.model.ServerNodeView;
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
* 已启用=1 时按新配置重新拉起。</p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerManageServiceImpl implements ServerManageService {

    /** 节点 映射器 */
    private final MsServerNodeMapper nodeMapper;
    /** 运行时注册表 */
    private final ServerNodeRegistry nodeRegistry;
    /** JSON */
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
            return ReturnResult.ok(null);
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
            nodeMapper.updateById(node);

            ServerHotUpdateResult result = new ServerHotUpdateResult();
            if (!wasRunning) {
                // 未运行：仅持久化定义，等待下次启动
                result.setHotApplied(false);
                result.setRestarted(false);
                result.addAppliedItem("定义已保存(节点未运行, 下次启动生效)");
                result.setMessage("节点未运行, 配置已保存, 启动后生效");
                result.setNode(toView(node));
                return ReturnResult.ok(result);
            }

            if (listeningChanged) {
                // 监听参数变更：先停后起
                nodeRegistry.stop(id);
                nodeRegistry.start(node);
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
            result.setHotApplied(true);
            result.setRestarted(false);
            result.addAppliedItem("过滤器链已热更新");
            result.setMessage("监听参数未变, 仅热更新过滤器链");
            result.setNode(toView(node));
            return ReturnResult.ok(result);
        } catch (Exception e) {
            log.error("[ServerManage] 热更新失败: id={} - {}", id, e.getMessage(), e);
            return ReturnResult.error("热更新失败: " + e.getMessage());
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
            descriptors.get(index).setEnabled(target);
            node.setFilters(objectMapper.writeValueAsString(descriptors));
            nodeMapper.updateById(node);
            RuntimeServer runtime = nodeRegistry.get(id);
            if (runtime != null && runtime.isRunning()) {
                nodeRegistry.hotToggleFilter(node, index, target);
            }
            return ReturnResult.ok(toView(node));
        } catch (Exception e) {
            log.error("[ServerManage] 过滤器热启停失败: id={} index={} - {}", id, index, e.getMessage(), e);
            return ReturnResult.error("过滤器热启停失败: " + e.getMessage());
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
            if (descriptor.getParams() != null) {
                merged.putAll(descriptor.getParams());
            }
            if (params != null) {
                params.forEach((k, v) -> merged.put(k, v == null ? null : String.valueOf(v)));
            }
            descriptor.setParams(merged);
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

    /** HTTP 引擎变体（SPI 注册但不应出现在协议下拉中） */
    private static final Set<String> HTTP_ENGINE_ALIASES =
            Set.of("jdk", "nio", "aio", "jdk-http", "nio-http", "aio-http");

    @Override
    public ReturnResult<List<Map<String, Object>>> protocols() {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        // 1. 内置四类基础协议（含默认端口/引擎等富元数据）
        for (ServerNodeProtocol protocol : ServerNodeProtocol.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", protocol.getCode());
            item.put("alias", protocol.getAlias());
            item.put("desc", protocol.getDesc());
            item.put("defaultPort", protocol.getDefaultPort());
            item.put("defaultEngine", protocol.getDefaultEngine());
            item.put("requiresBackend", protocol.isRequiresBackend());
            item.put("spi", false);
            result.add(item);
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
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", name);
            item.put("alias", alias);
            item.put("desc", describeOf(definition, alias));
            item.put("defaultPort", null);
            item.put("defaultEngine", alias);
            item.put("requiresBackend", alias.contains("proxy") && !alias.contains("socks"));
            item.put("spi", true);
            item.put("params", paramsOf(definition));
            result.add(item);
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
            "service-discovery", "/discovery",
            "reverse-proxy", "/proxy");
    /**
     * 过滤器类型 → 深度查看目标为「节点自身访问地址」。
     *
     * <p>这些插件的深度页面由插件自己的 HTTP 服务提供（FileStorageServerFilter 的 SYSTEM 布局
     * 在 {@code {serverUrl}{bucket}} 上返回 classpath {@code static/index.html}），
     * 因此前端应新窗口直开节点地址，而不是跳宿主路由——宿主未挂载该页时同样可用，不会死链。</p>
     */
    private static final Set<String> FILTER_NODE_DETAIL_TYPES = Set.of("file-storage");
    /** 过滤器类型 → 深度查看入口文案 */
    private static final Map<String, String> FILTER_DETAIL_LABELS = Map.of(
            "file-storage", "打开文件管理器",
            "service-discovery", "查看服务实例",
            "reverse-proxy", "打开代理管理");
    /** 过滤器类型 → 前端分组（左栏归类） */
    private static final Map<String, String> FILTER_GROUPS = Map.of(
            "file-storage", "storage",
            "service-discovery", "discovery",
            "reverse-proxy", "proxy",
            "url-mapping", "routing",
            "shell-url-mapping", "routing");

    @Override
    public ReturnResult<List<Map<String, Object>>> filterTypes() {
        List<Map<String, Object>> result = new ArrayList<>();
        // 1. 内置预设: 文件服务器(FileStorageServerFilter)
        Map<String, Object> fileStorage = new LinkedHashMap<>();
        fileStorage.put("type", "file-storage");
        fileStorage.put("desc", "文件服务器插件(FileStorageServerFilter), 将 HTTP 服务器变为文件服务器");
        fileStorage.put("params", fileStorageParams());
        fileStorage.put("require", "classpath 需包含 spring-support-filesystem-starter; 存储桶自动取容器中的 FileStorage Bean");
        fileStorage.put("group", groupOf(null, "file-storage"));
        fileStorage.put("detailPage", detailPageOf("file-storage"));
        fileStorage.put("detailKind", detailKindOf("file-storage"));
        fileStorage.put("detailLabel", detailLabelOf("file-storage"));
        fileStorage.put("spi", false);
        result.add(fileStorage);
        // 2. SPI 动态发现的 ServerFilter(仅暴露带 @SpiDescribe 元数据的对外过滤器)
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
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", name);
            item.put("desc", describe);
            item.put("params", paramsOf(definition));
            item.put("group", groupOf(definition, name));
            item.put("detailPage", detailPageOf(name));
            item.put("detailKind", detailKindOf(name));
            item.put("detailLabel", detailLabelOf(name));
            item.put("spi", true);
            result.add(item);
        });
        // 3. 通用插件: ServerFilter 全限定类名
        Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("type", "ServerFilter 全限定类名");
        custom.put("desc", "任意 ServerFilter 实现(优先无参构造, 其次 (ServerSetting) 构造); params 经 init(ServerFilterConfig) 注入");
        custom.put("params", List.of());
        custom.put("group", "custom");
        custom.put("detailPage", null);
        custom.put("detailKind", null);
        custom.put("detailLabel", null);
        custom.put("spi", false);
        result.add(custom);
        return ReturnResult.ok(result);
    }

    /**
    * 文件服务器预设参数（含枚举选项，前端可直接渲染下拉）。
    *
    * @return 参数列表
     */
    private List<Map<String, Object>> fileStorageParams() {
        List<Map<String, Object>> params = new ArrayList<>();
        params.add(param("openPreview", "true", "是否允许内联预览", "boolean"));
        params.add(param("openDownload", "true", "是否允许附件下载", "boolean"));
        params.add(param("openRange", "true", "是否支持 Range 分段", "boolean"));
        params.add(param("openWatermark", "false", "是否启用水印", "boolean"));
        params.add(param("openRemoteFile", "false", "是否允许远程文件透传", "boolean"));
        params.add(param("openWebjars", "true", "是否开放 webjars", "boolean"));
        params.add(param("watermark", "", "水印文本", "string"));
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
    * 构造一个普通参数描述。
    *
    * @param name         参数名
    * @param defaultValue 默认值
    * @param desc         说明
    * @param type         类型(boolean/string/number/enum)
    * @return 参数描述
     */
    private Map<String, Object> param(String name, String defaultValue, String desc, String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("defaultValue", defaultValue);
        item.put("desc", desc);
        item.put("type", type);
        return item;
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
    private Map<String, Object> paramEnum(String name, String defaultValue, String desc, String type,
                                          List<String> options) {
        Map<String, Object> item = param(name, defaultValue, desc, type);
        item.put("options", options);
        return item;
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
    * 已启用=1 时按定义拉起节点并返回视图。
    *
    * @param node 节点定义
    * @return 节点视图
     */
    private ReturnResult<ServerNodeView> startIfEnabled(MsServerNode node) {
        if (node.getEnabled() != null && node.getEnabled() == 1) {
            try {
                nodeRegistry.start(node);
            } catch (Exception e) {
                log.warn("[ServerManage] 节点 [{}] 启动失败(定义已保存): {}", node.getName(), e.getMessage());
            }
        }
        return ReturnResult.ok(toView(node));
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
            /**
            * illegal参数异常。
            * @param "节点名称不能为空" "节点名称不能为空"
            * @return illegal参数异常的结果
             */
            throw new IllegalArgumentException("节点名称不能为空");
        }
        LambdaQueryWrapper<MsServerNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsServerNode::getName, name.trim());
        if (excludeId != null) {
            wrapper.ne(MsServerNode::getId, excludeId);
        }
        if (nodeMapper.selectCount(wrapper) > 0) {
            /**
            * illegal参数异常。
            * @param name 名称
            * @return illegal参数异常的结果
             */
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
            /**
            * illegal参数异常。
            * @param e.getMessage( e.获取消息(
            * @return illegal参数异常的结果
             */
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
        ServerNodeView view = new ServerNodeView();
        view.setId(node.getId());
        view.setName(node.getName());
        view.setProtocol(node.getProtocol());
        ServerNodeProtocol protocol = ServerNodeProtocol.of(node.getProtocol());
        if (protocol != null) {
            view.setProtocolDesc(protocol.getDesc());
        } else {
            // SPI 动态协议：取 @SpiDescribe 或类简单名
            ServiceDefinition def = ServiceProvider.of(Server.class).getDefinition(node.getProtocol());
            view.setProtocolDesc(def != null && def.getDescribe() != null
                    ? def.getDescribe() : node.getProtocol());
        }
        view.setEngine(node.getEngine());
        view.setHost(node.getHost());
        view.setPort(node.getPort());
        view.setEnabled(node.getEnabled());
        view.setRemark(node.getRemark());
        RuntimeServer runtime = nodeRegistry.get(node.getId());
        view.setStatus(runtime == null ? "STOPPED" : runtime.status());
        view.setError(runtime == null ? null : runtime.getError());
        view.setServerUrl(runtime == null ? null : runtime.getServerUrl());
        try {
            view.setConfig(nodeRegistry.parseConfig(node));
            view.setFilters(nodeRegistry.parseFilters(node));
        } catch (Exception e) {
            log.warn("[ServerManage] 节点 [{}] 配置解析失败: {}", node.getName(), e.getMessage());
            view.setConfig(Map.of());
            view.setFilters(List.of());
        }
        return view;
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
    private List<Map<String, Object>> paramsOf(ServiceDefinition definition) {
        List<Map<String, Object>> params = new ArrayList<>();
        List<DescribeOptional> optional = definition.getDescribeOptional();
        if (optional == null || optional.isEmpty()) {
            return params;
        }
        for (DescribeOptional o : optional) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", o.getName());
            item.put("defaultValue", o.getDefaultValue());
            item.put("desc", o.getDescribe());
            item.put("type", o.getType());
            params.add(item);
        }
        return params;
    }
}
