package com.chua.starter.panel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Nginx 流量趋势")
public class NginxTrafficTrend {

    /** 站点标识。 */
    @Schema(description = "站点标识")
    private String siteKey;

    /** 站点名称。 */
    @Schema(description = "站点名称")
    private String siteName;

    /** IP 地址。 */
    @Schema(description = "IP 地址")
    private String ip;

    /** 时间粒度。 */
    @Schema(description = "时间粒度")
    private String granularity;

    /** 趋势点列表。 */
    @Schema(description = "趋势点列表")
    private List<NginxTrafficPoint> points;
}
