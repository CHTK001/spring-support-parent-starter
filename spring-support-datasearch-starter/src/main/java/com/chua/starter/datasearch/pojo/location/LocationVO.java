package com.chua.starter.datasearch.pojo.location;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 定位解析出参（顶栏天气取位置的唯一契约）。
 *
 * <p>字段命名与前端 {@code GeoLocation} 一一对应：由服务端按客户端 IP 解析出
 * 经纬度与地名，避免浏览器授权与跨域定位接口。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Schema(description = "定位解析结果")
@Data
public class LocationVO {

    /**
     * 纬度
    */
    @Schema(description = "纬度")
    private Double latitude;

    /**
     * 经度
    */
    @Schema(description = "经度")
    private Double longitude;

    /**
     * 展示地名
    */
    @Schema(description = "展示地名")
    private String city;

    /**
     * 来源（ip-api 等）
    */
    @Schema(description = "来源")
    private String source;
}
