package com.chua.starter.datasearch.pojo.holiday;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 节假日出参（与前端 {@code SysHolidayDTO} 字段一一对应）。
 *
 * <p>前端 {@code normalizeHolidays} 读的是 {@code sysHolidayDate} / {@code sysHolidayName} /
 * {@code sysHolidayIsHoliday} 三个字段；后台原本的 {@code SysHoliday} 实体由 system 模块的
 * 数据库表提供，本模块没有那张表，因此用同一套字段名直接对外，前端无需为「有的环境走库、
 * 有的环境走三方」写两套解析。</p>
 *
 * @param sysHolidayId        主键（本模块无库表，固定为 0）
 * @param sysHolidayYear      年份
 * @param sysHolidayDate      日期（yyyy-MM-dd）
 * @param sysHolidayName      节日名 / 调休说明
 * @param sysHolidayIsHoliday true=休息，false=调休上班
 * @author CH
 * @since 4.0.0.53
 */
@Schema(description = "节假日")
public record HolidayVO(
        @Schema(description = "主键（无库表时为 0）") long sysHolidayId,
        @Schema(description = "年份") int sysHolidayYear,
        @Schema(description = "日期（yyyy-MM-dd）") String sysHolidayDate,
        @Schema(description = "节日名 / 调休说明") String sysHolidayName,
        @Schema(description = "true=休息，false=调休上班") boolean sysHolidayIsHoliday) {
}
