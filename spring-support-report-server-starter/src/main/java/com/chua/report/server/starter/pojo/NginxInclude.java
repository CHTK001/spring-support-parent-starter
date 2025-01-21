package com.chua.report.server.starter.pojo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * nginx include配置
 * @author CH
 */
@Data
@AllArgsConstructor
@Schema(title = "nginx include配置")
public class NginxInclude {

    /**
     * 名称
     */
    @Schema(title = "名称")
    private String name;

    /**
     * 配置
     */
    @Schema(title = "配置")
    private String config;
}
