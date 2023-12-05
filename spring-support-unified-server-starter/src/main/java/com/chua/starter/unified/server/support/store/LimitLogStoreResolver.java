package com.chua.starter.unified.server.support.store;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.starter.unified.server.support.entity.UnifiedLimitLog;
import com.chua.starter.unified.server.support.service.UnifiedLimitLogService;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 限流存储器
 *
 * @author CH
 */
@Spi("limit")
public class LimitLogStoreResolver implements StoreResolver{
    @Resource
    private UnifiedLimitLogService unifiedLimitLogService;

    @Override
    public void resolve(String message, String applicationName) {
        JsonObject jsonObject = Json.fromJson(message, JsonObject.class);
        if(null == jsonObject) {
            return;
        }
        UnifiedLimitLog unifiedLimitLog = new UnifiedLimitLog();
        unifiedLimitLog.setUnifiedLimitLogAppname(applicationName);
        unifiedLimitLog.setCreateTime(new Date());
        unifiedLimitLog.setUnifiedLimitLogRequestAddress(jsonObject.getString("requestAddress"));
        unifiedLimitLog.setUnifiedLimitLogRequestUrl(jsonObject.getString("requestUrl"));
        unifiedLimitLogService.save(unifiedLimitLog);

    }
}
