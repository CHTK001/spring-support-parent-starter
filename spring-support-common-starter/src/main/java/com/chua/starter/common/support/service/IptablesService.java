package com.chua.starter.common.support.service;

import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.constant.CacheConstant;
import org.springframework.cache.annotation.Cacheable;

/**
 * IP地址服务接口，提供IP地址相关操作功能
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/19
 */
public interface IptablesService {


    /**
     * 将IP地址转换为地理位置信息
     *
     * @param address IP地址字符串，例如: "114.114.114.114" 或 "8.8.8.8"
     * @return 返回包含地理位置信息的封装结果，如果转换失败则返回错误信息
     * 示例成功返回: {@code ReturnResult.ok(GeoCity)} 包含城市、省份、国家等信息
     * 示例失败返回: {@code ReturnResult.failed("无法解析IP地址")} 包含错误原因
     */
    @Cacheable(cacheManager = CacheConstant.REDIS_CACHE_MIN, cacheNames = CacheConstant.REDIS_CACHE_MIN, key = "#address")
    ReturnResult<GeoCity> transferAddress(String address);
}

