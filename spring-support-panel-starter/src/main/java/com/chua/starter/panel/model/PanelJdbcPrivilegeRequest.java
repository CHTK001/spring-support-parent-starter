package com.chua.starter.panel.model;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 面板账号权限请求。
 *
 * @author CH
 */
@Data
@Schema(description = "面板账号权限请求")
public class PanelJdbcPrivilegeRequest {

    /** 账号名称。 */
    @Schema(description = "账号名称")
    private String panelAccountName;
    /** 主机。 */
    @Schema(description = "主机")
    private String panelHost;
    /** 权限列表。 */
    @Schema(description = "权限列表")
    private List<String> panelPrivileges;
    /** catalog 名称。 */
    @Schema(description = "catalog 名称")
    private String panelCatalogName;
    /** 表名。 */
    @Schema(description = "表名")
    private String panelTableName;
    /** 是否附带授权选项。 */
    @Schema(description = "是否附带授权选项")
    private boolean panelGrantOption;
}
