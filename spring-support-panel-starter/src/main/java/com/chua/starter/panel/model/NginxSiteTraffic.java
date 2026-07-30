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
@Schema(description = "Nginx 站点流量统计")
public class NginxSiteTraffic {

    /** 站点标识。 */
    @Schema(description = "站点标识")
    private String siteKey;

    /** 站点名称。 */
    @Schema(description = "站点名称")
    private String siteName;

    /** 日志路径。 */
    @Schema(description = "日志路径")
    private String logPath;

    /** 请求数。 */
    @Schema(description = "请求数")
    private long requests;

    /** 独立 IP 数。 */
    @Schema(description = "独立 IP 数")
    private long uniqueIps;

    /** QPS。 */
    @Schema(description = "QPS")
    private double qps;

    /** TPS。 */
    @Schema(description = "TPS")
    private double tps;

    /** Top IP 列表。 */
    @Schema(description = "Top IP 列表")
    private List<NginxSiteIpTraffic> topIps;
}
