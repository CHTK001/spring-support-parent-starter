package com.chua.starter.protocol.support.service.server;

import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.lang.code.ReturnResult;
import org.springframework.cache.annotation.Cacheable;

import static com.chua.starter.common.support.constant.Constant.REDIS_CACHE_MIN;

/**
 * IP地址服务
 *
 * @author CH
 * @since 2024/7/31
 */
public interface IptablesService {

    /**
     * 翻译地址
     * @param address 地址
     * @return {@link ReturnResult}<{@link GeoCity}>
     */
    @Cacheable(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, key = "#address")
    ReturnResult<GeoCity> transferAddress(String address);
}
