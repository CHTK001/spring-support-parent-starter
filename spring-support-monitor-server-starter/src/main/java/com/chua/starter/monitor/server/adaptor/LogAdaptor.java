package com.chua.starter.monitor.server.adaptor;

import com.chua.common.support.json.Json;
import com.chua.common.support.objects.annotation.AutoInject;
import com.chua.redis.support.search.SearchIndex;
import com.chua.redis.support.search.SearchSchema;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.redis.support.service.RedisSearchService;

import java.util.HashMap;
import java.util.Map;

import static com.chua.starter.monitor.server.constant.RedisConstant.REDIS_SEARCH_PREFIX;

/**
 * jvm适配器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/02/01
 */
public class LogAdaptor implements Adaptor<MonitorRequest> {

    @AutoInject
    private SocketSessionTemplate socketSessionTemplate;

    @AutoInject
    private RedisSearchService redisSearchService;

    @Override
    public void doAdaptor(MonitorRequest request) {
        checkIndex(request);
        registerDocument(request);
        socketSessionTemplate.send("log", Json.toJson(request));
    }

    /**
     * 注册文档
     *
     * @param request 请求
     */
    private void registerDocument(MonitorRequest request) {
        Map<String, String> document = new HashMap<>(2);
        document.put("text",  request.getData().toString());
        document.put("timestamp", String.valueOf(request.getTimestamp()));
        redisSearchService.addDocument(REDIS_SEARCH_PREFIX + request.getUid(), document);
    }

    /**
     * 检查索引
     *
     * @param request 请求
     */
    private void checkIndex(MonitorRequest request) {
        SearchIndex searchIndex = new SearchIndex();
        searchIndex.setName(REDIS_SEARCH_PREFIX + request.getUid());
        searchIndex.setLanguage("chinese");
        SearchSchema searchSchema = new SearchSchema();
        searchSchema.addTextField("text", 10);
        searchSchema.addSortableNumericField("timestamp");
        searchIndex.setSchema(searchSchema);
        redisSearchService.createIndex(searchIndex);
    }

    @Override
    public Class<MonitorRequest> getType() {
        return MonitorRequest.class;
    }
}
