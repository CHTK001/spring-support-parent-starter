package com.chua.starter.common.support.service;

import com.chua.common.support.network.ip.GeoCity;
import com.chua.common.support.lang.code.ReturnResult;
import org.springframework.cache.annotation.Cacheable;

/**
 * IP地址服务接口，提供IP地址相关操作功能
 *
 * @版本 1.0.0
 * @since 2024/01/19
 * @author CH
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
    @Cacheable(cacheManager = "redis-cache-600", cacheNames = "redis-cache-600", key = "#address")
    ReturnResult<GeoCity> transferAddress(String address);
}

