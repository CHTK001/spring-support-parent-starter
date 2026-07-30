package com.chua.starter.panel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Nginx server{} 块模型。
 *
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Nginx server 块模型")
public class NginxServerBlock {

    /** listen 端口/地址，如 "80" 或 "443 ssl"。 */
    @Schema(description = "listen 端口/地址")
    private String listen;

    /** server_name，如 "example.com www.example.com"。 */
    @Schema(description = "server_name")
    private String serverName;

    /** root 目录。 */
    @Schema(description = "root 目录")
    private String root;

    /** index 文件。 */
    @Schema(description = "index 文件")
    private String index;

    /** ssl_certificate 路径。 */
    @Schema(description = "ssl_certificate 路径")
    private String sslCertificate;

    /** ssl_certificate_key 路径。 */
    @Schema(description = "ssl_certificate_key 路径")
    private String sslCertificateKey;

    /** access_log 路径。 */
    @Schema(description = "access_log 路径")
    private String accessLog;

    /** location 块列表。 */
    @Schema(description = "location 块列表")
    private List<NginxLocationBlock> locations;

    /** 所属配置文件路径（用于多文件场景）。 */
    @Schema(description = "所属配置文件路径")
    private String configPath;
}
