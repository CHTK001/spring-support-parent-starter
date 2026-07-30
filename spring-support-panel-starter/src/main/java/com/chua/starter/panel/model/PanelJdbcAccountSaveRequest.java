package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 面板账号保存请求。
 *
 * @author CH
 */
@Data
@Schema(description = "面板账号保存请求")
public class PanelJdbcAccountSaveRequest {

    /** 账号名称。 */
    @Schema(description = "账号名称")
    private String panelAccountName;
    /** 主机。 */
    @Schema(description = "主机")
    private String panelHost;
    /** 密码。 */
    @Schema(description = "密码")
    private String panelPassword;
}
