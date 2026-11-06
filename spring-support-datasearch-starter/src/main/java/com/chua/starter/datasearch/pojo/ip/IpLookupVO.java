package com.chua.starter.datasearch.pojo.ip;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * IP 归属地出参（与前端 {@code ReIpText} 读取的字段一一对应）。
 *
 * <p>前端取 {@code country/province/city/isp} 拼成展示文本，其余字段供详情弹窗使用。
 * 字段全部可为空：内网地址、未收录地址在数据源侧就是空串，前端会跳过空值，
 * 这里不强行填「未知」——那会让「无归属地」和「归属地叫未知」无法区分。</p>
 *
 * @param ip        查询的 IP
 * @param country   国家或地区
 * @param province  省份
 * @param city      城市
 * @param isp       运营商
 * @param latitude  纬度（数据源不提供时为空）
 * @param longitude 经度（数据源不提供时为空）
 * @param postal    邮编（数据源不提供时为空）
 * @param timeZone  时区（数据源不提供时为空）
 * @author CH
 * @since 4.0.0.53
 */
@Schema(description = "IP 归属地")
public record IpLookupVO(
        @Schema(description = "查询的 IP") String ip,
        @Schema(description = "国家或地区") String country,
        @Schema(description = "省份") String province,
        @Schema(description = "城市") String city,
        @Schema(description = "运营商") String isp,
        @Schema(description = "纬度") Double latitude,
        @Schema(description = "经度") Double longitude,
        @Schema(description = "邮编") String postal,
        @Schema(description = "时区") String timeZone) {
}
