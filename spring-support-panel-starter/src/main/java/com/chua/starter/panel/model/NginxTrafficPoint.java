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
@Schema(description = "Nginx 流量趋势点")
public class NginxTrafficPoint {

    /** 标签。 */
    @Schema(description = "标签")
    private String label;

    /** 请求数。 */
    @Schema(description = "请求数")
    private long requests;
}
