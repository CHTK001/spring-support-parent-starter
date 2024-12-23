package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.redis.support.search.SearchIndex;
import com.chua.redis.support.search.SearchSchema;
import com.chua.report.client.starter.report.event.LogEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.setting.ReportExpireSetting;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import com.chua.starter.redis.support.service.RedisSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;

import java.util.HashMap;
import java.util.Map;

import static com.chua.common.support.constant.CommonConstant.EMPTY;
import static com.chua.redis.support.constant.RedisConstant.IDX_PREFIX;
import static com.chua.redis.support.constant.RedisConstant.REDIS_SEARCH_PREFIX;

/**
 * 日志上报
 * @author CH
 * @since 2024/9/13
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class LogReport {

    public static final String LOG_NAME = "log";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_SEARCH_PREFIX + LOG_NAME + ":";
    private final RedisSearchService redisSearchService;
    private final SocketSessionTemplate socketSessionTemplate;


    @OnRouterEvent("log")
    public void report(ReportEvent<?> reportEvent) {
        LogEvent logEvent = BeanUtils.copyProperties(reportEvent.getReportData(), LogEvent.class);
//        registerRedisSearch(logEvent, reportEvent);
        reportToSocketIo(logEvent, reportEvent);
    }

    /**
     * 发送消息
     *
     * @param logEvent    日志信息
     * @param reportEvent
     */
    private void reportToSocketIo(LogEvent logEvent, ReportEvent<?> reportEvent) {
        String[] eventIds = reportEvent.eventIds();
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(logEvent));
        }
    }


    private void registerRedisSearch(LogEvent logEvent, ReportEvent<?> reportEvent) {
        try {
            registerIndex(reportEvent);
        } catch (Exception ignored) {
        }

        try {
            registerDocument(logEvent, reportEvent);
        } catch (Exception ignored) {
        }
    }

    /**
     * 注册文档
     *
     * @param logEvent    日志信息
     * @param reportEvent
     */
    private void registerDocument(LogEvent logEvent, ReportEvent<?> reportEvent) {
        Map<String, String> document = new HashMap<>(3);
        document.put("text", logEvent.getMessage());
        document.put("modelType", "log");
        document.put("timestamp", String.valueOf(logEvent.getTimestamp()));
        document.put("level", logEvent.getLevel());
        document.put("traceId", StringUtils.defaultString(logEvent.getTraceId() , EMPTY));
        document.put("className", logEvent.getClassName());
        document.put("applicationActive", reportEvent.getApplicationActive());
        document.put("applicationHost", reportEvent.getApplicationHost());
        document.put("applicationPort", String.valueOf(reportEvent.getApplicationPort()));
        document.put("applicationName", reportEvent.getApplicationName());
        ReportExpireSetting expire = GlobalSettingFactory.getInstance().get("expire", ReportExpireSetting.class);
        Long expireTime = expire.getLog();
        if(null == expireTime || expireTime <=0 ) {
            return;
        }
        document.put("expire", String.valueOf(NumberUtils.defaultIfNullOrPositive(expireTime, 86400 * 7)));
        redisSearchService.addDocument(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId(), document);
    }

    /**
     * 注册索引
     */
    private void registerIndex(ReportEvent<?> reportEvent) {
        SearchIndex searchIndex = new SearchIndex();
        searchIndex.setName(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId());
        searchIndex.setPrefix(reportEvent.clientEventId() + ":");
        SearchSchema searchSchema = new SearchSchema();
        searchSchema.addTextField("text", 10);
        searchSchema.addTextField("modelType", 10);
        searchSchema.addTextField("level", 1);
        searchSchema.addTextField("className", 1);
        searchSchema.addTextField("traceId", 10);
        searchSchema.addTextField("applicationName", 1);
        searchSchema.addTextField("applicationPort", 1);
        searchSchema.addTextField("applicationHost", 1);
        searchSchema.addTextField("applicationActive", 1);
        searchSchema.addSortableNumericField("timestamp");
        searchIndex.setSchema(searchSchema);
        redisSearchService.createIndex(searchIndex);
    }
}
