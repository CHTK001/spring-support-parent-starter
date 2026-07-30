package com.chua.starter.software.support.pojo.software;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 软件信息响应
 *
 * @author CH
 * @since 2026/7/25
 */
@Data
@Schema(description = "软件信息响应")
public class SoftwareDTO extends com.chua.starter.software.support.entity.SoftSoftware {

    private static final long serialVersionUID = 1L;

    /**
     * 软件源名称
     */
    @Schema(description = "软件源名称")
    private String sourceName;

    /**
     * 是否已安装
     */
    @Schema(description = "是否已安装")
    private Boolean installed;
}