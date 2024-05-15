package com.chua.starter.monitor.server.service;

import com.chua.common.support.lang.code.ReturnResult;
import org.springframework.cache.annotation.Cacheable;

import static com.chua.starter.common.support.constant.Constant.REDIS_CACHE;

/**
 * ip
 * @author CH
 * @version 1.0.0
 * @since 2024/01/19
 */
public interface IptablesService {


    /**
     * 翻译地址
     * @param address 地址
     * @return {@link ReturnResult}<{@link GeoCity}>
     */
    @Cacheable(cacheManager = REDIS_CACHE, cacheNames = REDIS_CACHE, key = "#address")
    ReturnResult<GeoCity> transferAddress(String address);
}
