package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.function.Joiner;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.redis.support.search.SearchIndex;
import com.chua.redis.support.search.SearchSchema;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.setting.ReportExpireSetting;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import com.chua.starter.common.support.watch.Span;
import com.chua.starter.redis.support.service.RedisSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.chua.redis.support.constant.RedisConstant.IDX_PREFIX;
import static com.chua.redis.support.constant.RedisConstant.REDIS_SEARCH_PREFIX;

/**
 * url上报
 * @author CH
 * @since 2024/9/20
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class TraceReport {


    public static final String LOG_NAME = "trace";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_SEARCH_PREFIX + LOG_NAME + ":";
    private final RedisSearchService redisSearchService;
    private final SocketSessionTemplate socketSessionTemplate;

    @OnRouterEvent("trace")
    public void report(ReportEvent<?> reportEvent) {
        Span span = Json.fromJson(reportEvent.getReportData().toString(), Span.class);
        registerRedisSearch(span, reportEvent);
        reportToSocketIo(span, reportEvent);
    }

    private void reportToSocketIo(Span span, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送SYS事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(span));
        }
    }

    private void registerRedisSearch(Span span, ReportEvent<?> reportEvent) {
        try {
            registerIndex(reportEvent);
        } catch (Exception ignored) {
        }

        try {
            registerDocument(span, reportEvent);
        } catch (Exception ignored) {
        }
    }

    private void registerDocument(Span span, ReportEvent<?> reportEvent) {

        try {
            Map<String, String> document = new LinkedHashMap<>();
            document.put("modelType", "trace");
            document.put("linkId", StringUtils.defaultString( span.getLinkId(), ""));
            document.put("id", StringUtils.defaultString(span.getId(), ""));
            document.put("pid", StringUtils.defaultString(span.getPid(), ""));
            document.put("enterTime", String.valueOf(span.getEnterTime()));
            document.put("endTime", String.valueOf(span.getEndTime()));
            document.put("costTime", String.valueOf(span.getCostTime()));
            document.put("message", String.valueOf(span.getMessage()));
            document.put("stack", Joiner.on("\r\n").join(span.getStack()));
            document.put("header", Joiner.on("\r\n").join(span.getHeader()));
            document.put("method", span.getMethod());
            document.put("model", span.getModel());
            document.put("typeMethod", StringUtils.defaultString(span.getTypeMethod(), ""));
            document.put("type", span.getType());
            document.put("args", Joiner.on("\r\n").join(span.getArgs()));
            document.put("ex",StringUtils.defaultString(span.getEx(), ""));
            document.put("error", StringUtils.defaultString(span.getError(), ""));
            document.put("title", StringUtils.defaultString(String.valueOf(span.isTitle()), ""));
            document.put("db", StringUtils.defaultString(span.getDb(), ""));
            document.put("threadName", StringUtils.defaultString(span.getThreadName(), ""));
            document.put("from", StringUtils.defaultString(span.getFrom(), ""));
            document.put("children", Json.toJson(span.getChildren()));
            document.put("timestamp", String.valueOf(reportEvent.getTimestamp()));
            document.put("applicationActive", reportEvent.getApplicationActive());
            document.put("applicationHost", reportEvent.getApplicationHost());
            document.put("applicationPort", String.valueOf(reportEvent.getApplicationPort()));
            document.put("applicationName", reportEvent.getApplicationName());
            ReportExpireSetting expire = GlobalSettingFactory.getInstance().get("expire", ReportExpireSetting.class);
            Long expireTime = expire.getTrace();
            if(null == expireTime || expireTime <=0 ) {
                return;
            }
            document.put("expire", String.valueOf(NumberUtils.defaultIfNullOrPositive(expireTime, 86400 * 30)));
            redisSearchService.addDocument(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId(), document);
        } catch (Exception ignored) {
        }
    }

    private void registerIndex(ReportEvent<?> reportEvent) {
        try {
            SearchIndex searchIndex = new SearchIndex();
            searchIndex.setName(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId());
            searchIndex.setPrefix(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId() );
            searchIndex.setLanguage("chinese");
            SearchSchema searchSchema = new SearchSchema();
            searchSchema.addTextField("linkId", 10);
            searchSchema.addTextField("id", 10);
            searchSchema.addTextField("model", 10);
            searchSchema.addTextField("modelType", 10);
            searchSchema.addTextField("pid", 10);
            searchSchema.addSortableNumericField("enterTime");
            searchSchema.addSortableNumericField("endTime");
            searchSchema.addSortableNumericField("costTime");
            searchSchema.addTextField("message", 10);
            searchSchema.addTextField("stack", 10);
            searchSchema.addTextField("header", 10);
            searchSchema.addTextField("method", 1);
            searchSchema.addTextField("typeMethod", 1);
            searchSchema.addTextField("title", 1);
            searchSchema.addTextField("type", 1);
            searchSchema.addTextField("args", 1);
            searchSchema.addTextField("ex", 1);
            searchSchema.addTextField("error", 1);
            searchSchema.addTextField("db", 1);
            searchSchema.addTextField("threadName", 1);
            searchSchema.addTextField("from", 1);
            searchSchema.addTextField("children", 1);
            searchSchema.addSortableNumericField("timestamp");
            searchSchema.addTextField("applicationName", 1);
            searchSchema.addTextField("applicationPort", 1);
            searchSchema.addTextField("applicationHost", 1);
            searchSchema.addTextField("applicationActive", 1);
            searchIndex.setSchema(searchSchema);
            redisSearchService.createIndex(searchIndex);
        } catch (Exception ignored) {
        }
    }
}
