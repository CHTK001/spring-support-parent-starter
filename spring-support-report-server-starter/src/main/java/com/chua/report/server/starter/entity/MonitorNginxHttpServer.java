package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 *
 * @since 2024/12/29
 * @author CH    
 */

/**
 * nginx http服务配置
 */
@ApiModel(description = "nginx http服务配置")
@Schema(description = "nginx http服务配置")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "monitor_nginx_http_server")
public class MonitorNginxHttpServer extends SysBase implements Serializable {
    @TableId(value = "monitor_nginx_http_server_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorNginxHttpServerId;

    /**
     * monitor_nginx_http表ID
     */
    @TableField(value = "monitor_nginx_http_id")
    @ApiModelProperty(value = "monitor_nginx_http表ID")
    @Schema(description = "monitor_nginx_http表ID")
    private Integer monitorNginxHttpId;

    /**
     * listen 80
     */
    @TableField(value = "monitor_nginx_http_server_port")
    @ApiModelProperty(value = "listen 80")
    @Schema(description = "listen 80")
    private Integer monitorNginxHttpServerPort;

    /**
     * server_name kuntu.com;
     */
    @TableField(value = "monitor_nginx_http_server_name")
    @ApiModelProperty(value = "server_name kuntu.com;")
    @Schema(description = "server_name kuntu.com;")
    @Size(max = 255, message = "server_name kuntu.com;最大长度要小于 255")
    private String monitorNginxHttpServerName;

    /**
     * 编码
     */
    @TableField(value = "monitor_nginx_http_server_charset")
    @ApiModelProperty(value = "编码")
    @Schema(description = "编码")
    @Size(max = 255, message = "编码最大长度要小于 255")
    private String monitorNginxHttpServerCharset;

    /**
     * #access_log  logs/host.access.log  main;
     */
    @TableField(value = "monitor_nginx_http_server_access_log")
    @ApiModelProperty(value = " #access_log  logs/host.access.log  main;")
    @Schema(description = " #access_log  logs/host.access.log  main;")
    @Size(max = 255, message = " #access_log  logs/host.access.log  main;最大长度要小于 255")
    private String monitorNginxHttpServerAccessLog;

    /**
     * # 301重定向跳转到HTTPS接口;return 301 https://$server_name$request_uri;
     */
    @TableField(value = "monitor_nginx_http_server_return")
    @ApiModelProperty(value = "# 301重定向跳转到HTTPS接口;return 301 https://$server_name$request_uri;")
    @Schema(description = "# 301重定向跳转到HTTPS接口;return 301 https://$server_name$request_uri;")
    @Size(max = 255, message = "# 301重定向跳转到HTTPS接口;return 301 https://$server_name$request_uri;最大长度要小于 255")
    private String monitorNginxHttpServerReturn;

    /**
     * listen 443 ssl;
     */
    @TableField(value = "monitor_nginx_http_server_ssl")
    @ApiModelProperty(value = "listen 443 ssl;")
    @Schema(description = "listen 443 ssl;")
    @Size(max = 11, message = "listen 443 ssl;最大长度要小于 11")
    private String monitorNginxHttpServerSsl;

    /**
     * listen 53 udp;
     */
    @TableField(value = "monitor_nginx_http_server_udp")
    @ApiModelProperty(value = "listen 53 udp;")
    @Schema(description = "listen 53 udp;")
    @Size(max = 255, message = "listen 53 udp;最大长度要小于 255")
    private String monitorNginxHttpServerUdp;

    /**
     * error_page 500 502 503 504 /50x.html;
     */
    @TableField(value = "monitor_nginx_http_server_error_page")
    @ApiModelProperty(value = "error_page 500 502 503 504 /50x.html;")
    @Schema(description = "error_page 500 502 503 504 /50x.html;")
    @Size(max = 255, message = "error_page 500 502 503 504 /50x.html;最大长度要小于 255")
    private String monitorNginxHttpServerErrorPage;

    /**
     * ssl_certificate     	cert/yphtoy.com.pem;   #加密证书路径
     */
    @TableField(value = "monitor_nginx_http_server_ssl_certificate")
    @ApiModelProperty(value = "ssl_certificate     	cert/yphtoy.com.pem;   #加密证书路径")
    @Schema(description = "ssl_certificate     	cert/yphtoy.com.pem;   #加密证书路径")
    @Size(max = 255, message = "ssl_certificate     	cert/yphtoy.com.pem;   #加密证书路径最大长度要小于 255")
    private String monitorNginxHttpServerSslCertificate;

    /**
     * ssl_certificate_key	cert/yphtoy.com.key;       #加密私钥路径
     */
    @TableField(value = "monitor_nginx_http_server_ssl_certificate_key")
    @ApiModelProperty(value = "ssl_certificate_key	cert/yphtoy.com.key;       #加密私钥路径")
    @Schema(description = "ssl_certificate_key	cert/yphtoy.com.key;       #加密私钥路径")
    @Size(max = 255, message = "ssl_certificate_key	cert/yphtoy.com.key;       #加密私钥路径最大长度要小于 255")
    private String monitorNginxHttpServerSslCertificateKey;

    /**
     * ssl_protocols		TLSv1 TLSv1.1 TLSv1.2;     #加密协议
     */
    @TableField(value = "monitor_nginx_http_server_ssl_protocols")
    @ApiModelProperty(value = "ssl_protocols		TLSv1 TLSv1.1 TLSv1.2;     #加密协议")
    @Schema(description = "ssl_protocols		TLSv1 TLSv1.1 TLSv1.2;     #加密协议")
    @Size(max = 255, message = "ssl_protocols		TLSv1 TLSv1.1 TLSv1.2;     #加密协议最大长度要小于 255")
    private String monitorNginxHttpServerSslProtocols;

    /**
     * ssl_session_timeout	10m;                       #加密访问缓存过期时间
     */
    @TableField(value = "monitor_nginx_http_server_ssl_session_timeout")
    @ApiModelProperty(value = "ssl_session_timeout	10m;                       #加密访问缓存过期时间")
    @Schema(description = "ssl_session_timeout	10m;                       #加密访问缓存过期时间")
    @Size(max = 255, message = "ssl_session_timeout	10m;                       #加密访问缓存过期时间最大长度要小于 255")
    private String monitorNginxHttpServerSslSessionTimeout;

    /**
     * ssl_ciphers		HIGH:!aNULL:!MD5;              #加密算法
     */
    @TableField(value = "monitor_nginx_http_server_ssl_ciphers")
    @ApiModelProperty(value = "ssl_ciphers		HIGH:!aNULL:!MD5;              #加密算法")
    @Schema(description = "ssl_ciphers		HIGH:!aNULL:!MD5;              #加密算法")
    @Size(max = 255, message = "ssl_ciphers		HIGH:!aNULL:!MD5;              #加密算法最大长度要小于 255")
    private String monitorNginxHttpServerSslCiphers;

    /**
     * ssl_prefer_server_ciphers on;	               #是否由服务器决定采用哪种加密算法
     */
    @TableField(value = "monitor_nginx_http_server_ssl_prefer_server_ciphers")
    @ApiModelProperty(value = "ssl_prefer_server_ciphers on;	               #是否由服务器决定采用哪种加密算法")
    @Schema(description = "ssl_prefer_server_ciphers on;	               #是否由服务器决定采用哪种加密算法")
    @Size(max = 255, message = "ssl_prefer_server_ciphers on;	               #是否由服务器决定采用哪种加密算法最大长度要小于 255")
    private String monitorNginxHttpServerSslPreferServerCiphers;
}