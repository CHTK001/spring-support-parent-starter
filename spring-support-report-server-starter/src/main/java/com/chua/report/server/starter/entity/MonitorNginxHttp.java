package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.report.server.starter.ngxin.NginxDisAssembly;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

import static com.chua.common.support.constant.CommonConstant.EMPTY;

/**
 * @author CH
 * @since 2024/12/29
 */
@ApiModel(description = "monitor_nginx_http")
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "monitor_nginx_http")
public class MonitorNginxHttp extends SysBase implements Serializable {
    @TableId(value = "monitor_nginx_http_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorNginxHttpId;

    /**
     * 长连接超时时间，单位是秒
     */
    @TableField(value = "monitor_nginx_http_keepalive_timeout")
    @ApiModelProperty(value = "长连接超时时间，单位是秒")
    @Schema(description = "长连接超时时间，单位是秒")
    private Integer monitorNginxHttpKeepaliveTimeout;

    /**
     * 开启高效文件传输模式，sendfile指令指定nginx是否调用sendfile函数来输出文件，对于普通应用设为 on，如果用来进行下载等应用磁盘IO重负载应用，可设置为off，以平衡磁盘与网络I/O处理速度，降低系统的负载。注意：如果图片显示不正常把这个改成off.sendfile指令指定 nginx 是否调用sendfile 函数（zero copy 方式）来输出文件，对于普通应用，必须设为on。如果用来进行下载等应用磁盘IO重负载应用，可设置为off，以平衡磁盘与网络IO处理速度，降低系统uptime
     */
    @TableField(value = "monitor_nginx_http_sendfile")
    @ApiModelProperty(value = "开启高效文件传输模式，sendfile指令指定nginx是否调用sendfile函数来输出文件，对于普通应用设为 on，如果用来进行下载等应用磁盘IO重负载应用，可设置为off，以平衡磁盘与网络I/O处理速度，降低系统的负载。注意：如果图片显示不正常把这个改成off.sendfile指令指定 nginx 是否调用sendfile 函数（zero copy 方式）来输出文件，对于普通应用，必须设为on。如果用来进行下载等应用磁盘IO重负载应用，可设置为off，以平衡磁盘与网络IO处理速度，降低系统uptime")
    @Schema(description = "开启高效文件传输模式，sendfile指令指定nginx是否调用sendfile函数来输出文件，对于普通应用设为 on，如果用来进行下载等应用磁盘IO重负载应用，可设置为off，以平衡磁盘与网络I/O处理速度，降低系统的负载。注意：如果图片显示不正常把这个改成off.sendfile指令指定 nginx 是否调用sendfile 函数（zero copy 方式）来输出文件，对于普通应用，必须设为on。如果用来进行下载等应用磁盘IO重负载应用，可设置为off，以平衡磁盘与网络IO处理速度，降低系统uptime")
    @Size(max = 11, message = "开启高效文件传输模式，sendfile指令指定nginx是否调用sendfile函数来输出文件，对于普通应用设为 on，如果用来进行下载等应用磁盘IO重负载应用，可设置为off，以平衡磁盘与网络I/O处理速度，降低系统的负载。注意：如果图片显示不正常把这个改成off.sendfile指令指定 nginx 是否调用sendfile 函数（zero copy 方式）来输出文件，对于普通应用，必须设为on。如果用来进行下载等应用磁盘IO重负载应用，可设置为off，以平衡磁盘与网络IO处理速度，降低系统uptime最大长度要小于 11")
    private String monitorNginxHttpSendfile;

    /**
     * 默认字符集
     */
    @TableField(value = "monitor_nginx_http_charset")
    @ApiModelProperty(value = "默认字符集")
    @Schema(description = "默认字符集")
    private String monitorNginxHttpCharset;

    /**
     * 用了log_format指令设置了日志格式之后，需要用access_log指令指定日志文件的存放路径记录了哪些用户，哪些页面以及用户浏览器、ip和其他的访问信息
     */
    @TableField(value = "monitor_nginx_http_access_log")
    @ApiModelProperty(value = "用了log_format指令设置了日志格式之后，需要用access_log指令指定日志文件的存放路径记录了哪些用户，哪些页面以及用户浏览器、ip和其他的访问信息")
    @Schema(description = "用了log_format指令设置了日志格式之后，需要用access_log指令指定日志文件的存放路径记录了哪些用户，哪些页面以及用户浏览器、ip和其他的访问信息")
    @Size(max = 255, message = "用了log_format指令设置了日志格式之后，需要用access_log指令指定日志文件的存放路径记录了哪些用户，哪些页面以及用户浏览器、ip和其他的访问信息最大长度要小于 255")
    private String monitorNginxHttpAccessLog;
    /**
     * 错误日志
     */
    @TableField(value = "monitor_nginx_http_error_log")
    @ApiModelProperty(value = "错误日志")
    @Schema(description = "错误日志")
    private String monitorNginxHttpErrorLog;
    /**
     * 自定义日志格式
     */
    @TableField(value = "monitor_nginx_http_log_format")
    @ApiModelProperty(value = "自定义日志格式")
    @Schema(description = "自定义日志格式")
    private String monitorNginxHttpLogFormat;



    /**
     * 服务器名字的hash表大小
     */
    @TableField(value = "monitor_nginx_http_server_names_hash_bucket_size")
    @ApiModelProperty(value = "服务器名字的hash表大小")
    @Schema(description = "服务器名字的hash表大小")
    private Integer monitorNginxHttpServerNamesHashBucketSize;

    /**
     * 隐藏ngnix版本号
     */
    @TableField(value = "monitor_nginx_http_server_tokens")
    @ApiModelProperty(value = "隐藏ngnix版本号")
    @Schema(description = "隐藏ngnix版本号")
    @Size(max = 255, message = "隐藏ngnix版本号最大长度要小于 255")
    private String monitorNginxHttpServerTokens;

    /**
     * 忽略不合法的请求头
     */
    @TableField(value = "monitor_nginx_http_ignore_invalid_headers")
    @ApiModelProperty(value = "忽略不合法的请求头")
    @Schema(description = "忽略不合法的请求头")
    @Size(max = 255, message = "忽略不合法的请求头最大长度要小于 255")
    private String monitorNginxHttpIgnoreInvalidHeaders;

    /**
     * 让 nginx 在处理自己内部重定向时不默认使用  server_name设置中的第一个域名
     */
    @TableField(value = "monitor_nginx_http_server_name_in_redirect")
    @ApiModelProperty(value = "让 nginx 在处理自己内部重定向时不默认使用  server_name设置中的第一个域名")
    @Schema(description = "让 nginx 在处理自己内部重定向时不默认使用  server_name设置中的第一个域名")
    @Size(max = 255, message = "让 nginx 在处理自己内部重定向时不默认使用  server_name设置中的第一个域名最大长度要小于 255")
    private String monitorNginxHttpServerNameInRedirect;

    /**
     * 客户端请求体的大小
     */
    @TableField(value = "monitor_nginx_http_client_body_buffer_size")
    @ApiModelProperty(value = "客户端请求体的大小")
    @Schema(description = "客户端请求体的大小")
    @Size(max = 255, message = "客户端请求体的大小最大长度要小于 255")
    private String monitorNginxHttpClientBodyBufferSize;

    /**
     * 告诉nginx在一个数据包里发送所有头文件，而不一个接一个的发送。
     */
    @TableField(value = "monitor_nginx_http_tcp_nopush")
    @ApiModelProperty(value = "告诉nginx在一个数据包里发送所有头文件，而不一个接一个的发送。")
    @Schema(description = "告诉nginx在一个数据包里发送所有头文件，而不一个接一个的发送。")
    @Size(max = 11, message = "告诉nginx在一个数据包里发送所有头文件，而不一个接一个的发送。最大长度要小于 11")
    private String monitorNginxHttpTcpNopush;

    /**
     * tcp_nodelay off 会增加通信的延时，但是会提高带宽利用率。在高延时、数据量大的通信场景中应该会有不错的效果tcp_nodelay on，会增加小包的数量，但是可以提高响应速度。在及时性高的通信场景中应该会有不错的效果
     */
    @TableField(value = "monitor_nginx_http_tcp_nodelay")
    @ApiModelProperty(value = "tcp_nodelay off 会增加通信的延时，但是会提高带宽利用率。在高延时、数据量大的通信场景中应该会有不错的效果tcp_nodelay on，会增加小包的数量，但是可以提高响应速度。在及时性高的通信场景中应该会有不错的效果")
    @Schema(description = "tcp_nodelay off 会增加通信的延时，但是会提高带宽利用率。在高延时、数据量大的通信场景中应该会有不错的效果tcp_nodelay on，会增加小包的数量，但是可以提高响应速度。在及时性高的通信场景中应该会有不错的效果")
    @Size(max = 255, message = "tcp_nodelay off 会增加通信的延时，但是会提高带宽利用率。在高延时、数据量大的通信场景中应该会有不错的效果tcp_nodelay on，会增加小包的数量，但是可以提高响应速度。在及时性高的通信场景中应该会有不错的效果最大长度要小于 255")
    private String monitorNginxHttpTcpNodelay;

    /**
     * gzip模块设置，使用 gzip 压缩可以降低网站带宽消耗，同时提升访问速度
     */
    @TableField(value = "monitor_nginx_http_gzip")
    @ApiModelProperty(value = "gzip模块设置，使用 gzip 压缩可以降低网站带宽消耗，同时提升访问速度")
    @Schema(description = "gzip模块设置，使用 gzip 压缩可以降低网站带宽消耗，同时提升访问速度")
    @Size(max = 11, message = "gzip模块设置，使用 gzip 压缩可以降低网站带宽消耗，同时提升访问速度最大长度要小于 11")
    private String monitorNginxHttpGzip;

    /**
     * 禁用gzip压缩
     */
    @TableField(value = "monitor_nginx_http_gzip_disable")
    @ApiModelProperty(value = "禁用gzip压缩")
    @Schema(description = "禁用gzip压缩")
    private String monitorNginxHttpGzipDisable;

    /**
     * 最小压缩大小
     */
    @TableField(value = "monitor_nginx_http_gzip_min_length")
    @ApiModelProperty(value = "最小压缩大小")
    @Schema(description = "最小压缩大小")
    @Size(max = 11, message = "最小压缩大小最大长度要小于 11")
    private String monitorNginxHttpGzipMinLength;

    /**
     * 压缩缓冲区4 16k
     */
    @TableField(value = "monitor_nginx_http_gzip_buffers")
    @ApiModelProperty(value = "压缩缓冲区4 16k")
    @Schema(description = "压缩缓冲区4 16k")
    @Size(max = 255, message = "压缩缓冲区4 16k最大长度要小于 255")
    private String monitorNginxHttpGzipBuffers;

    /**
     * gzip_http_version 1.0;        #压缩版本
     */
    @TableField(value = "monitor_nginx_http_gzip_http_version")
    @ApiModelProperty(value = "gzip_http_version 1.0;        #压缩版本")
    @Schema(description = "gzip_http_version 1.0;        #压缩版本")
    @Size(max = 255, message = "gzip_http_version 1.0;        #压缩版本最大长度要小于 255")
    private String monitorNginxHttpGzipHttpVersion;

    /**
     * gzip_comp_level 2;            #压缩等级
     */
    @TableField(value = "monitor_nginx_http_gzip_comp_level")
    @ApiModelProperty(value = "gzip_comp_level 2;            #压缩等级")
    @Schema(description = "gzip_comp_level 2;            #压缩等级")
    private Integer monitorNginxHttpGzipCompLevel;

    /**
     * #gzip_types   text/plain text/css text/xml text/javascript application/json application/x-javascript application/xml application/xml+rss;#压缩类型
     */
    @TableField(value = "monitor_nginx_http_gzip_types")
    @ApiModelProperty(value = "#gzip_types   text/plain text/css text/xml text/javascript application/json application/x-javascript application/xml application/xml+rss;#压缩类型")
    @Schema(description = "#gzip_types   text/plain text/css text/xml text/javascript application/json application/x-javascript application/xml application/xml+rss;#压缩类型")
    @Size(max = 255, message = "#gzip_types   text/plain text/css text/xml text/javascript application/json application/x-javascript application/xml application/xml+rss;#压缩类型最大长度要小于 255")
    private String monitorNginxHttpGzipTypes;
    /**
     * gzip_vary on;                 #是否开启gzip vary头
     */
    @TableField(value = "monitor_nginx_http_gzip_vary")
    @ApiModelProperty(value = "gzip_vary on;                 #是否开启gzip vary头")
    @Schema(description = "gzip_vary on;                 #是否开启gzip vary头")
    private String monitorNginxHttpGzipVary;

    /**
     * ssl_certificate     	cert/yphtoy.com.pem;   #加密证书路径
     */
    @TableField(value = "monitor_nginx_http_ssl_certificate")
    @ApiModelProperty(value = "ssl_certificate     	cert/yphtoy.com.pem;   #加密证书路径")
    @Schema(description = "ssl_certificate     	cert/yphtoy.com.pem;   #加密证书路径")
    @Size(max = 255, message = "ssl_certificate     	cert/yphtoy.com.pem;   #加密证书路径最大长度要小于 255")
    private String monitorNginxHttpSslCertificate;

    /**
     * ssl_certificate_key	cert/yphtoy.com.key;       #加密私钥路径
     */
    @TableField(value = "monitor_nginx_http_ssl_certificate_key")
    @ApiModelProperty(value = "ssl_certificate_key	cert/yphtoy.com.key;       #加密私钥路径")
    @Schema(description = "ssl_certificate_key	cert/yphtoy.com.key;       #加密私钥路径")
    @Size(max = 255, message = "ssl_certificate_key	cert/yphtoy.com.key;       #加密私钥路径最大长度要小于 255")
    private String monitorNginxHttpSslCertificateKey;

    /**
     * ssl_protocols		TLSv1 TLSv1.1 TLSv1.2;     #加密协议
     */
    @TableField(value = "monitor_nginx_http_ssl_protocols")
    @ApiModelProperty(value = "ssl_protocols		TLSv1 TLSv1.1 TLSv1.2;     #加密协议")
    @Schema(description = "ssl_protocols		TLSv1 TLSv1.1 TLSv1.2;     #加密协议")
    @Size(max = 255, message = "ssl_protocols		TLSv1 TLSv1.1 TLSv1.2;     #加密协议最大长度要小于 255")
    private String monitorNginxHttpSslProtocols;

    /**
     * ssl_session_timeout	10m;                       #加密访问缓存过期时间
     */
    @TableField(value = "monitor_nginx_http_ssl_session_timeout")
    @ApiModelProperty(value = "ssl_session_timeout	10m;                       #加密访问缓存过期时间")
    @Schema(description = "ssl_session_timeout	10m;                       #加密访问缓存过期时间")
    @Size(max = 255, message = "ssl_session_timeout	10m;                       #加密访问缓存过期时间最大长度要小于 255")
    private String monitorNginxHttpSslSessionTimeout;

    /**
     * ssl_ciphers		HIGH:!aNULL:!MD5;              #加密算法
     */
    @TableField(value = "monitor_nginx_http_ssl_ciphers")
    @ApiModelProperty(value = "ssl_ciphers		HIGH:!aNULL:!MD5;              #加密算法")
    @Schema(description = "ssl_ciphers		HIGH:!aNULL:!MD5;              #加密算法")
    @Size(max = 255, message = "ssl_ciphers		HIGH:!aNULL:!MD5;              #加密算法最大长度要小于 255")
    private String monitorNginxHttpSslCiphers;

    /**
     * ssl_prefer_server_ciphers on;	               #是否由服务器决定采用哪种加密算法
     */
    @TableField(value = "monitor_nginx_http_ssl_prefer_server_ciphers")
    @ApiModelProperty(value = "ssl_prefer_server_ciphers on;	               #是否由服务器决定采用哪种加密算法")
    @Schema(description = "ssl_prefer_server_ciphers on;	               #是否由服务器决定采用哪种加密算法")
    @Size(max = 255, message = "ssl_prefer_server_ciphers on;	               #是否由服务器决定采用哪种加密算法最大长度要小于 255")
    private String monitorNginxHttpSslPreferServerCiphers;

    /**
     * # 包含其他的配置文件include  /etc/nginx/conf.d/*.conf;include  /etc/nginx/sites-enabled/*;
     */
    @TableField(value = "monitor_nginx_http_include")
    @ApiModelProperty(value = "# 包含其他的配置文件include  /etc/nginx/conf.d/*.conf;include  /etc/nginx/sites-enabled/*;")
    @Schema(description = "# 包含其他的配置文件include  /etc/nginx/conf.d/*.conf;include  /etc/nginx/sites-enabled/*;")
    @Size(max = 255, message = "# 包含其他的配置文件include  /etc/nginx/conf.d/*.conf;include  /etc/nginx/sites-enabled/*;最大长度要小于 255")
    private String monitorNginxHttpInclude;

    /**
     * 限流；limit_req_zone $binary_remote_addr zone=mylimit:10m rate=1r/s;
     */
    @TableField(value = "monitor_nginx_http_limit_req_zone")
    @ApiModelProperty(value = "限流；limit_req_zone $binary_remote_addr zone=mylimit:10m rate=1r/s;")
    @Schema(description = "限流；limit_req_zone $binary_remote_addr zone=mylimit:10m rate=1r/s;")
    @Size(max = 255, message = "限流；limit_req_zone $binary_remote_addr zone=mylimit:10m rate=1r/s;最大长度要小于 255")
    private String monitorNginxHttpLimitReqZone;

    /**
     * 缓存； proxy_cache_path /data/nginx/cache levels=1:2 keys_zone=my_cache:10m;max_size=10g inactive=60m use_temp_path=off; # 定义缓存路径、目录结构、缓存区名称和大小、最大缓存大小、非活动数据清理时间，以及是否使用临时路径
     */
    @TableField(value = "monitor_nginx_http_proxy_cache_path")
    @ApiModelProperty(value = "缓存； proxy_cache_path /data/nginx/cache levels=1:2 keys_zone=my_cache:10m;max_size=10g inactive=60m use_temp_path=off; # 定义缓存路径、目录结构、缓存区名称和大小、最大缓存大小、非活动数据清理时间，以及是否使用临时路径  ")
    @Schema(description = "缓存； proxy_cache_path /data/nginx/cache levels=1:2 keys_zone=my_cache:10m;max_size=10g inactive=60m use_temp_path=off; # 定义缓存路径、目录结构、缓存区名称和大小、最大缓存大小、非活动数据清理时间，以及是否使用临时路径  ")
    private String monitorNginxHttpProxyCachePath;

    /**
     * 所属配置
     */
    @TableField(value = "monitor_nginx_config_id")
    @ApiModelProperty(value = "所属配置")
    @Schema(description = "所属配置")
    private Integer monitorNginxConfigId;

    /**
     * 获取父级路径
     * @return
     */
    public String getIncludeParentPath() {
        for (String s : monitorNginxHttpInclude.split(";")) {
            if(s.endsWith(".conf")) {
                return NginxDisAssembly.getFullPath(s);
            }
        }

        return EMPTY;

    }
}