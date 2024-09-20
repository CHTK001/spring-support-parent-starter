package com.chua.starter.redis.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.indicator.DataIndicator;
import com.chua.common.support.session.indicator.TimeIndicator;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.redis.support.client.RedisClient;
import com.chua.redis.support.client.RedisSession;
import com.chua.redis.support.client.RedisTimeSeries;
import com.chua.starter.redis.support.service.TimeSeriesService;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.timeseries.TSCreateParams;
import redis.clients.jedis.timeseries.TSElement;
import redis.clients.jedis.timeseries.TSMGetParams;
import redis.clients.jedis.timeseries.TSRangeParams;

import java.util.*;

/**
 * 时间序列服务的实现类。
 * 提供关于时间序列数据的增、删、改、查等操作的实现。
 *
 * @author CH
 * @since 2024/7/4
 */
public class TimeSeriesServiceImpl implements TimeSeriesService {


    @Autowired
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
    public ReturnResult<List<TimeIndicator>> range(String indicator, long fromTimestamp, long toTimestamp, boolean latest, int count) {
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

        List<TSElement> tsElements = latest ?  redisTimeSeries.tsRevRange(indicator, tsRangeParams) : redisTimeSeries.tsRange(indicator, tsRangeParams);
        List<TimeIndicator> timeIndicators = new ArrayList<>(1000);
        for (TSElement element : tsElements) {
            TimeIndicator timeIndicator = new TimeIndicator(indicator);
            timeIndicator.setTimestamp(element.getTimestamp());
            timeIndicator.setValue(element.getValue());

            timeIndicators.add(timeIndicator);
        }
        return ReturnResult.success(timeIndicators);
    }

    @Override
    public ReturnResult<Map<String, List<TimeIndicator>>> mRange(String indicator, long fromTimestamp, long toTimestamp, boolean latest, int count) {
        RedisSession redisSession  = (RedisSession) redisClient.getSession();

        if(!redisSession.checkModule("timeseries")) {
            return ReturnResult.error("模块未加载");
        }

        JedisPool jedis = redisSession.getJedis();
        Set<String> keys = null;
        try (Jedis jedis1 = jedis.getResource()) {
            keys = jedis1.keys(indicator + "*");
        }

        if(CollectionUtils.isEmpty(keys)) {
            return ReturnResult.success(Collections.emptyMap());
        }

        Map<String, List<TimeIndicator>> map = new HashMap<>(keys.size());
        keys.forEach(it -> {
            map.put(it, range(it, fromTimestamp, toTimestamp, latest, count).getData());
        });

        return ReturnResult.success(map);
    }

    @Override
    public ReturnResult<DataIndicator> get(String indicator, long fromTimestamp, long toTimestamp, int count) {
        RedisSession redisSession  = (RedisSession) redisClient.getSession();
        JedisPool jedis = redisSession.getJedis();
        try (Jedis resource = jedis.getResource()) {
            String s = resource.get(indicator);
            DataIndicator dataIndicator = new DataIndicator("");
            dataIndicator.setTimestamp(System.currentTimeMillis());
            dataIndicator.setPersistence(true);
            dataIndicator.setValue(s);
            return ReturnResult.of(dataIndicator);
        }
    }

    @Override
    public void set(String indicator, String value) {
        RedisSession redisSession  = (RedisSession) redisClient.getSession();
        JedisPool jedis = redisSession.getJedis();
        try (Jedis resource = jedis.getResource()) {
            resource.set(indicator, value);
        }
    }

}

