package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.utils.NumberUtils;
import com.chua.redis.support.search.SearchIndex;
import com.chua.redis.support.search.SearchSchema;
import com.chua.report.client.starter.report.event.MappingEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.report.event.TraceEvent;
import com.chua.report.client.starter.setting.ReportExpireSetting;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import com.chua.starter.common.support.project.Project;
import com.chua.starter.redis.support.service.RedisSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

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

    @OnRouterEvent("trace")
    public void report(ReportEvent<?> reportEvent) {
        TraceEvent traceEvent = BeanUtils.copyProperties(reportEvent.getReportData(), TraceEvent.class);

    }

    private void registerRedisSearch(MappingEvent mappingEvent, ReportEvent<?> reportEvent) {
        try {
            registerIndex();
        } catch (Exception ignored) {
        }

        try {
            registerDocument(mappingEvent, reportEvent);
        } catch (Exception ignored) {
        }
    }

    private void registerDocument(MappingEvent mappingEvent, ReportEvent<?> reportEvent) {

        try {
            Map<String, String> document = new LinkedHashMap<>();
            document.put("text", mappingEvent.getUrl());
            document.put("type", "url");
            document.put("method", mappingEvent.getMethod());
            document.put("address", mappingEvent.getAddress());
            document.put("cost", String.valueOf(mappingEvent.getCost()));
            document.put("timestamp", String.valueOf(System.currentTimeMillis()));
            document.put("applicationActive", reportEvent.getApplicationActive());
            document.put("applicationHost", reportEvent.getApplicationHost());
            document.put("applicationPort", String.valueOf(reportEvent.getApplicationPort()));
            document.put("applicationName", reportEvent.getApplicationName());
            ReportExpireSetting expire = GlobalSettingFactory.getInstance().get("expire", ReportExpireSetting.class);
            document.put("expire", String.valueOf(NumberUtils.defaultIfNullOrPositive(expire.getUrl(), 86400 * 30)));
            redisSearchService.addDocument(LOG_INDEX_NAME_PREFIX + Project.getInstance().calcApplicationUuid(), document);
        } catch (Exception ignored) {
        }
    }

    private void registerIndex() {
        try {
            SearchIndex searchIndex = new SearchIndex();
            searchIndex.setName(LOG_INDEX_NAME_PREFIX + Project.getInstance().calcApplicationUuid());
            searchIndex.setLanguage("chinese");
            SearchSchema searchSchema = new SearchSchema();
            searchSchema.addTextField("text", 10);
            searchSchema.addTextField("type", 10);
            searchSchema.addTextField("method", 10);
            searchSchema.addTextField("address", 10);
            searchSchema.addSortableNumericField("cost");
            searchSchema.addTextField("applicationName", 1);
            searchSchema.addTextField("applicationPort", 1);
            searchSchema.addTextField("applicationHost", 1);
            searchSchema.addTextField("applicationActive", 1);
            searchSchema.addSortableNumericField("timestamp");
            searchIndex.setSchema(searchSchema);
            redisSearchService.createIndex(searchIndex);
        } catch (Exception ignored) {
        }
    }
}
