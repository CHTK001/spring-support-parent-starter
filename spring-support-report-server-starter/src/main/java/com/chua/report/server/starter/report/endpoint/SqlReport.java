package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.utils.NumberUtils;
import com.chua.redis.support.search.SearchIndex;
import com.chua.redis.support.search.SearchSchema;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.report.event.SqlEvent;
import com.chua.report.client.starter.setting.ReportExpireSetting;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import com.chua.starter.common.support.project.Project;
import com.chua.starter.redis.support.service.RedisSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.chua.redis.support.constant.RedisConstant.IDX_PREFIX;
import static com.chua.redis.support.constant.RedisConstant.REDIS_SEARCH_PREFIX;

/**
 * sql上报
 *
 * @author CH
 * @since 2024/9/13
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class SqlReport {

    public static final String LOG_NAME = "sql";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_SEARCH_PREFIX + LOG_NAME + ":";
    private final RedisSearchService redisSearchService;

    @OnRouterEvent("sql")
    public void report(ReportEvent<?> reportEvent) {
        SqlEvent sqlEvent = BeanUtils.copyProperties(reportEvent.getReportData(), SqlEvent.class);
        registerRedisSearch(sqlEvent, reportEvent);

    }

    private void registerRedisSearch(SqlEvent sqlEvent, ReportEvent<?> reportEvent) {
        try {
            registerIndex();
        } catch (Exception ignored) {
        }

        try {
            registerDocument(sqlEvent, reportEvent);
        } catch (Exception ignored) {
        }
    }

    private void registerDocument(SqlEvent sqlEvent, ReportEvent<?> reportEvent) {

        try {
            Map<String, String> document = new LinkedHashMap<>();
            document.put("text", sqlEvent.getSql());
            document.put("type", "sql");
            document.put("event", sqlEvent.getEvent());
            document.put("className", sqlEvent.getClassName());
            document.put("thread", sqlEvent.getThread());
            document.put("timestamp", String.valueOf(System.currentTimeMillis()));
            document.put("applicationActive", reportEvent.getApplicationActive());
            document.put("applicationHost", reportEvent.getApplicationHost());
            document.put("applicationPort", String.valueOf(reportEvent.getApplicationPort()));
            document.put("applicationName", reportEvent.getApplicationName());
            ReportExpireSetting expire = GlobalSettingFactory.getInstance().get("expire", ReportExpireSetting.class);
            Long expireTime = expire.getSql();
            if(null == expireTime || expireTime <=0 ) {
                return;
            }
            document.put("expire", String.valueOf(NumberUtils.defaultIfNullOrPositive(expireTime, 86400 * 30)));
            redisSearchService.addDocument(LOG_INDEX_NAME_PREFIX + Project.getInstance().calcApplicationUuid(), document);
        } catch (Exception ignored) {
        }
    }

    private void registerIndex() {
        try {
            SearchIndex searchIndex = new SearchIndex();
            searchIndex.setName(LOG_INDEX_NAME_PREFIX + Project.getInstance().calcApplicationUuid());
            SearchSchema searchSchema = new SearchSchema();
            searchSchema.addTextField("text", 10);
            searchSchema.addTextField("event", 10);
            searchSchema.addTextField("modelType", 10);
            searchSchema.addTextField("className", 10);
            searchSchema.addTextField("thread", 10);
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
