package com.chua.starter.datasearch.pojo.location;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 定位解析出参（顶栏天气取位置的唯一契约）。
 *
 * <p>字段命名与前端 {@code GeoLocation} 一一对应：由服务端按客户端 IP 解析出
 * 经纬度与地名，避免浏览器授权与跨域定位接口。</p>
 *
 * @param latitude  纬度
 * @param longitude 经度
 * @param city      展示地名
 * @param source    来源（ip-api 等）
 * @author CH
 * @since 4.0.0.43
 */
@Schema(description = "定位解析结果")
public record LocationVO(
        @Schema(description = "纬度") Double latitude,
        @Schema(description = "经度") Double longitude,
        @Schema(description = "展示地名") String city,
        @Schema(description = "来源") String source) {
}
