package com.chua.starter.redis.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.indicator.TimeIndicator;
import com.chua.redis.support.client.RedisClient;
import com.chua.redis.support.client.RedisSession;
import com.chua.redis.support.client.RedisTimeSeries;
import com.chua.starter.redis.support.service.TimeSeriesService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import redis.clients.jedis.timeseries.TSCreateParams;
import redis.clients.jedis.timeseries.TSElement;
import redis.clients.jedis.timeseries.TSRangeParams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 时间序列服务的实现类。
 * 提供关于时间序列数据的增、删、改、查等操作的实现。
 *
 * @author CH
 * @since 2024/7/4
 */
@Service
public class TimeSeriesServiceImpl implements TimeSeriesService {


    @Resource
    private RedisClient redisClient;
    @Override
    public ReturnResult<Boolean> save(String indicator, long timestamp, double value, LinkedHashMap<String, String> label, long retentionPeriod) {
        RedisSession redisSession  = (RedisSession) redisClient.getSession();

        if(!redisSession.checkModule("timeseries")) {
            return ReturnResult.error("模块未加载");
        }

        RedisTimeSeries redisTimeSeries = ((RedisSession) redisClient.getSession()).getRedisTimeSeries();
        TSElement tsElement = redisTimeSeries.tsGet(indicator);
        if(null == tsElement) {
            redisTimeSeries.tsCreate(indicator);
        }
        TSCreateParams tsCreateParams = new TSCreateParams();
        tsCreateParams.labels(label);
        tsCreateParams.compressed();
        if(retentionPeriod > 0) {
            tsCreateParams.retention(retentionPeriod);
        }
        redisTimeSeries.tsAdd(indicator, timestamp, value, tsCreateParams);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<Boolean> save(String indicator, long timestamp, double value, long retentionPeriod) {
        RedisSession redisSession  = (RedisSession) redisClient.getSession();

        if(!redisSession.checkModule("timeseries")) {
            return ReturnResult.error("模块未加载");
        }

        RedisTimeSeries redisTimeSeries = ((RedisSession) redisClient.getSession()).getRedisTimeSeries();
        TSElement tsElement = redisTimeSeries.tsGet(indicator);
        if(null == tsElement) {
            redisTimeSeries.tsCreate(indicator);
        }
        TSCreateParams tsCreateParams = new TSCreateParams();
        if(retentionPeriod > 0) {
            tsCreateParams.retention(retentionPeriod);
        }
        tsCreateParams.compressed();
        redisTimeSeries.tsAdd(indicator, timestamp, value, tsCreateParams);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<Boolean> delete(String indicator, long fromTimestamp, long toTimestamp) {
        RedisSession redisSession  = (RedisSession) redisClient.getSession();

        if(!redisSession.checkModule("timeseries")) {
            return ReturnResult.error("模块未加载");
        }

        RedisTimeSeries redisTimeSeries = ((RedisSession) redisClient.getSession()).getRedisTimeSeries();
        TSElement tsElement = redisTimeSeries.tsGet(indicator);
        if(null == tsElement) {
            return ReturnResult.error("指标不存在");
        }
        redisTimeSeries.tsDel(indicator, fromTimestamp, toTimestamp);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<List<TimeIndicator>> range(String indicator, long fromTimestamp, long toTimestamp, int count) {
        RedisSession redisSession  = (RedisSession) redisClient.getSession();

        if(!redisSession.checkModule("timeseries")) {
            return ReturnResult.error("模块未加载");
        }

        RedisTimeSeries redisTimeSeries = ((RedisSession) redisClient.getSession()).getRedisTimeSeries();
        TSElement tsElement = redisTimeSeries.tsGet(indicator);
        if(null == tsElement) {
            return ReturnResult.error("指标不存在");
        }
        TSRangeParams tsRangeParams = new TSRangeParams(fromTimestamp, toTimestamp);
        tsRangeParams.count(count);
        tsRangeParams.latest();

        List<TSElement> tsElements = redisTimeSeries.tsRange(indicator, tsRangeParams);
        List<TimeIndicator> timeIndicators = new ArrayList<>(1000);
        String[] split = indicator.split(":");
        for (TSElement element : tsElements) {
            TimeIndicator timeIndicator = new TimeIndicator(split[3]);
            timeIndicator.setName(split[4]);
            timeIndicator.setTimestamp(element.getTimestamp());
            timeIndicator.setValue(element.getValue());

            timeIndicators.add(timeIndicator);
        }
        return ReturnResult.success(timeIndicators);
    }

}

