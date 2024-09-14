package com.chua.report.server.starter.service;

import com.chua.common.support.geo.GeoCity;
import com.chua.common.support.lang.code.ReturnResult;

/**
 * ip
 * @author CH
 * @version 1.0.0
 * @since 2024/01/19
 */
public interface ReportIptablesService {


    /**
     * 翻译地址
     * @param address 地址
     * @return {@link ReturnResult}<{@link GeoCity}>
     */
    ReturnResult<GeoCity> transferReportAddress(String address);
}
