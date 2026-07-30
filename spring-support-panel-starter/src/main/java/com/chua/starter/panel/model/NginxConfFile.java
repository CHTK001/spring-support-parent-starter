package com.chua.starter.panel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Nginx 配置文件模型（支持多文件场景）。
 *
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Nginx 配置文件模型（支持多文件场景）")
public class NginxConfFile {

    /** 文件绝对路径，如 "/etc/nginx/nginx.conf"。 */
    @Schema(description = "文件绝对路径")
    private String path;

    /** 显示标签，如 "nginx.conf"。 */
    @Schema(description = "显示标签")
    private String label;

    /** 文件类型：main / site / include。 */
    @Builder.Default
    @Schema(description = "文件类型：main / site / include")
    private String type = "site";

    /** 该文件中解析出的 server 块列表。 */
    @Schema(description = "server 块列表")
    private List<NginxServerBlock> servers;

    /** 文件是否存在。 */
    @Builder.Default
    @Schema(description = "文件是否存在")
    private boolean exists = true;
}
