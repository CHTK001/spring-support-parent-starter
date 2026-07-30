package com.chua.starter.panel.model;

import lombok.Builder;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 面板数据库账号视图。
 *
 * @author CH
 */
@Data
@Builder
@Schema(description = "面板数据库账号视图")
public class PanelJdbcAccountView {

    /** 账号名称。 */
    @Schema(description = "账号名称")
    private String panelAccountName;
    /** 主机。 */
    @Schema(description = "主机")
    private String panelHost;
    /** 权限列表。 */
    @Schema(description = "权限列表")
    private List<String> panelGrants;
}
