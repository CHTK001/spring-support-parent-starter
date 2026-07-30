package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 上传 JDBC 驱动返回结果。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "上传 JDBC 驱动返回结果")
public class PanelDriverUploadView {

    /** 方言类型。 */
    @Schema(description = "方言类型")
    private String panelDialectType;
    /** 驱动类名。 */
    @Schema(description = "驱动类名")
    private String panelDriverClassName;
    /** 驱动 Jar 名称。 */
    @Schema(description = "驱动 Jar 名称")
    private String panelDriverJarName;
    /** 驱动 Jar 路径。 */
    @Schema(description = "驱动 Jar 路径")
    private String panelDriverJarPath;
}
