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
import com.chua.starter.monitor.support.server.model.ServerNodeRequest;
import com.chua.starter.monitor.support.server.model.ServerNodeView;
import com.chua.starter.monitor.support.service.ServerManageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    @Override
    public ReturnResult<List<Map<String, Object>>> filterTypes() {
        List<Map<String, Object>> result = new ArrayList<>();
        // 1. 内置预设: 文件服务器(FileStorageServerFilter)
        Map<String, Object> fileStorage = new LinkedHashMap<>();
        fileStorage.put("type", "file-storage");
        fileStorage.put("desc", "文件服务器插件(FileStorageServerFilter), 将 HTTP 服务器变为文件服务器");
        fileStorage.put("params", Map.of(
                "openPreview", "是否允许内联预览(true/false)",
                "openDownload", "是否允许附件下载(true/false)",
                "openRange", "是否支持 Range 分段(true/false)",
                "openWatermark", "是否启用水印(true/false)",
                "openRemoteFile", "是否允许远程文件透传(true/false)",
                "openWebjars", "是否开放 webjars(true/false)",
                "watermark", "水印文本"));
        fileStorage.put("require", "classpath 需包含 spring-support-filesystem-starter; 存储桶自动取容器中的 FileStorage Bean");
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
            item.put("spi", true);
            result.add(item);
        });
        // 3. 通用插件: ServerFilter 全限定类名
        Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("type", "ServerFilter 全限定类名");
        custom.put("desc", "任意 ServerFilter 实现(优先无参构造, 其次 (ServerSetting) 构造); params 经 init(ServerFilterConfig) 注入");
        custom.put("spi", false);
        result.add(custom);
        return ReturnResult.ok(result);
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
    private List<Map<String, String>> paramsOf(ServiceDefinition definition) {
        List<Map<String, String>> params = new ArrayList<>();
        List<DescribeOptional> optional = definition.getDescribeOptional();
        if (optional == null || optional.isEmpty()) {
            return params;
        }
        for (DescribeOptional o : optional) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", o.getName());
            item.put("defaultValue", o.getDefaultValue());
            item.put("desc", o.getDescribe());
            item.put("type", o.getType());
            params.add(item);
        }
        return params;
    }
}
