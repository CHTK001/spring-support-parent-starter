package com.chua.starter.panel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Nginx location{} 块模型。
 *
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Nginx location 块模型")
public class NginxLocationBlock {

    /** 路径，如 "/" 或 "/api"。 */
    @Schema(description = "路径")
    private String path;

    /** 模式：proxy / static / redirect / custom。 */
    @Builder.Default
    @Schema(description = "模式：proxy / static / redirect / custom")
    private String mode = "proxy";

    // ── 反向代理 ──────────────────────────────────────────────────────────────

    /** proxy_pass 目标地址。 */
    @Schema(description = "proxy_pass 目标地址")
    private String proxyPass;

    /** proxy_read_timeout（秒）。 */
    @Schema(description = "proxy_read_timeout（秒）")
    private String proxyReadTimeout;

    /** proxy_connect_timeout（秒）。 */
    @Schema(description = "proxy_connect_timeout（秒）")
    private String proxyConnectTimeout;

    /** proxy_set_header 列表，如 ["Host $host", "X-Real-IP $remote_addr"]。 */
    @Schema(description = "proxy_set_header 列表")
    private List<String> proxySetHeader;

    /** proxy_buffering on/off。 */
    @Schema(description = "proxy_buffering on/off")
    private Boolean proxyBuffering;

    // ── 静态文件 ──────────────────────────────────────────────────────────────

    /** root 目录。 */
    @Schema(description = "root 目录")
    private String root;

    /** alias 目录。 */
    @Schema(description = "alias 目录")
    private String alias;

    /** try_files 指令。 */
    @Schema(description = "try_files 指令")
    private String tryFiles;

    /** expires 缓存时间，如 "7d"。 */
    @Schema(description = "expires 缓存时间")
    private String expires;

    /** autoindex on/off。 */
    @Schema(description = "autoindex on/off")
    private Boolean autoindex;

    /** index 文件。 */
    @Schema(description = "index 文件")
    private String index;

    // ── 重定向 ────────────────────────────────────────────────────────────────

    /** return 状态码，如 "301"。 */
    @Schema(description = "return 状态码")
    private String returnCode;

    /** return 目标 URL。 */
    @Schema(description = "return 目标 URL")
    private String returnUrl;

    // ── 通用 ──────────────────────────────────────────────────────────────────

    /** allow 列表。 */
    @Schema(description = "allow 列表")
    private List<String> allowList;

    /** deny 列表。 */
    @Schema(description = "deny 列表")
    private List<String> denyList;

    /** limit_req zone 名称。 */
    @Schema(description = "limit_req zone 名称")
    private String limitReqZone;

    /** limit_req burst。 */
    @Schema(description = "limit_req burst")
    private String limitReqBurst;

    /** limit_req nodelay。 */
    @Schema(description = "limit_req nodelay")
    private Boolean limitReqNoDelay;

    /** add_header 列表，如 ["X-Frame-Options SAMEORIGIN"]。 */
    @Schema(description = "add_header 列表")
    private List<String> addHeader;

    /** 自定义模式下的原始指令文本。 */
    @Schema(description = "自定义模式下的原始指令文本")
    private String rawDirectives;
}
