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

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 *
 * @since 2024/12/29
 * @author CH    
 */

/**
 * 地址解析;\nwebsocket:\nlocation /ws {  # 匹配以/ws开头的请求\nproxy_pass http://websocket_backend;  # 转发请求到WebSocket后端服务器  \nproxy_http_version 1.1;  # 代理时使用的HTTP版本  \nproxy_set_header Upgrade $http_upgrade;  # 传递Upgrade头部以支持WebSocket  \nproxy_set_header Connection upgrade;  # 设置Connection头部为upgrade以支持WebSocket  \nproxy_set_header Host $host;  # 传递Host头部  \n # 其他可能的配置，如处理WebSocket特有的超时、缓冲等  \n }
 */
@ApiModel(description = "地址解析;\nwebsocket:\nlocation /ws {  # 匹配以/ws开头的请求\nproxy_pass http://websocket_backend;  # 转发请求到WebSocket后端服务器  \nproxy_http_version 1.1;  # 代理时使用的HTTP版本  \nproxy_set_header Upgrade $http_upgrade;  # 传递Upgrade头部以支持WebSocket  \nproxy_set_header Connection upgrade;  # 设置Connection头部为upgrade以支持WebSocket  \nproxy_set_header Host $host;  # 传递Host头部  \n # 其他可能的配置，如处理WebSocket特有的超时、缓冲等  \n }")
@Schema(description = "地址解析;\nwebsocket:\nlocation /ws {  # 匹配以/ws开头的请求\nproxy_pass http://websocket_backend;  # 转发请求到WebSocket后端服务器  \nproxy_http_version 1.1;  # 代理时使用的HTTP版本  \nproxy_set_header Upgrade $http_upgrade;  # 传递Upgrade头部以支持WebSocket  \nproxy_set_header Connection upgrade;  # 设置Connection头部为upgrade以支持WebSocket  \nproxy_set_header Host $host;  # 传递Host头部  \n # 其他可能的配置，如处理WebSocket特有的超时、缓冲等  \n }")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "monitor_nginx_http_server_location")
public class MonitorNginxHttpServerLocation extends SysBase implements Serializable {
    @TableId(value = "monitor_nginx_http_server_location_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorNginxHttpServerLocationId;

    /**
     * monitor_nginx_http_server表ID
     */
    @TableField(value = "monitor_nginx_http_server_id")
    @ApiModelProperty(value = "monitor_nginx_http_server表ID")
    @Schema(description = "monitor_nginx_http_server表ID")
    private Integer monitorNginxHttpServerId;

    /**
     * 地址名称；location
     */
    @TableField(value = "monitor_nginx_http_server_location_name")
    @ApiModelProperty(value = "地址名称；location ")
    @Schema(description = "地址名称；location ")
    @Size(max = 255, message = "地址名称；location 最大长度要小于 255")
    private String monitorNginxHttpServerLocationName;

    /**
     * proxy_pass http://192.168.247.129:8080;
     */
    @TableField(value = "monitor_nginx_http_server_location_proxy_pass")
    @ApiModelProperty(value = "proxy_pass http://192.168.247.129:8080;")
    @Schema(description = "proxy_pass http://192.168.247.129:8080;")
    @Size(max = 255, message = "proxy_pass http://192.168.247.129:8080;最大长度要小于 255")
    private String monitorNginxHttpServerLocationProxyPass;

    /**
     * proxy_cache my_cache;  # 启用名为my_cache的缓存
     */
    @TableField(value = "monitor_nginx_http_server_location_proxy_cache")
    @ApiModelProperty(value = "proxy_cache my_cache;  # 启用名为my_cache的缓存")
    @Schema(description = "proxy_cache my_cache;  # 启用名为my_cache的缓存")
    @Size(max = 255, message = "proxy_cache my_cache;  # 启用名为my_cache的缓存最大长度要小于 255")
    private String monitorNginxHttpServerLocationProxyCache;

    /**
     * proxy_cache_valid 200 302 10m;  # 对HTTP 200和302响应缓存10分钟\nproxy_cache_valid 404      1m;  # 对HTTP 404响应缓存1分钟
     */
    @TableField(value = "monitor_nginx_http_server_location_proxy_cache_valid")
    @ApiModelProperty(value = "proxy_cache_valid 200 302 10m;  # 对HTTP 200和302响应缓存10分钟\nproxy_cache_valid 404      1m;  # 对HTTP 404响应缓存1分钟 ")
    @Schema(description = "proxy_cache_valid 200 302 10m;  # 对HTTP 200和302响应缓存10分钟\nproxy_cache_valid 404      1m;  # 对HTTP 404响应缓存1分钟 ")
    @Size(max = 255, message = "proxy_cache_valid 200 302 10m;  # 对HTTP 200和302响应缓存10分钟\nproxy_cache_valid 404      1m;  # 对HTTP 404响应缓存1分钟 最大长度要小于 255")
    private String monitorNginxHttpServerLocationProxyCacheValid;

    /**
     * proxy_cache_methods GET HEAD;  # 对哪些方法进行缓存
     */
    @TableField(value = "monitor_nginx_http_server_location_proxy_cache_methods")
    @ApiModelProperty(value = "proxy_cache_methods GET HEAD;  # 对哪些方法进行缓存")
    @Schema(description = "proxy_cache_methods GET HEAD;  # 对哪些方法进行缓存")
    @Size(max = 255, message = "proxy_cache_methods GET HEAD;  # 对哪些方法进行缓存最大长度要小于 255")
    private String monitorNginxHttpServerLocationProxyCacheMethods;

    /**
     * client_max_body_size       10m;   #允许客户端请求的最大单文件字节数
     */
    @TableField(value = "monitor_nginx_http_server_location_client_max_body_size")
    @ApiModelProperty(value = "client_max_body_size       10m;   #允许客户端请求的最大单文件字节数")
    @Schema(description = "client_max_body_size       10m;   #允许客户端请求的最大单文件字节数")
    @Size(max = 11, message = "client_max_body_size       10m;   #允许客户端请求的最大单文件字节数最大长度要小于 11")
    private String monitorNginxHttpServerLocationClientMaxBodySize;

    /**
     * client_body_buffer_size    128k;  #缓冲区代理缓冲用户端请求的最大字节数
     */
    @TableField(value = "monitor_nginx_http_server_location_client_body_buffer_size")
    @ApiModelProperty(value = "client_body_buffer_size    128k;  #缓冲区代理缓冲用户端请求的最大字节数")
    @Schema(description = "client_body_buffer_size    128k;  #缓冲区代理缓冲用户端请求的最大字节数")
    private Double monitorNginxHttpServerLocationClientBodyBufferSize;

    /**
     * proxy_read_timeout 1000;
     */
    @TableField(value = "monitor_nginx_http_server_location_proxy_read_timeout")
    @ApiModelProperty(value = "proxy_read_timeout 1000;")
    @Schema(description = "proxy_read_timeout 1000;")
    private Integer monitorNginxHttpServerLocationProxyReadTimeout;

    /**
     * proxy_send_timeout 300;
     */
    @TableField(value = "monitor_nginx_http_server_location_proxy_send_timeout")
    @ApiModelProperty(value = "proxy_send_timeout 300;")
    @Schema(description = "proxy_send_timeout 300;")
    private Integer monitorNginxHttpServerLocationProxySendTimeout;

    /**
     * nginx跟后端服务器连接超时时间(代理连接超时)
     */
    @TableField(value = "monitor_nginx_http_server_location_proxy_connect_timeout")
    @ApiModelProperty(value = "nginx跟后端服务器连接超时时间(代理连接超时)")
    @Schema(description = "nginx跟后端服务器连接超时时间(代理连接超时)")
    private Integer monitorNginxHttpServerLocationProxyConnectTimeout;

    /**
     * root   html;
     */
    @TableField(value = "monitor_nginx_http_server_location_root")
    @ApiModelProperty(value = "root   html;")
    @Schema(description = "root   html;")
    @Size(max = 255, message = "root   html;最大长度要小于 255")
    private String monitorNginxHttpServerLocationRoot;

    /**
     * #定义首页索引文件的名称\n# index  index.html index.htm;
     */
    @TableField(value = "monitor_nginx_http_server_location_index")
    @ApiModelProperty(value = "#定义首页索引文件的名称\n# index  index.html index.htm;")
    @Schema(description = "#定义首页索引文件的名称\n# index  index.html index.htm;")
    @Size(max = 255, message = "#定义首页索引文件的名称\n# index  index.html index.htm;最大长度要小于 255")
    private String monitorNginxHttpServerLocationIndex;

    /**
     * 三.总的来说,index的优先级比try_files高,请求会先去找index配置,这里最后一个参数必须存在\ntry_files $uri $uri/ /index.html
     */
    @TableField(value = "monitor_nginx_http_server_location_try_files")
    @ApiModelProperty(value = "三.总的来说,index的优先级比try_files高,请求会先去找index配置,这里最后一个参数必须存在\ntry_files $uri $uri/ /index.html")
    @Schema(description = "三.总的来说,index的优先级比try_files高,请求会先去找index配置,这里最后一个参数必须存在\ntry_files $uri $uri/ /index.html")
    @Size(max = 255, message = "三.总的来说,index的优先级比try_files高,请求会先去找index配置,这里最后一个参数必须存在\ntry_files $uri $uri/ /index.html最大长度要小于 255")
    private String monitorNginxHttpServerLocationTryFiles;

    /**
     * if ($request_method = 'OPTIONS') {\nreturn 204;\n }
     */
    @TableField(value = "monitor_nginx_http_server_location_condition")
    @ApiModelProperty(value = "if ($request_method = 'OPTIONS') {\nreturn 204;\n }")
    @Schema(description = "if ($request_method = 'OPTIONS') {\nreturn 204;\n }")
    private String monitorNginxHttpServerLocationCondition;

    /**
     * 限流； limit_req zone=mylimit burst=5 nodelay;;
     */
    @TableField(value = "monitor_nginx_http_server_location_limit_req")
    @ApiModelProperty(value = "限流； limit_req zone=mylimit burst=5 nodelay;;")
    @Schema(description = "限流； limit_req zone=mylimit burst=5 nodelay;;")
    @Size(max = 255, message = "限流； limit_req zone=mylimit burst=5 nodelay;;最大长度要小于 255")
    private String monitorNginxHttpServerLocationLimitReq;
}