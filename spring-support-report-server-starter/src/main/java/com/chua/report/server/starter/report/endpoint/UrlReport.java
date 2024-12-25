package com.chua.report.server.starter.report.endpoint;

import com.chua.common.support.annotations.OnRouterEvent;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.NumberUtils;
import com.chua.redis.support.search.SearchIndex;
import com.chua.redis.support.search.SearchSchema;
import com.chua.report.client.starter.report.event.MappingEvent;
import com.chua.report.client.starter.report.event.ReportEvent;
import com.chua.report.client.starter.setting.ReportExpireSetting;
import com.chua.report.server.starter.service.IptablesService;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.common.support.application.GlobalSettingFactory;
import com.chua.starter.redis.support.service.RedisSearchService;
import com.chua.starter.redis.support.service.SimpleRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringBootConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.chua.redis.support.constant.RedisConstant.REDIS_SEARCH_PREFIX;
import static com.chua.redis.support.constant.RedisConstant.REDIS_SIMPLE_SERIES_PREFIX;

/**
 * url上报
 * @author CH
 * @since 2024/9/20
 */
@SpringBootConfiguration
@RequiredArgsConstructor
public class UrlReport {


    public static final String LOG_NAME = "url";
    public static final String LOG_INDEX_NAME_PREFIX = REDIS_SEARCH_PREFIX + LOG_NAME + ":";
    public static final String LOG_INDEX_NAME_PREFIX2 = REDIS_SIMPLE_SERIES_PREFIX + LOG_NAME + ":";
    private final RedisSearchService redisSearchService;
    private final SimpleRedisService simpleRedisService;
    private final SocketSessionTemplate socketSessionTemplate;
    private final IptablesService iptablesService;
    @OnRouterEvent("url")
    public void report(ReportEvent<?> reportEvent) {
        MappingEvent mappingEvent = BeanUtils.copyProperties(reportEvent.getReportData(), MappingEvent.class);
        ReturnResult<GeoCity> geoCityReturnResult = iptablesService.transferAddress(mappingEvent.getAddress());
        GeoCity geoCity = null;
        if(geoCityReturnResult.isOk()) {
            geoCity = geoCityReturnResult.getData();
            mappingEvent.setLatitude(geoCity.getLongitude());
            mappingEvent.setLongitude(geoCity.getLatitude());
            mappingEvent.setCity(geoCity.getCity());
        }
        registerRedisSearch(mappingEvent, reportEvent);
        // 通过Socket.IO发送JVM事件信息
        reportToSocketIo( mappingEvent, reportEvent);
    }
    /**
     * 通过Socket.IO报告JVM事件
     *
     * @param mappingEvent 包含JVM监控数据的事件对象
     * @param reportEvent  原始报告事件对象
     */
    private void reportToSocketIo(MappingEvent mappingEvent, ReportEvent<?> reportEvent) {
        // 计算事件ID数组
        String[] eventIds = reportEvent.eventIds();
        // 遍历事件ID数组，发送JVM事件信息
        for (String eventId : eventIds) {
            socketSessionTemplate.send(eventId, Json.toJSONString(mappingEvent));
        }
    }
    private void registerRedisSearch(MappingEvent mappingEvent, ReportEvent<?> reportEvent) {
        try {
            registerIndex(reportEvent);
        } catch (Exception ignored) {
        }

        try {
            registerDocument(mappingEvent, reportEvent);
        } catch (Exception ignored) {
        }

        simpleRedisService.incrementDay(LOG_INDEX_NAME_PREFIX2 + reportEvent.clientEventId() ,  mappingEvent.getUrl());
    }

    private void registerDocument(MappingEvent mappingEvent, ReportEvent<?> reportEvent) {

        try {
            Map<String, String> document = new LinkedHashMap<>();
            document.put("text", mappingEvent.getUrl());
            document.put("modelType", "url");
            document.put("method", mappingEvent.getMethod());
            document.put("address", mappingEvent.getAddress());
            document.put("cost", String.valueOf(mappingEvent.getCost()));
            document.put("timestamp", String.valueOf(System.currentTimeMillis()));
            document.put("longitude", null == mappingEvent.getLongitude() ? "0" : String.valueOf(mappingEvent.getLongitude()));
            document.put("city", mappingEvent.getCity());
            document.put("latitude", null == mappingEvent.getLatitude() ? "0" : String.valueOf(mappingEvent.getLatitude()));
            document.put("applicationActive", reportEvent.getApplicationActive());
            document.put("applicationHost", reportEvent.getApplicationHost());
            document.put("applicationPort", String.valueOf(reportEvent.getApplicationPort()));
            document.put("applicationName", reportEvent.getApplicationName());
            ReportExpireSetting expire = GlobalSettingFactory.getInstance().get("expire", ReportExpireSetting.class);
            Long expireTime = expire.getUrl();
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
            searchIndex.setPrefix(LOG_INDEX_NAME_PREFIX + reportEvent.clientEventId());
            searchIndex.setLanguage("chinese");
            SearchSchema searchSchema = new SearchSchema();
            searchSchema.addTextField("text", 10);
            searchSchema.addTextField("type", 10);
            searchSchema.addTextField("modelType", 10);
            searchSchema.addTextField("method", 10);
            searchSchema.addTextField("address", 10);
            searchSchema.addSortableNumericField("cost");
            searchSchema.addTextField("city", 10);
            searchSchema.addTextField("longitude", 10);
            searchSchema.addTextField("latitude", 10);
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
