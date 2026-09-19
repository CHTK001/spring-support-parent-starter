package com.chua.starter.software.support.pojo.software;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 软件信息响应
 *
 * @since 2026/7/25
 * @author CH
 */
@Data
@Schema(description = "软件信息响应")
public class SoftwareDTO extends com.chua.starter.software.support.entity.SoftSoftware {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 软件源名称
    */
    @Schema(description = "软件源名称")
    /** 来源名称 */
    private String sourceName;

    /**
    * 是否已安装
    */
    @Schema(description = "是否已安装")
    /** Installed */
    private Boolean installed;
}
