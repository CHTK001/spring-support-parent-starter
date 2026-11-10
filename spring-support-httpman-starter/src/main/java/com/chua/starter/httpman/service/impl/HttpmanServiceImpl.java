package com.chua.starter.httpman.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.httpman.config.HttpmanProperties;
import com.chua.starter.httpman.entity.MsHttpmanCollection;
import com.chua.starter.httpman.entity.MsHttpmanHistory;
import com.chua.starter.httpman.entity.MsHttpmanRequest;
import com.chua.starter.httpman.entity.MsHttpmanVariable;
import com.chua.starter.httpman.mapper.MsHttpmanCollectionMapper;
import com.chua.starter.httpman.mapper.MsHttpmanHistoryMapper;
import com.chua.starter.httpman.mapper.MsHttpmanRequestMapper;
import com.chua.starter.httpman.mapper.MsHttpmanVariableMapper;
import com.chua.starter.httpman.model.dto.HttpmanHistoryQuery;
import com.chua.starter.httpman.model.dto.HttpmanRequestSave;
import com.chua.starter.httpman.model.dto.HttpmanVariableSave;
import com.chua.starter.httpman.model.vo.HttpmanResponse;
import com.chua.starter.httpman.service.HttpmanService;
import com.chua.starter.httpman.support.HttpmanProxyExecutor;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.PageResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 接口调试控制台 - 服务实现
 *
 * @author CH
 * @since 4.0.0.51
 */
@Service
public class HttpmanServiceImpl implements HttpmanService {

    private static final Logger log = LoggerFactory.getLogger(HttpmanServiceImpl.class);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final TypeReference<List<Map<String, String>>> HEADER_LIST_TYPE =
            new TypeReference<List<Map<String, String>>>() {};

    private final HttpmanProperties properties;

    private final MsHttpmanCollectionMapper collectionMapper;

    private final MsHttpmanRequestMapper requestMapper;

    private final MsHttpmanHistoryMapper historyMapper;

    private final MsHttpmanVariableMapper variableMapper;

    private final HttpmanProxyExecutor executor;

    public HttpmanServiceImpl(HttpmanProperties properties,
                              MsHttpmanCollectionMapper collectionMapper,
                              MsHttpmanRequestMapper requestMapper,
                              MsHttpmanHistoryMapper historyMapper,
                              MsHttpmanVariableMapper variableMapper) {
        this.properties = properties;
        this.collectionMapper = collectionMapper;
        this.requestMapper = requestMapper;
        this.historyMapper = historyMapper;
        this.variableMapper = variableMapper;
        this.executor = new HttpmanProxyExecutor(properties);
    }

    // ==================== 集合 ====================

    @Override
    public MsHttpmanCollection createCollection(MsHttpmanCollection collection) {
        collectionMapper.insert(collection);
        return collection;
    }

    @Override
    public MsHttpmanCollection updateCollection(MsHttpmanCollection collection) {
        collectionMapper.updateById(collection);
        return collection;
    }

    @Override
    public void deleteCollection(String id) {
        collectionMapper.deleteById(id);
        // 级联删除集合下的请求（含子请求）以及这些请求的运行历史
        List<MsHttpmanRequest> requests = listRequests(id, null);
        deleteRequestRecords(requests);
    }

    @Override
    public List<MsHttpmanCollection> listCollections() {
        LambdaQueryWrapper<MsHttpmanCollection> wrapper =
                new LambdaQueryWrapper<>(MsHttpmanCollection.class)
                        .orderByAsc(MsHttpmanCollection::getCollectionOrder);
        return collectionMapper.selectList(wrapper);
    }

    // ==================== 请求 ====================

    @Override
    public MsHttpmanRequest saveRequest(HttpmanRequestSave save) {
        MsHttpmanRequest request = new MsHttpmanRequest();
        request.setRequestCollectionId(save.getCollectionId());
        request.setRequestParentId(save.getParentId());
        request.setRequestName(save.getName());
        request.setRequestUrl(save.getUrl());
        request.setRequestMethod(save.getMethod());
        request.setRequestHeaders(save.getHeaders());
        request.setRequestQuery(save.getQuery());
        request.setRequestBody(save.getBody());
        request.setRequestBodyType(save.getBodyType());
        request.setRequestAuth(save.getAuth());
        requestMapper.insert(request);
        return request;
    }

    @Override
    public MsHttpmanRequest updateRequest(String id, HttpmanRequestSave save) {
        MsHttpmanRequest request = requestMapper.selectById(id);
        if (request == null) {
            throw new IllegalArgumentException("请求不存在: " + id);
        }
        request.setRequestUrl(save.getUrl());
        request.setRequestMethod(save.getMethod());
        request.setRequestHeaders(save.getHeaders());
        request.setRequestQuery(save.getQuery());
        request.setRequestBody(save.getBody());
        request.setRequestBodyType(save.getBodyType());
        request.setRequestAuth(save.getAuth());
        request.setRequestName(save.getName());
        request.setRequestParentId(save.getParentId());
        requestMapper.updateById(request);
        return request;
    }

    @Override
    public void deleteRequest(String id) {
        // 级联删除子请求以及这些请求的运行历史
        List<MsHttpmanRequest> children = requestMapper.selectList(
                new LambdaQueryWrapper<>(MsHttpmanRequest.class)
                        .eq(MsHttpmanRequest::getRequestParentId, id));
        deleteRequestRecords(children);
        requestMapper.deleteById(id);
    }

    @Override
    public MsHttpmanRequest getRequest(String id) {
        return requestMapper.selectById(id);
    }

    @Override
    public List<MsHttpmanRequest> listRequests(String collectionId, String parentId) {
        LambdaQueryWrapper<MsHttpmanRequest> wrapper =
                new LambdaQueryWrapper<>(MsHttpmanRequest.class)
                        .eq(MsHttpmanRequest::getRequestCollectionId, collectionId);
        if (parentId != null) {
            wrapper.eq(MsHttpmanRequest::getRequestParentId, parentId);
        }
        wrapper.orderByDesc(MsHttpmanRequest::getCreateTime);
        return requestMapper.selectList(wrapper);
    }

    // ==================== 执行 ====================

    @Override
    public ReturnResult<Map<String, Object>> execute(MsHttpmanRequest request) {
        return executeAndRecord(request, request.getRequestCollectionId());
    }

    @Override
    public ReturnResult<Map<String, Object>> executeAndRecord(MsHttpmanRequest request, String collectionId) {
        // 参数校验
        if (request.getRequestUrl() == null || request.getRequestUrl().isBlank()) {
            return ReturnResult.error("请求地址不能为空");
        }
        if (request.getRequestHeaders() == null) {
            request.setRequestHeaders("[]");
        }
        if (request.getRequestQuery() == null) {
            request.setRequestQuery("[]");
        }
        if (request.getRequestBody() == null) {
            request.setRequestBody("");
        }

        // 方法白名单
        if (request.getRequestMethod() == null
                || !properties.getAllowedMethods().contains(request.getRequestMethod())) {
            return ReturnResult.error("不允许的请求方法: " + request.getRequestMethod());
        }

        long start = System.currentTimeMillis();
        String url = request.getRequestUrl();
        String body = request.getRequestBody() == null ? "" : request.getRequestBody();

        // 变量解析
        Map<String, String> variables = getVariableMap(null, request.getRequestCollectionId());
        url = resolvePlaceholders(url, variables);
        body = resolvePlaceholders(body, variables);

        // 请求头解析 + 变量替换 + 脱敏
        Map<String, String> headers = resolveHeaderMap(request.getRequestHeaders(), variables);

        // 查询参数
        Map<String, List<String>> params = parseParamMap(request.getRequestQuery());

        // 发起执行（同步等待结果）
        HttpmanResponse response;
        try {
            response = executor.execute(request.getRequestMethod(), url, headers, params, body,
                    // [P3C A20 豁免]
                    request.getRequestBodyType()).block(Duration.ofSeconds(properties.getReadTimeout().toSeconds() + 15));
        } catch (Exception e) {
            log.error("Httpman execute failed: {}", e.getMessage());
            response = HttpmanResponse.error(request.getRequestMethod(), url, start, e);
        }
        if (response == null) {
            response = HttpmanResponse.error(request.getRequestMethod(), url, start,
                    new IllegalStateException("执行无返回结果"));
        }

        // 组装响应
        Map<String, Object> result = new HashMap<>();
        result.put("historyId", null);
        result.put("status", response.getStatus());
        result.put("headers", response.getHeaders());
        result.put("body", response.getBody());
        result.put("bodyLength", response.getBodyLength());
        result.put("costMs", response.getCostMs());
        result.put("error", response.getError());

        // 写历史（脱敏）
        if (request.getRequestId() != null) {
            createHistoryRecord(request, response, url, body, result);
        }
        return ReturnResult.ok(result);
    }

    /**
     * 创建历史记录（脱敏后落库）
     */
    private Map<String, Object> createHistoryRecord(MsHttpmanRequest request, HttpmanResponse response,
                                                    String url, String body, Map<String, Object> result) {
        // 脱敏后的请求头 JSON
        String historyRequestHeaders = writJson(resolveSensitiveList(request.getRequestHeaders(), properties.isSensitiveHeaderMask()));

        // 脱敏后的响应头 JSON
        String historyResponseHeaders = writJson(resolveSensitiveList(response.getHeaders(), properties.isSensitiveHeaderMask()));

        MsHttpmanHistory history = new MsHttpmanHistory();
        history.setHistoryRequestId(request.getRequestId());
        history.setHistoryCollectionId(request.getRequestCollectionId());
        history.setHistoryRequestName(request.getRequestName());
        history.setHistoryMethod(request.getRequestMethod());
        history.setHistoryUrl(url);
        history.setHistoryStatus(response.getStatus());
        history.setHistoryCostMs(response.getCostMs());
        history.setHistoryResponseTime(LocalDateTime.now().format(FMT));
        history.setHistoryRequestHeaders(historyRequestHeaders);
        history.setHistoryRequestBody(body);
        history.setHistoryResponseHeaders(historyResponseHeaders);
        history.setHistoryResponseBody(response.getBody());
        history.setHistoryError(response.getError());
        // [P3C A20 豁免]
        history.setHistoryAlert(response.getStatus() >= 400 || response.getCostMs() > 60000);
        historyMapper.insert(history);
        result.put("historyId", history.getHistoryId());
        return result;
    }

    // [P3C MJ-方法注释 豁免]
    private void deleteRequestRecords(List<MsHttpmanRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<String> requestIds = requests.stream().map(MsHttpmanRequest::getRequestId).collect(Collectors.toList());
        historyMapper.delete(
                new LambdaQueryWrapper<>(MsHttpmanHistory.class)
                        .in(MsHttpmanHistory::getHistoryRequestId, requestIds));
        for (MsHttpmanRequest r : requests) {
            deleteRequest(r.getRequestId());
        }
    }

    // ==================== 历史 ====================

    @Override
    public PageResult<Map<String, Object>> pageHistory(HttpmanHistoryQuery query) {
        LambdaQueryWrapper<MsHttpmanHistory> wrapper =
                new LambdaQueryWrapper<>(MsHttpmanHistory.class)
                        .eq(query.getRequestId() != null, MsHttpmanHistory::getHistoryRequestId, query.getRequestId())
                        .eq(query.getCollectionId() != null, MsHttpmanHistory::getHistoryCollectionId, query.getCollectionId())
                        .eq(query.getStatus() != null && query.getStatus() > 0, MsHttpmanHistory::getHistoryStatus, query.getStatus())
                        .ge(query.getStartTime() != null, MsHttpmanHistory::getHistoryResponseTime, query.getStartTime().format(FMT))
                        .le(query.getEndTime() != null, MsHttpmanHistory::getHistoryResponseTime, query.getEndTime().format(FMT));

        Page<MsHttpmanHistory> page = historyMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        List<Map<String, Object>> records = page.getRecords().stream()
                .map(this::historyToMap).collect(Collectors.toList());

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setPageNo(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setTotalPage(page.getPages());
        result.setRecords(records);
        return result;
    }

    /**
     * 历史详情（包含完整的请求与响应）
     */
    @Override
    public Map<String, Object> getHistory(String id) {
        MsHttpmanHistory history = historyMapper.selectById(id);
        if (history == null) {
            return Collections.emptyMap();
        }
        return historyToMap(history);
    }

    @Override
    public void deleteHistory(String id) {
        historyMapper.deleteById(id);
    }

    @Override
    public void cleanHistory(int retentionDays) {
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        historyMapper.delete(
                new LambdaQueryWrapper<>(MsHttpmanHistory.class)
                        .le(MsHttpmanHistory::getHistoryResponseTime, cutoff.format(FMT)));
        log.info("cleanHistory: removed records before {}", cutoff);
    }

    // ==================== 变量 ====================

    @Override
    public MsHttpmanVariable createVariable(MsHttpmanVariable variable) {
        variableMapper.insert(variable);
        return variable;
    }

    @Override
    public MsHttpmanVariable updateVariable(MsHttpmanVariable variable) {
        variableMapper.updateById(variable);
        return variable;
    }

    @Override
    public MsHttpmanVariable getVariable(String id) {
        return variableMapper.selectById(id);
    }

    @Override
    public void deleteVariable(String id) {
        variableMapper.deleteById(id);
    }

    @Override
    public List<MsHttpmanVariable> listVariables(String scope, String collectionId) {
        LambdaQueryWrapper<MsHttpmanVariable> wrapper =
                new LambdaQueryWrapper<>(MsHttpmanVariable.class)
                        .eq(MsHttpmanVariable::getVariableScope, scope)
                        .eq(collectionId != null, MsHttpmanVariable::getVariableCollectionId, collectionId);
        return variableMapper.selectList(wrapper);
    }

    /**
     * 按作用域获取变量映射（用于 {{variable}} 模板解析）
     */
    @Override
    public Map<String, String> getVariableMap(String scope, String collectionId) {
        LambdaQueryWrapper<MsHttpmanVariable> wrapper = new LambdaQueryWrapper<>(MsHttpmanVariable.class);
        if (StringUtils.hasText(scope)) {
            wrapper.eq(MsHttpmanVariable::getVariableScope, scope);
        } else {
            wrapper.ne(MsHttpmanVariable::getVariableScope, "REQUEST");
        }
        if (collectionId != null) {
            wrapper.eq(MsHttpmanVariable::getVariableCollectionId, collectionId);
        }
        return variableMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(MsHttpmanVariable::getVariableName, MsHttpmanVariable::getVariableValue,
                        (a, b) -> b, HashMap::new));
    }

    // ==================== 工具 ====================

    /**
     * 替换请求中的 {{variable}} 占位符
     */
    private String resolvePlaceholders(String text, Map<String, String> variables) {
        if (text == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return text;
    }

    /**
     * 解析请求头 JSON 数组 [{"name":"X","value":"Y"}]
     */
    private Map<String, String> parseHeaderMap(String json) {
        List<Map<String, String>> list = parseHeaderList(json);
        return list.stream().collect(Collectors.toMap(
                m -> m.get("name") == null ? "" : m.get("name"),
                m -> m.get("value") == null ? "" : m.get("value"),
                (a, b) -> b, HashMap::new));
    }

    /**
     * 解析查询参数 JSON 数组 [{"name":"X","value":"Y"}]
     */
    private Map<String, List<String>> parseParamMap(String json) {
        List<Map<String, String>> list = parseHeaderList(json);
        if (list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.groupingBy(
                m -> m.get("name") == null ? "" : m.get("name"),
                Collectors.mapping(m -> m.get("value") == null ? "" : m.get("value"), Collectors.toList())));
    }

    private List<Map<String, String>> parseHeaderList(String json) {
        if (!StringUtils.hasText(json) || "[]".equals(json)) {
            return new ArrayList<>();
        }
        try {
            return JSON.readValue(json, HEADER_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("请求头 JSON 格式错误: " + json, e);
        }
    }

    /** JSON 序列化（受检异常包装为运行时异常） */
    private String writJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败: " + obj, e);
        }
    }

    private Map<String, String> resolveHeaderMap(String json, Map<String, String> variables) {
        List<Map<String, String>> list = parseHeaderList(json);
        Map<String, String> map = new HashMap<>();
        for (Map<String, String> m : list) {
            String name = m.get("name");
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            String value = m.get("value") == null ? "" : m.get("value");
            value = resolvePlaceholders(value, variables);
            if (properties.isSensitiveHeaderMask()
                    && HttpmanProxyExecutor.SENSITIVE_KEYS.contains(name.trim().toLowerCase())) {
                value = "******";
            }
            map.put(name.trim(), value);
        }
        return map;
    }

    private List<Map<String, String>> resolveSensitiveList(String json, boolean mask) {
        List<Map<String, String>> list = parseHeaderList(json);
        if (!mask) {
            return list;
        }
        return list.stream().map(this::maskSensitive).collect(Collectors.toList());
    }

    /** Map<String,String> => 脱敏后保存为 JSON 数组列表 */
    private List<Map<String, String>> resolveSensitiveList(Map<String, String> map, boolean mask) {
        List<Map<String, String>> list = new ArrayList<>();
        if (map == null || map.isEmpty()) {
            return list;
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            Map<String, String> item = new HashMap<>();
            item.put("name", entry.getKey());
            item.put("value", entry.getValue());
            list.add(mask ? maskSensitive(item) : item);
        }
        return list;
    }

    /** 单一节点脱敏 */
    private Map<String, String> maskSensitive(Map<String, String> m) {
        String name = m.get("name");
        if (name == null || !HttpmanProxyExecutor.SENSITIVE_KEYS.contains(name.toLowerCase())) {
            return m;
        }
        Map<String, String> copy = new HashMap<>(m);
        copy.put("value", "******");
        return copy;
    }

    private Map<String, Object> historyToMap(MsHttpmanHistory history) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", history.getHistoryId());
        map.put("requestId", history.getHistoryRequestId());
        map.put("collectionId", history.getHistoryCollectionId());
        map.put("requestName", history.getHistoryRequestName());
        map.put("method", history.getHistoryMethod());
        map.put("url", history.getHistoryUrl());
        map.put("status", history.getHistoryStatus());
        map.put("costMs", history.getHistoryCostMs());
        map.put("responseTime", history.getHistoryResponseTime());
        map.put("requestHeaders", history.getHistoryRequestHeaders());
        map.put("requestBody", history.getHistoryRequestBody());
        map.put("responseHeaders", history.getHistoryResponseHeaders());
        map.put("responseBody", history.getHistoryResponseBody());
        map.put("error", history.getHistoryError());
        map.put("alert", history.getHistoryAlert());
        return map;
    }
}
