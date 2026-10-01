package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.monitor.support.middleware.MiddlewareEndpoint;
import com.chua.starter.monitor.support.middleware.MiddlewareHttp;
import com.chua.starter.monitor.support.middleware.MiddlewareNamespace;
import com.chua.starter.monitor.support.middleware.NacosClient;
import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.monitor.support.model.MiddlewareTestVO;
import com.chua.starter.monitor.support.model.NacosClusterNodeVO;
import com.chua.starter.monitor.support.model.NacosConfigDetailVO;
import com.chua.starter.monitor.support.model.NacosConfigPageVO;
import com.chua.starter.monitor.support.model.NacosConfigVO;
import com.chua.starter.monitor.support.model.NacosInstanceVO;
import com.chua.starter.monitor.support.model.NacosNamespaceVO;
import com.chua.starter.monitor.support.model.NacosServiceVO;
import com.chua.starter.monitor.support.model.dto.MiddlewareTestDTO;
import com.chua.starter.monitor.support.model.dto.NacosConfigKeyDTO;
import com.chua.starter.monitor.support.model.dto.NacosConfigQueryDTO;
import com.chua.starter.monitor.support.model.dto.NacosConfigSaveDTO;
import com.chua.starter.monitor.support.model.dto.NacosInstanceUpdateDTO;
import com.chua.starter.monitor.support.model.dto.NacosNamespaceDTO;
import com.chua.starter.monitor.support.service.NacosManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nacos 管理实现：全部走 Nacos v1 OpenAPI，不落任何本地副本。
 *
 * <p>Nacos 各版本在列表接口回传的字段不完全一致（如配置格式、更新时间只在较新版本出现），
 * 这里一律「取到就填、取不到留空」，不用默认值冒充真实数据。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NacosManageServiceImpl implements NacosManageService {

    /**
     * 配置列表默认每页条数
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 配置列表最大每页条数，避免一次拉穿 Nacos
     */
    private static final int MAX_PAGE_SIZE = 500;

    /**
     * 服务列表一次取回的上限
     */
    private static final int SERVICE_LIMIT = 200;

    /**
     * 未指定分组时 Nacos 使用的分组
     */
    private static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    /**
     * HTTP 日期解析格式（Last-Modified 头）
     */
    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;

    /**
     * Nacos OpenAPI 客户端
     */
    private final NacosClient client;

    @Override
    public MiddlewareTestVO test(MiddlewareTestDTO request) {
        MiddlewareEndpoint endpoint = testEndpoint(request);
        try {
            client.getObject(endpoint, "/v1/console/namespaces", Map.of());
        } catch (IllegalStateException e) {
            return new MiddlewareTestVO(false, null, e.getMessage());
        }
        return new MiddlewareTestVO(true, version(endpoint), "连接成功");
    }

    @Override
    public List<NacosNamespaceVO> namespaces(Long datasourceId) {
        MiddlewareEndpoint endpoint = client.endpoint(datasourceId);
        JsonObject json = client.getObject(endpoint, "/v1/console/namespaces", Map.of());
        JsonArray data = json.getJsonArray("data");
        List<NacosNamespaceVO> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            JsonObject item = data.getJsonObject(i);
            String tenant = str(item.get("namespace"));
            String display = firstNonBlank(str(item.get("namespaceShowName")), MiddlewareNamespace.toDisplay(tenant));
            result.add(new NacosNamespaceVO(MiddlewareNamespace.toDisplay(tenant), tenant, display,
                    intOrNull(item.get("configCount"))));
        }
        return result;
    }

    @Override
    public boolean createNamespace(NacosNamespaceDTO dto) {
        MiddlewareEndpoint endpoint = client.endpoint(datasourceIdOf(dto));
        Map<String, String> form = MiddlewareNamespace.query(
                "customNamespaceId", trim(dto == null ? null : dto.namespaceId()),
                "namespaceName", requireText(dto == null ? null : dto.namespaceName(), "命名空间名称不能为空"),
                "namespaceDesc", trim(dto == null ? null : dto.namespaceDesc()));
        accepted(client.postForm(endpoint, "/v1/console/namespaces", form));
        return true;
    }

    @Override
    public boolean updateNamespace(NacosNamespaceDTO dto) {
        MiddlewareEndpoint endpoint = client.endpoint(datasourceIdOf(dto));
        Map<String, String> form = MiddlewareNamespace.query(
                "namespace", requireText(dto == null ? null : dto.namespaceId(), "命名空间 ID 不能为空"),
                "namespaceShowName", requireText(dto == null ? null : dto.namespaceName(), "命名空间名称不能为空"),
                "namespaceDesc", trim(dto == null ? null : dto.namespaceDesc()));
        accepted(client.putForm(endpoint, "/v1/console/namespaces", form));
        return true;
    }

    @Override
    public boolean deleteNamespace(Long datasourceId, String namespaceId) {
        MiddlewareEndpoint endpoint = client.endpoint(datasourceId);
        accepted(client.delete(endpoint, "/v1/console/namespaces",
                MiddlewareNamespace.query("namespaceId", requireText(namespaceId, "命名空间 ID 不能为空"))));
        return true;
    }

    @Override
    public NacosConfigPageVO configs(NacosConfigQueryDTO query) {
        MiddlewareEndpoint endpoint = client.endpoint(query.datasourceId());
        int page = positive(query.page(), 1);
        int size = pageSize(query.pageSize());
        String keyword = trim(query.dataId());
        Map<String, String> params = MiddlewareNamespace.query(
                "pageNo", String.valueOf(page),
                "pageSize", String.valueOf(size),
                "tenant", MiddlewareNamespace.toProtocol(query.namespace()),
                "group", trim(query.group()),
                "dataId", keyword,
                "search", keyword.isEmpty() ? "accurate" : "blur");
        JsonObject json = client.getObject(endpoint, "/v1/cs/configs", params);
        // 实测（2.4.3）：配置列表的条目数组字段名是 pageItems，1.x/2.x 一致。
        // 原先读的是 items，取不到 → list 永远空但 total 正确（totalCount 能读到），
        // 表现为"列表有总条数却一条都渲染不出来"。这里按 pageItems 优先、items 兜底。
        JsonArray items = firstArray(json, "pageItems", "items");
        List<NacosConfigVO> list = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.getJsonObject(i);
            list.add(new NacosConfigVO(str(item.get("dataId")),
                    firstNonBlank(str(item.get("group")), DEFAULT_GROUP),
                    emptyToNull(lower(str(item.get("type")))),
                    emptyToNull(timeOf(item))));
        }
        Integer total = intOrNull(json.get("totalCount"));
        return new NacosConfigPageVO(list, total == null ? list.size() : total, page, size);
    }

    @Override
    public NacosConfigDetailVO configDetail(String dataId, NacosConfigKeyDTO key) {
        MiddlewareEndpoint endpoint = client.endpoint(key.datasourceId());
        String group = defaultGroup(key.group());
        Map<String, String> params = MiddlewareNamespace.query(
                "dataId", requireText(dataId, "Data ID 不能为空"),
                "group", group,
                "tenant", MiddlewareNamespace.toProtocol(key.namespace()));
        MiddlewareHttp.Response response = client.getRaw(endpoint, "/v1/cs/configs", params);
        String namespace = MiddlewareNamespace.toDisplay(MiddlewareNamespace.toProtocol(key.namespace()));
        return new NacosConfigDetailVO(dataId, group, emptyToNull(lower(response.header("Config-Type"))),
                emptyToNull(httpDate(response.header("Last-Modified"))), namespace, response.body(), null);
    }

    @Override
    public boolean saveConfig(NacosConfigSaveDTO config) {
        if (config.content() == null || config.content().isBlank()) {
            throw new IllegalStateException("配置内容不能为空");
        }
        MiddlewareEndpoint endpoint = client.endpoint(config.datasourceId());
        Map<String, String> form = MiddlewareNamespace.query(
                "dataId", requireText(config.dataId(), "Data ID 不能为空"),
                "group", defaultGroup(config.group()),
                "tenant", MiddlewareNamespace.toProtocol(config.namespace()),
                "content", config.content());
        String type = lower(config.format());
        if (!type.isEmpty()) {
            form.put("type", type);
        }
        String betaIps = trim(config.betaIps());
        if (!betaIps.isEmpty()) {
            // beta 发布：同一发布端点，带上 betaIps（逗号分隔）
            form.put("betaIps", betaIps);
        }
        accepted(client.postForm(endpoint, "/v1/cs/configs", form));
        return true;
    }

    @Override
    public boolean deleteConfig(String dataId, NacosConfigKeyDTO key) {
        MiddlewareEndpoint endpoint = client.endpoint(key.datasourceId());
        Map<String, String> params = MiddlewareNamespace.query(
                "dataId", requireText(dataId, "Data ID 不能为空"),
                "group", defaultGroup(key.group()),
                "tenant", MiddlewareNamespace.toProtocol(key.namespace()));
        accepted(client.delete(endpoint, "/v1/cs/configs", params));
        return true;
    }

    @Override
    public List<NacosServiceVO> services(Long datasourceId, String namespace) {
        MiddlewareEndpoint endpoint = client.endpoint(datasourceId);
        Map<String, String> params = MiddlewareNamespace.query(
                "pageNo", "1",
                "pageSize", String.valueOf(SERVICE_LIMIT),
                "namespaceId", MiddlewareNamespace.toProtocol(namespace),
                "hasIpCount", "true",
                "withInstances", "false");
        JsonObject json = client.getObject(endpoint, "/v1/ns/catalog/services", params);
        JsonArray data = json.getJsonArray("serviceList");
        List<NacosServiceVO> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            JsonObject item = data.getJsonObject(i);
            Integer ipCount = intOrNull(item.get("ipCount"));
            Integer healthy = intOrNull(item.get("healthyInstanceCount"));
            Integer unhealthy = ipCount == null ? null : Math.max(0, ipCount - (healthy == null ? 0 : healthy));
            result.add(new NacosServiceVO(str(item.get("name")), countClusters(item.get("clusters")),
                    healthy, unhealthy, boolOrNull(item.get("triggerFlag"))));
        }
        return result;
    }

    @Override
    public List<NacosInstanceVO> instances(Long datasourceId, String namespace, String serviceName, String groupName) {
        MiddlewareEndpoint endpoint = client.endpoint(datasourceId);
        String group = defaultGroup(groupName);
        String fullName = serviceNameWithGroup(serviceName, group);
        Map<String, String> params = MiddlewareNamespace.query(
                "serviceName", fullName,
                "namespaceId", MiddlewareNamespace.toProtocol(namespace));
        JsonObject json = client.getObject(endpoint, "/v1/ns/instance/list", params);
        JsonArray hosts = json.getJsonArray("hosts");
        List<NacosInstanceVO> result = new ArrayList<>(hosts == null ? 0 : hosts.size());
        if (hosts != null) {
            for (int i = 0; i < hosts.size(); i++) {
                JsonObject host = hosts.getJsonObject(i);
                result.add(new NacosInstanceVO(
                        str(host.get("ip")),
                        intOrNull(host.get("port")),
                        doubleOrNull(host.get("weight")),
                        boolOrNull(host.get("healthy")),
                        boolOrNull(host.get("enabled")),
                        boolOrNull(host.get("ephemeral")),
                        emptyToNull(str(host.get("clusterName"))),
                        metadataOf(host.getJsonObject("metadata"))));
            }
        }
        return result;
    }

    @Override
    public boolean updateInstance(NacosInstanceUpdateDTO dto) {
        if (dto == null) {
            throw new IllegalStateException("实例更新参数不能为空");
        }
        MiddlewareEndpoint endpoint = client.endpoint(dto.datasourceId());
        String group = defaultGroup(dto.groupName());
        String fullName = serviceNameWithGroup(requireText(dto.serviceName(), "服务名不能为空"), group);
        Map<String, String> form = MiddlewareNamespace.query(
                "serviceName", fullName,
                "groupName", group,
                "namespaceId", MiddlewareNamespace.toProtocol(dto.namespace()),
                "ip", requireText(dto.ip(), "实例 IP 不能为空"),
                "port", dto.port() == null ? null : String.valueOf(dto.port()));
        if (dto.weight() != null) {
            form.put("weight", String.valueOf(dto.weight()));
        }
        if (dto.enabled() != null) {
            form.put("enabled", String.valueOf(dto.enabled()));
        }
        accepted(client.putForm(endpoint, "/v1/ns/instance", form));
        return true;
    }

    @Override
    public List<NacosClusterNodeVO> clusterNodes(Long datasourceId) {
        MiddlewareEndpoint endpoint = client.endpoint(datasourceId);
        JsonArray members = members(client.get(endpoint, "/v1/core/cluster/nodes", Map.of()));
        List<NacosClusterNodeVO> result = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            JsonObject node = members.getJsonObject(i);
            String[] parts = addressParts(str(node.get("address")));
            String ip = firstNonBlank(trim(str(node.get("ip"))), parts[0]);
            Integer port = intOrNull(firstNonNull(node.get("port"), parts[1]));
            String role = firstNonBlank(str(node.get("role")),
                    str(node.getJsonObject("extendInfo").get("role")),
                    str(node.getJsonObject("extendData").get("role")));
            Boolean leader = boolOrNull(node.get("leader"));
            if (leader == null) {
                leader = "LEADER".equalsIgnoreCase(role);
            }
            result.add(new NacosClusterNodeVO(ip, port, emptyToNull(statusOf(node)), emptyToNull(upper(role)),
                    intOrNull(firstNonNull(node.get("weight"), node.get("adWeight"))), leader));
        }
        return result;
    }

    /**
     * 解析测试目标：优先库中数据源，其次即席填写的地址，最后落到第一个 nacos 数据源。
     *
     * @param request 测试请求，可为空
     * @return 端点
     */
    private MiddlewareEndpoint testEndpoint(MiddlewareTestDTO request) {
        if (request == null || request.host() == null || request.host().isBlank()) {
            return client.endpoint(request == null ? null : request.datasourceId());
        }
        if (request.datasourceId() != null && request.datasourceId() > 0L) {
            return client.endpoint(request.datasourceId());
        }
        DataSourceVO adhoc = new DataSourceVO(null, "ad-hoc", "nacos", request.host().trim(), request.port(),
                request.contextPath(), request.username(), request.password(), null, 1, null, null, null,
                null, null, null, null, null, null);
        return client.endpoint(adhoc, "/nacos");
    }

    /**
     * 命名空间载荷里的数据源 ID。
     *
     * @param dto 命名空间载荷，可为空
     * @return 数据源 ID
     */
    private static Long datasourceIdOf(NacosNamespaceDTO dto) {
        return dto == null ? null : dto.datasourceId();
    }

    /**
     * Nacos 服务全名：分组与服务名用 {@code @@} 拼接。
     *
     * @param serviceName 服务名
     * @param group       分组
     * @return 形如 {@code DEFAULT_GROUP@@service-name}
     */
    private static String serviceNameWithGroup(String serviceName, String group) {
        String name = trim(serviceName);
        if (name.contains("@@")) {
            return name;
        }
        return group + "@@" + name;
    }

    /**
     * 把 metadata 对象转成字符串映射，null 时返回空映射。
     *
     * @param metadata 原始 metadata 对象，可为空
     * @return 字符串映射
     */
    private static Map<String, String> metadataOf(JsonObject metadata) {
        Map<String, String> map = new HashMap<>();
        if (metadata == null) {
            return map;
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            map.put(entry.getKey(), str(entry.getValue()));
        }
        return map;
    }

    /**
     * 尽力取版本号，该接口在部分部署下不可用。
     *
     * @param endpoint 端点
     * @return 版本号，取不到为 null
     */
    private String version(MiddlewareEndpoint endpoint) {
        try {
            return emptyToNull(str(client.getObject(endpoint, "/v1/console/server/state", Map.of()).get("version")));
        } catch (IllegalStateException e) {
            log.debug("[nacos] 版本接口不可用: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 集群节点响应的数组位置在版本间换过名，逐个尝试。
     *
     * <p>实测（2.4.3）：{@code GET /v1/core/cluster/nodes} 返
     * {@code {"code":200,"message":null,"data":[...]}}，数组在 {@code data} 里；
     * 旧版曾把数组包在 {@code members} 里，1.x 还有直接回顶层数组的形态。
     * 原先只认 {@code members}，导致 2.x 上"集群节点"面板恒为空。</p>
     *
     * @param body 响应体
     * @return 节点数组，取不到时为空数组
     */
    private static JsonArray members(String body) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            return new JsonArray();
        }
        if (text.startsWith("[")) {
            return Json.getJsonArray(text);
        }
        return firstArray(Json.getJsonObject(text), "data", "members");
    }

    /**
     * 依次尝试若干字段名，取第一个真实存在的 JSON 数组。
     *
     * @param json    响应对象
     * @param keyCandidates 候选字段名，按优先级排列
     * @return 命中的数组，全都不存在或不是数组时返回空数组（不返回 null，避免调用方再判空）
     */
    private static JsonArray firstArray(JsonObject json, String... keyCandidates) {
        for (String key : keyCandidates) {
            JsonArray array = json.getJsonArray(key);
            if (array != null && !array.isEmpty()) {
                return array;
            }
        }
        return new JsonArray();
    }

    /**
     * 节点状态：优先服务端字段，其次用健康标记换算。
     *
     * @param node 节点对象
     * @return 大写状态，无法判定时为空串
     */
    private static String statusOf(JsonObject node) {
        String state = firstNonBlank(str(node.get("state")), str(node.get("status")));
        if (!state.isEmpty()) {
            return upper(state);
        }
        Boolean healthy = boolOrNull(firstNonNull(node.get("healthy"), node.get("valid")));
        return healthy == null ? "" : (healthy ? "UP" : "DOWN");
    }

    /**
     * 集群数量：字段可能是逗号串，也可能是数组。
     *
     * @param value 原始值
     * @return 集群数，字段缺失时为 null
     */
    private static Integer countClusters(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "[]".equals(text)) {
            return 0;
        }
        return text.split(",").length;
    }

    /**
     * 配置的更新时间：字段名在版本间有差异，逐个尝试。
     *
     * @param item 配置项
     * @return 可读时间，无字段时为空串
     */
    private static String timeOf(JsonObject item) {
        Object value = firstNonNull(item.get("lastModified"), item.get("gmtModified"), item.get("modifiedTime"));
        Long millis = longOrNull(value);
        return millis == null ? str(value) : MiddlewareNamespace.format(millis);
    }

    /**
     * 校验 Nacos 写接口的返回值。
     *
     * <p>成功标记在模块之间**不一致**（实测 2.4.3）：
     * 配置 {@code POST /v1/cs/configs} 与命名空间 {@code POST /v1/console/namespaces} 回 {@code true}；
     * 而命名模块的 {@code POST|PUT /v1/ns/instance} 回 {@code ok}。
     * 早先只认 {@code true}，导致实例上下线/改权重**明明生效却抛"Nacos 拒绝了该请求: ok"**——
     * 比单纯失败更糟：用户看到失败提示去重试，线上流量权重其实已经改了。</p>
     *
     * @param body 响应体
     * @throws IllegalStateException 服务端明确拒绝
     */
    private static void accepted(String body) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty() || "true".equalsIgnoreCase(text) || "ok".equalsIgnoreCase(text)) {
            return;
        }
        throw new IllegalStateException("Nacos 拒绝了该请求: " + text);
    }

    /**
     * 把 HTTP 日期头换成可读时间。
     *
     * @param header 头原值
     * @return 可读时间，解析不了则原样返回
     */
    private static String httpDate(String header) {
        String text = trim(header);
        if (text.isEmpty()) {
            return "";
        }
        try {
            return MiddlewareNamespace.format(ZonedDateTime.parse(text, HTTP_DATE).toInstant().toEpochMilli());
        } catch (RuntimeException e) {
            return text;
        }
    }

    /**
     * 拆 {@code ip:port} 形式的节点地址。
     *
     * @param address 地址
     * @return 长度为 2 的数组，依次为 ip 与 port 文本
     */
    private static String[] addressParts(String address) {
        String text = trim(address);
        int index = text.lastIndexOf(':');
        if (index <= 0 || index == text.length() - 1) {
            return new String[]{text, ""};
        }
        return new String[]{text.substring(0, index), text.substring(index + 1)};
    }

    /**
     * 分组缺省值。
     *
     * @param group 分组原值
     * @return 去空白后的分组，为空时给 DEFAULT_GROUP
     */
    private static String defaultGroup(String group) {
        String text = trim(group);
        return text.isEmpty() ? DEFAULT_GROUP : text;
    }

    /**
     * 要求字符串字段非空。
     *
     * @param value    原值
     * @param message  为空时的提示
     * @return 去空白后的值
     * @throws IllegalStateException 值为空
     */
    private static String requireText(String value, String message) {
        String text = trim(value);
        if (text.isEmpty()) {
            throw new IllegalStateException(message);
        }
        return text;
    }

    /**
     * 取正整数，否则用默认值。
     *
     * @param value      原值
     * @param defaultVal 默认值
     * @return 正整数
     */
    private static int positive(Integer value, int defaultVal) {
        return value == null || value <= 0 ? defaultVal : value;
    }

    /**
     * 每页条数，超过上限时收敛。
     *
     * @param value 原值
     * @return 不超过上限的每页条数
     */
    private static int pageSize(Integer value) {
        return Math.min(positive(value, DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE);
    }

    /**
     * 宽松取字符串。
     *
     * @param value 原始值
     * @return 文本，null 转空串
     */
    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 宽松取整数。
     *
     * @param value 原始值
     * @return 整数，非数字时为 null
     */
    private static Integer intOrNull(Object value) {
        Long result = longOrNull(value);
        return result == null ? null : result.intValue();
    }

    /**
     * 宽松取双精度数。
     *
     * @param value 原始值
     * @return 双精度数，非数字时为 null
     */
    private static Double doubleOrNull(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = str(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 宽松取长整数，兼容以字符串回传的毫秒时间戳。
     *
     * @param value 原始值
     * @return 长整数，非数字时为 null
     */
    private static Long longOrNull(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = str(value);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 宽松取布尔。
     *
     * @param value 原始值
     * @return 布尔值，无法判定时为 null
     */
    private static Boolean boolOrNull(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = str(value);
        if (text.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * 依次取第一个非 null 值。
     *
     * @param values 候选值
     * @return 第一个非 null，全为 null 时返回 null
     */
    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 依次取第一个非空白字符串。
     *
     * @param values 候选值
     * @return 第一个非空白，全为空白时返回空串
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * 去空白。
     *
     * @param value 原值
     * @return 去空白结果，null 转空串
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 转小写。
     *
     * @param value 原值
     * @return 小写结果
     */
    private static String lower(String value) {
        return trim(value).toLowerCase();
    }

    /**
     * 转大写。
     *
     * @param value 原值
     * @return 大写结果
     */
    private static String upper(String value) {
        return trim(value).toUpperCase();
    }

    /**
     * 空串转 null，让 NON_NULL 序列化时省略该字段。
     *
     * @param value 原值
     * @return 非空原值或 null
     */
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
