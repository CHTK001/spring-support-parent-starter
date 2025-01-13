package com.chua.report.server.starter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.report.server.starter.entity.MonitorNginxConfig;
import com.chua.starter.mybatis.entity.Query;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;

/**
 * @author CH
 * @since 2024/12/29
 */
public interface MonitorNginxConfigService extends IService<MonitorNginxConfig> {

    /**
     * 根据Nginx配置ID生成配置字符串
     *  <pre>
     *      <code>
     *# 全局配置
     * user nginx nginx;  # 指定运行Nginx的用户和用户组为nginx
     * worker_processes auto;  # 自动设置工作进程数量，等于CPU核心数
     *
     * # 错误日志配置
     * error_log /var/log/nginx/error.log warn;  # 设置错误日志的路径和级别为warn
     *
     * # PID文件配置
     * pid /var/run/nginx.pid;  # 设置PID文件的存放位
     *
     * events {
     *      #设置每个工作进程的最大客户端连接数。这个指令通常与 worker_processes 指令结合使用，来计算整个 Nginx 服务器的最大并发连接数。
     *      worker_connections 1024;
     *      #指定使用哪种事件模型。Nginx 支持多种事件模型，如 epoll（Linux）、kqueue（BSD）、select 和 poll 等。通常，Nginx 会根据操作系统自动选择最佳的事件模型，但也可以手动指定。
     *      use epoll;
     *      #设置是否允许服务器在单个监听事件中接受多个连接。这可以减少 I/O 等待时间，提高性能。
     *      multi_accept on;
     *      #在某些情况下，可以设置为 on 来允许多个工作进程同时监听相同的端口。默认情况下，它是关闭的，以避免多个进程间的端口竞争
     *      accept_mutex on;
     *      #当 accept_mutex 被启用时，这个指令可以设置尝试获取互斥锁的延迟时间。
     *      accept_mutex_delay 10ms;
     *
     * }
     * http {
     *     limit_req_zone $binary_remote_addr zone=mylimit:10m rate=1r/s;
     *     proxy_cache_path /data/nginx/cache levels=1:2 keys_zone=my_cache:10m max_size=10g inactive=60m use_temp_path=off;
     *     # 定义缓存路径、目录结构、缓存区名称和大小、最大缓存大小、非活动数据清理时间，以及是否使用临时路径
     *
     *     upstream myapp1 {  # 定义一个名为myapp1的服务器组
     *         server backend1.example.com weight=5;  # 添加一个服务器，并设置权重为5
     *         server backend2.example.com;  # 添加另一个服务器，权重默认为1
     *         server backend3.example.com down;  # 将此服务器标记为down，不参与负载均衡
     *         server backup1.example.com backup;  # 将此服务器作为备份服务器
     *     }
     *
     *     server {
     *         listen 80;  # 监听80端口
     *
     *         location / {  # 匹配所有请求
     *             proxy_pass http://myapp1;  # 将请求转发到myapp1服务器组
     *             proxy_set_header Host $host;  # 设置请求头中的Host字段为原始请求的Host
     *             proxy_set_header X-Real-IP $remote_addr;  # 设置请求头中的X-Real-IP字段为客户端的真实IP地址
     *             proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;  # 设置请求头中的X-Forwarded-For字段，以记录原始请求和代理链的IP地址
     *             proxy_set_header X-Forwarded-Proto $scheme;  # 设置请求头中的X-Forwarded-Proto字段为原始请求的协议（http或https）
     *         }
     *     }
     *
     *     server {
     *          listen 80;  # 监听80端口（注意：WebSocket通常使用443端口并启用SSL，但这里为了示例保持简单）
     *
     *          location /ws {  # 匹配以/ws开头的请求
     *              proxy_pass http://websocket_backend;  # 转发请求到WebSocket后端服务器
     *              proxy_http_version 1.1;  # 代理时使用的HTTP版本
     *              proxy_set_header Upgrade $http_upgrade;  # 传递Upgrade头部以支持WebSocket
     *              proxy_set_header Connection "upgrade";  # 设置Connection头部为upgrade以支持WebSocket
     *              proxy_set_header Host $host;  # 传递Host头部
     *
     *              # 其他可能的配置，如处理WebSocket特有的超时、缓冲等
     *          }
     *      }
     *
     *     server {
     *          listen 443 ssl;  # 监听443端口，并启用SSL
     *          server_name example.com;  # 设置服务器名称
     *
     *          ssl_certificate /path/to/your/fullchain.pem;  # SSL证书文件路径
     *          ssl_certificate_key /path/to/your/privatekey.pem;  # SSL私钥文件路径
     *
     *          ssl_session_timeout 1d;  # SSL会话超时时间
     *          ssl_session_cache shared:MozSSL:10m;  # SSL会话缓存设置
     *          ssl_session_tickets off;  # 禁用SSL会话票证
     *
     *          ssl_protocols TLSv1.2 TLSv1.3;  # 启用的SSL/TLS协议版本
     *          ssl_prefer_server_ciphers on;  # 偏好使用服务器端的密码套件配置
     *
     *          ssl_ciphers '...';  # 使用的密码套件列表，这里省略了具体值
     *
     *          location / {
     *              # 这里可以配置如何处理HTTP请求，但通常对于HTTPS，主要配置在server块级别
     *          }
     *      }
     * }
     *      </code>
     *  </pre>
     * @param nginxConfigId Nginx配置的唯一标识符
     * @return 返回一个包含配置字符串的ReturnResult对象，其中包括了获取配置结果和配置字符串本身
     */
    Boolean createConfigString(Integer nginxConfigId);
    /**
     * 获取Nginx配置字符串
     */
    String getConfigString(Integer nginxConfigId);

    /**
     * 分析Nginx配置文件
     * @param file Nginx配置文件
     * @return 返回一个包含分析结果的对象，其中包括了分析结果和错误信息
     */
    Boolean analyzeConfig(MultipartFile file);

    /**
     * 备份Nginx配置
     * @param nginxConfig Nginx配置对象
     * @return 返回一个包含备份结果的对象，其中包括了备份结果和错误信息
     */
    Boolean backup(MonitorNginxConfig nginxConfig);

    /**
     * 停止Nginx配置
     * @param monitorNginxConfigId Nginx配置的唯一标识符
     * @return 返回一个包含停止结果的对象，其中包括了停止结果和错误信息
     */
    String stop(@NotNull(message = "不能为null") Integer monitorNginxConfigId);

    /**
     * 启动Nginx配置
     * @param monitorNginxConfigId Nginx配置的唯一标识符
     * @return 返回一个包含启动结果的对象，其中包括了启动结果和错误信息
     */
    String start(@NotNull(message = "不能为null") Integer monitorNginxConfigId);

    /**
     * 重启Nginx配置
     * @param monitorNginxConfigId Nginx配置的唯一标识符
     * @return 返回一个包含重启结果的对象，其中包括了重启结果和错误信息
     */
    String restart(@NotNull(message = "不能为null") Integer monitorNginxConfigId);

    /**
     * 分页查询Nginx配置
     * @param query 查询条件
     * @return 返回一个包含分页结果的对象，其中包括了分页结果和错误信息
     */
    ReturnPageResult<MonitorNginxConfig> pageForConfig(Query<MonitorNginxConfig> query);

    /**
     * 更新Nginx配置
     * @param nginxConfig Nginx配置对象
     * @return 返回一个包含更新结果的对象，其中包括了更新结果和错误信息
     */
    Boolean update(MonitorNginxConfig nginxConfig);

    /**
     * 保存Nginx配置
     * @param nginxConfig Nginx配置对象
     * @return 返回一个包含保存结果的对象，其中包括了保存结果和错误信息
     */
    MonitorNginxConfig saveForConfig(MonitorNginxConfig nginxConfig);

    /**
     * 获取Nginx配置
     * @param monitorNginxConfigId Nginx配置的唯一标识符
     * @return 返回一个包含获取结果的对象，其中包括了获取结果和错误信息
     */
    MonitorNginxConfig getForConfig(Integer monitorNginxConfigId);

    /**
     * 分析Nginx配置
     * @param nginxConfigId Nginx配置的唯一标识符
     * @return 返回一个包含分析结果的对象，其中包括了分析结果和错误信息
     */
    Boolean analyzeConfig(Integer nginxConfigId);
}
