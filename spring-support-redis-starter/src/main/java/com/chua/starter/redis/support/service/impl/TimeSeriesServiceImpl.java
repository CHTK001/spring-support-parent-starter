package com.chua.starter.redis.support.service.impl;

import com.chua.advanced.support.indicator.DataIndicator;
import com.chua.advanced.support.indicator.TimeIndicator;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.redis.support.client.RedisClient;
import com.chua.redis.support.client.RedisTimeSeries;
import com.chua.starter.redis.support.service.TimeSeriesService;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.timeseries.TSCreateParams;
import redis.clients.jedis.timeseries.TSElement;
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

        if(!redisClient.checkModule("timeseries")) {
            return ReturnResult.error("模块未加载");
        }

        RedisTimeSeries redisTimeSeries = redisClient.getRedisTimeSeries();
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

        if(!redisClient.checkModule("timeseries")) {
            return ReturnResult.error("模块未加载");
        }

        RedisTimeSeries redisTimeSeries = redisClient.getRedisTimeSeries();
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

        if(!redisClient.checkModule("timeseries")) {
            return ReturnResult.error("模块未加载");
        }

        RedisTimeSeries redisTimeSeries = redisClient.getRedisTimeSeries();
        TSElement tsElement = redisTimeSeries.tsGet(indicator);
        if(null == tsElement) {
            return ReturnResult.error("指标不存在");
        }
        redisTimeSeries.tsDel(indicator, fromTimestamp, toTimestamp);
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<List<TimeIndicator>> range(String indicator, long fromTimestamp, long toTimestamp, boolean latest, int count) {

        if(!redisClient.checkModule("timeseries")) {
            return ReturnResult.error("模块未加载");
        }

        RedisTimeSeries redisTimeSeries = redisClient.getRedisTimeSeries();
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

        if(!redisClient.checkModule("timeseries")) {
            return ReturnResult.error("模块未加载");
        }

        JedisPool jedis = redisClient.getJedisPool();
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
        JedisPool jedis = redisClient.getJedisPool();
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
    public void put(String indicator, String value) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            resource.set(indicator, value);
        }
    }

    @Override
    public void hSet(String indicator, String key, String value) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            resource.hset(indicator, key, value);
        }
    }

    @Override
    public Map<String, String> hGet(String indicator) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            return resource.hgetAll(indicator);
        }
    }

    @Override
    public void increment(String indicator, String key) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            resource.hincrBy(indicator ,  key, 1);
        }
    }

    @Override
    public void decrement(String indicator, String key) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            resource.hincrBy(indicator ,  key, -1);
        }
    }

    @Override
    public boolean expire(String key, long seconds) {
        JedisPool jedis = redisClient.getJedisPool();
        try (Jedis resource = jedis.getResource()) {
            return resource.expire(key, seconds) == 1;
        }
    }
}

