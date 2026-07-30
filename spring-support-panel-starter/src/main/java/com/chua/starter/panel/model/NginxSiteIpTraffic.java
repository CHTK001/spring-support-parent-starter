package com.chua.starter.panel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Nginx 站点 IP 流量统计")
public class NginxSiteIpTraffic {

    /** IP 地址。 */
    @Schema(description = "IP 地址")
    private String ip;

    /** 请求数。 */
    @Schema(description = "请求数")
    private long requests;

    /** 占比。 */
    @Schema(description = "占比")
    private double percent;
}
