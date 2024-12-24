package com.chua.starter.redis.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.task.cache.Cacheable;
import com.chua.common.support.task.cache.GuavaCacheable;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.redis.support.client.RedisChanelSession;
import com.chua.redis.support.client.RedisClient;
import com.chua.redis.support.client.RedisSearch;
import com.chua.redis.support.search.SearchIndex;
import com.chua.redis.support.search.SearchQuery;
import com.chua.redis.support.search.SearchResultItem;
import com.chua.starter.redis.support.service.RedisSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.util.Map;

/**
 * Redis搜索服务的实现类。
 * 该类实现了RedisSearchService接口，提供了在Redis中进行搜索操作的具体实现。
 * @author CH
 * @since 2024/7/5
 */
public class RedisSearchServiceImpl implements RedisSearchService {
    @Autowired
    private RedisClient redisClient;
    public static final String LANGUAGE = "english";

    private static final Cacheable CACHEABLE = new GuavaCacheable();

    @Override
    public ReturnResult<Boolean> dropIndex(String index, long expireTime) {
        if(null == redisClient) {
            return ReturnResult.error("redisClient未初始化");
        }

        if(!CACHEABLE.exist(index)) {
            return ReturnResult.ok();
        }
        RedisChanelSession redisSession  = (RedisChanelSession) redisClient.getSession();

        try (Jedis jedis = redisSession.getJedis().getResource()) {
            String s = jedis.get(index + ":CREATE_INIT");
            if(StringUtils.isEmpty(s)) {
                return ReturnResult.ok();
            }

            long l = 0;
            try {
                l = Long.parseLong(s);
            } catch (NumberFormatException e) {
                return ReturnResult.error("数据异常");
            }

            if(System.currentTimeMillis() - l > expireTime) {
                redisSession.remove(index + ":CREATE_INIT");
                return ReturnResult.ok();
            }
        }
        return ReturnResult.ok();
    }

    @Override
    public ReturnResult<Boolean> createIndex(SearchIndex searchIndex) {
        if(null == redisClient) {
            return ReturnResult.error("redisClient未初始化");
        }

        if(CACHEABLE.exist(searchIndex.getName())) {
            return ReturnResult.ok();
        }
        RedisChanelSession redisSession  = (RedisChanelSession) redisClient.getSession();

        if(!redisSession.checkModule("search")) {
            return ReturnResult.error("模块未加载");
        }

        try (Jedis jedis = redisSession.getJedis().getResource()) {
            jedis.set(searchIndex.getName() + ":CREATE_INIT", String.valueOf(System.currentTimeMillis()));
        }
        RedisSearch redisSearch = redisSession.getRedisSearch();
        try {
            redisSearch.ftInfo(searchIndex.getName());
            CACHEABLE.put(searchIndex.getName(), searchIndex.getName(), 30);
            return ReturnResult.ok();
        } catch (Exception ignored) {
        }
        redisSearch.createIndex(searchIndex);
        return ReturnResult.ok();
    }

    @Override
    public ReturnResult<Boolean> addDocument(String key, Map<String, String> document) {
        if(null == redisClient) {
            return ReturnResult.error("redisClient未初始化");
        }

        RedisChanelSession redisSession  = (RedisChanelSession) redisClient.getSession();

        if(!redisSession.checkModule("search")) {
            return ReturnResult.error("模块未加载");
        }
        RedisSearch redisSearch = redisSession.getRedisSearch();
        redisSearch.addDocument(LANGUAGE, key + ":" + IdUtils.createSimpleUuid(), document);
        return ReturnResult.ok();
    }

    //SQL Condition	                                RediSearch Equivalent	注释
    //where x='foo' and y='bar'	                    @x:foo @y:bar	for less ambiguity use (@x:foo) (@y:bar)
    //where x='foo' and y!='bar'	                @x:foo -@y:bar
    //where x='foo' or y='bar'	                    (@x:foo) | (@y:bar)
    //where x in ('foo' ,'bar' )	                @x:(foo| bar)	quotes means exact phrase
    //where y='foo' and x not in ('foo' ,'bar' )	@y:foo (-@x:foo)(-@x:bar)
    //where num between 10 and 20	                @num:[10:20]
    //where num >=10	                            @num:[10 +inf]
    //where num > 10	                            @num:[(10 +inf]
    //where num < 10	                            @num:[-inf (10]
    //where num <= 10	                            @num:[-inf 10]
    //where num < 10 or num >20	                    @num:[-inf (10] | @num:[(20 +inf ]
    //where name like 'john%'	                    @name:john*
    @Override
    public ReturnResult<SearchResultItem> queryAll(SearchQuery query, int offset, int limit) {
        if(null == redisClient) {
            return ReturnResult.error("redisClient未初始化");
        }

        RedisChanelSession redisSession  = (RedisChanelSession) redisClient.getSession();

        if(!redisSession.checkModule("search")) {
            return ReturnResult.error("模块未加载");
        }


        RedisSearch redisSearch = redisSession.getRedisSearch();
        return ReturnResult.ok(redisSearch.queryAll(query, offset, limit));
    }
}
