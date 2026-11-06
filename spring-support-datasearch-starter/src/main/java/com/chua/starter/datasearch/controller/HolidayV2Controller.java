package com.chua.starter.datasearch.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasearch.pojo.holiday.HolidayVO;
import com.chua.starter.datasearch.service.HolidayDataService;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 节假日前台接口。
 *
 * <p>路径与前端 {@code HOLIDAY_LIST_PATH}（{@code /v2/holiday/get}）严格一致：
 * 原实现由 system 模块的 {@code SysHolidayController} 提供并读库表，本模块改为直连
 * {@code HolidayProvider} SPI，让没有 sys_holiday 表的宿主也能出放假安排。</p>
 *
 * <p>接口口径：</p>
 * <ul>
 *   <li>参数可选（HEAD 探测与本年度查询都无需传参）；</li>
 *   <li>匿名可访问（日历在登录前就可能渲染）；</li>
 *   <li>「今年没有数据」返回空数组而不是失败码 —— 空是权威结论，失败才是回落信号；
 *       只有数据源整体不可用才返回失败码，前端据此退回内置表。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Tag(name = "节假日前台接口")
@RestController
@RequestMapping("/v2/holiday")
@RequiredArgsConstructor
public class HolidayV2Controller {

    /**
     * 节假日数据服务
     */
    private final HolidayDataService holidayDataService;

    /**
     * 取某年的法定节假日与调休补班（按日期逐行）
     *
     * @param year 公历年，缺省当年
     * @return 节假日列表
     */
    @Operation(summary = "节假日列表")
    @Parameter(name = "year", description = "公历年，如 2026；缺省当前年份")
    @TokenForIgnore
    @GetMapping("/get")
    public ReturnResult<List<HolidayVO>> get(
            @RequestParam(value = "year", required = false) Integer year) {
        try {
            List<HolidayVO> list = holidayDataService.list(year);
            return list == null ? ReturnResult.error("无可用节假日数据源") : ReturnResult.ok(list);
        } catch (Exception e) {
            log.warn("[holiday] 节假日查询失败: year={}, msg={}", year, e.getMessage());
            return ReturnResult.error("节假日查询失败：" + e.getMessage());
        }
    }
}
