package com.chua.starter.datasearch.service;

import com.chua.starter.datasearch.pojo.holiday.HolidayVO;

import java.util.List;

/**
 * 节假数据服务。
 *
 * <p>数据源为 {@code HolidayProvider} SPI（holiday-cn 年度 JSON，内置 2026 兜底），
 * 输出按日期逐行排好序的列表，字段名对齐前端 {@code SysHolidayDTO}。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface HolidayDataService {

    /**
     * 取某年的法定节假日与调休补班。
     *
     * @param year 公历年，为空取当前年份
     * @return 按日期升序的记录；无可用数据源时返回 空，由调用方给出失败码
     */
    List<HolidayVO> list(Integer year);
}
