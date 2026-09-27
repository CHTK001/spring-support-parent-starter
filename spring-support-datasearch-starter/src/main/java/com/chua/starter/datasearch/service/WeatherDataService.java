package com.chua.starter.datasearch.service;

import com.chua.starter.datasearch.pojo.location.LocationVO;
import com.chua.starter.datasearch.pojo.weather.WeatherOverviewVO;
import com.chua.starter.datasearch.query.WeatherQuery;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 顶栏天气数据服务。
 *
 * <p>把多个三方气象数据源（SPI）归一成本系统契约，前端只认这一套结构；
 * 数据源可达性由实现内部判定，全部不可用时返回 {@code null} 交由调用方降级。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public interface WeatherDataService {

    /**
     * 查询天气总览（实况 + 逐小时 + 逐日）。
     *
     * @param query 查询条件（坐标优先，缺省按城市）
     * @return 天气总览；无可用数据源时返回 空
     */
    WeatherOverviewVO overview(WeatherQuery query);

    /**
     * 按请求来源解析展示位置。
     *
     * <p>公网客户端按来源 IP 定位；内网/回环地址无法定位，退回服务器自身定位。</p>
     *
     * @param request 当前请求（取客户端 IP）
     * @return 定位结果；数据源不可达时返回 空
     */
    LocationVO resolveLocation(HttpServletRequest request);
}
