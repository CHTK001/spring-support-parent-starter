package com.chua.starter.monitor.support.nginx;

import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_EVENTS;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_HTTP;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_IF;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_LIMIT_EXCEPT;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_LOCATION;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_MAIN;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_MAP;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_SERVER;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_STREAM;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_TYPES;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.SCOPE_UPSTREAM;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_DURATION;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_ENUM;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_FLAG;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_HOST;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_LIST;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_NUMBER;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_PATH;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_REGEX;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_SIZE;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_STRING;
import static com.chua.starter.monitor.support.nginx.NginxDirectiveMeta.TYPE_URL;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * nginx 指令元数据注册表。
 *
 * <p>为「UI 模式」提供指令语义：作用域、参数类型、枚举取值、是否可重复、是否必填、
 * 中文说明与示例。前端据此决定渲染数字框 / 下拉 / 开关 / 文本域，并给出新手提示。</p>
 *
 * <p><strong>覆盖策略</strong>：覆盖日常运维与建站绝大多数指令，并对危险指令
 * （{@code worker_processes auto}、{@code daemon} 等）给出说明。<strong>不追求覆盖
 * 第三方模块的全部指令</strong>——缺项不会造成任何配置丢失，未登记指令仍可解析、
 * 可展示、可原样保存，只是 UI 退化为通用文本输入。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class NginxDirectiveRegistry {

    /**
     * server + location
     */
    private static final Set<String> SRV_LOC = Set.of(SCOPE_SERVER, SCOPE_LOCATION);

    /**
     * http + server + location
     */
    private static final Set<String> HTTP_SRV_LOC = Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION);

    /**
     * http + server
     */
    private static final Set<String> HTTP_SRV = Set.of(SCOPE_HTTP, SCOPE_SERVER);

    /**
     * 指令元数据表
     */
    private static final Map<String, NginxDirectiveMeta> TABLE = new LinkedHashMap<>();

    static {
        buildMain();
        buildEvents();
        buildHttpCore();
        buildHttpServer();
        buildHttpLocation();
        buildProxy();
        buildUpstream();
        buildStream();
        buildTypes();
        buildMap();
        buildLimit();
    }

    /**
     * 工具类不允许实例化。
     */
    private NginxDirectiveRegistry() {
    }

    /**
     * 取全部元数据。
     *
     * @return 元数据集合
     */
    public static Collection<NginxDirectiveMeta> all() {
        return TABLE.values();
    }

    /**
     * 按指令名查元数据。
     *
     * @param name 指令名
     * @return 元数据，未登记时为 null
     */
    public static NginxDirectiveMeta find(String name) {
        return name == null ? null : TABLE.get(name);
    }

    /**
     * 取某作用域下可用的指令。
     *
     * @param scope 作用域
     * @return 元数据列表
     */
    public static List<NginxDirectiveMeta> byScope(String scope) {
        if (scope == null) {
            return List.of();
        }
        return TABLE.values().stream().filter(m -> m.scopes().contains(scope)).toList();
    }

    /**
     * 取区块名对应的作用域标识。
     *
     * @param blockName 区块名
     * @return 作用域标识，未知区块返回 http
     */
    public static String scopeOfBlock(String blockName) {
        if (blockName == null) {
            return SCOPE_HTTP;
        }
        return switch (blockName) {
            case "events" -> SCOPE_EVENTS;
            case "http" -> SCOPE_HTTP;
            case "server" -> SCOPE_SERVER;
            case "location" -> SCOPE_LOCATION;
            case "upstream" -> SCOPE_UPSTREAM;
            case "stream" -> SCOPE_STREAM;
            case "types" -> SCOPE_TYPES;
            case "map", "geo", "split_clients" -> SCOPE_MAP;
            case "if" -> SCOPE_IF;
            case "limit_except" -> SCOPE_LIMIT_EXCEPT;
            default -> SCOPE_HTTP;
        };
    }

    /**
     * 登记一条指令。
     *
     * @param meta 元数据
     */
    private static void put(NginxDirectiveMeta meta) {
        TABLE.put(meta.name(), meta);
    }

    // ==================== 主配置 ====================

    /**
     * 主配置层指令。
     */
    private static void buildMain() {
        put(NginxDirectiveMeta.of("user", Set.of(SCOPE_MAIN), TYPE_STRING, false, false,
                "以哪个用户身份运行 worker 进程。填错会导致权限不足或安全风险",
                "www-data"));
        put(NginxDirectiveMeta.enumOf("worker_processes", Set.of(SCOPE_MAIN), false, false,
                "worker 进程数量。推荐 auto（按 CPU 核数自动）", "auto",
                "auto", "1", "2", "4", "8", "16"));
        put(NginxDirectiveMeta.enumOf("worker_connections", Set.of(SCOPE_EVENTS), false, false,
                "单个 worker 最多同时处理的连接数", "1024",
                "128", "256", "512", "1024", "2048", "4096", "8192"));
        put(NginxDirectiveMeta.of("daemon", Set.of(SCOPE_MAIN), TYPE_FLAG, false, false,
                "是否以守护进程（后台）方式运行", "on"));
        put(NginxDirectiveMeta.of("master_process", Set.of(SCOPE_MAIN), TYPE_FLAG, false, false,
                "是否启用 master/worker 架构。关闭后 nginx 不再支持平滑重载", "on"));
        put(NginxDirectiveMeta.of("error_log", Set.of(SCOPE_MAIN, SCOPE_HTTP, SCOPE_SERVER, SCOPE_STREAM),
                TYPE_PATH, false, false, "错误日志文件路径，stderr 表示输出到标准错误", "/var/log/nginx/error.log"));
        put(NginxDirectiveMeta.of("pid", Set.of(SCOPE_MAIN), TYPE_PATH, false, false,
                "存放 master 进程 PID 的文件", "/var/run/nginx.pid"));
        put(NginxDirectiveMeta.enumOf("worker_rlimit_nofile", Set.of(SCOPE_MAIN), false, false,
                "worker 进程可打开的最大文件描述符数", "65535", "1024", "4096", "65535", "1048576"));
        put(NginxDirectiveMeta.of("worker_priority", Set.of(SCOPE_MAIN), TYPE_NUMBER, false, false,
                "worker 进程优先级（负值更高）", "0"));
        put(NginxDirectiveMeta.of("worker_cpu_affinity", Set.of(SCOPE_MAIN), TYPE_STRING, true, false,
                "把 worker 绑定到指定 CPU 核，逗号分隔", "0,1"));
        put(NginxDirectiveMeta.enumOf("timer_resolution", Set.of(SCOPE_MAIN), false, false,
                "计时器精度，减小可提升定时器精度但增加开销", "100ms",
                "1ms", "10ms", "100ms", "1s"));
        put(NginxDirectiveMeta.of("lock_file", Set.of(SCOPE_MAIN), TYPE_PATH, false, false,
                "接受 UDP 连接的锁文件路径", "/var/lock/nginx.lock"));
        put(NginxDirectiveMeta.of("working_directory", Set.of(SCOPE_MAIN), TYPE_PATH, false, false,
                "worker 进程的工作目录", "/"));
        put(NginxDirectiveMeta.of("env", Set.of(SCOPE_MAIN), TYPE_STRING, true, false,
                "让 worker 进程继承系统环境变量", "TZ=Asia/Shanghai"));
    }

    /**
     * events 层指令。
     */
    private static void buildEvents() {
        put(NginxDirectiveMeta.enumOf("use", Set.of(SCOPE_EVENTS), false, false,
                "事件模型。Linux 推荐 epoll，BSD 推荐 kqueue", "epoll", "epoll", "kqueue", "select", "poll", "rtsig"));
        put(NginxDirectiveMeta.enumOf("multi_accept", Set.of(SCOPE_EVENTS), false, false,
                "一次事件循环是否接受所有新连接", "on", "on", "off"));
        put(NginxDirectiveMeta.of("accept_mutex", Set.of(SCOPE_EVENTS), TYPE_FLAG, false, false,
                "是否启用互斥锁串行化 accept。高并发下建议关闭", "on"));
        put(NginxDirectiveMeta.of("worker_connections", Set.of(SCOPE_EVENTS), TYPE_NUMBER, false, false,
                "单个 worker 最多同时处理的连接数", "1024"));
    }

    /**
     * http 层核心指令。
     */
    private static void buildHttpCore() {
        put(NginxDirectiveMeta.of("include", Set.of(SCOPE_MAIN, SCOPE_EVENTS, SCOPE_HTTP, SCOPE_SERVER,
                SCOPE_LOCATION, SCOPE_UPSTREAM, SCOPE_STREAM, SCOPE_TYPES, SCOPE_MAP, SCOPE_LIMIT_EXCEPT,
                SCOPE_IF), TYPE_PATH, true, false,
                "引入另一个配置文件，常用于拆分站点配置", "conf.d/*.conf"));
        put(NginxDirectiveMeta.of("default_type", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_STRING,
                false, false, "未匹配到扩展名时的默认 MIME 类型", "application/octet-stream"));
        put(NginxDirectiveMeta.of("sendfile", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "用 sendfile 系统调用发送文件，减少用户态拷贝", "on"));
        put(NginxDirectiveMeta.of("tcp_nopush", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "启用 TCP_CORK 攒包发送，发送大文件时更高效", "on"));
        put(NginxDirectiveMeta.of("tcp_nodelay", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "禁用 Nagle 算法，降低小包延迟", "on"));
        put(NginxDirectiveMeta.of("keepalive_timeout", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_DURATION, false, false, "长连接保持时间", "65s"));
        put(NginxDirectiveMeta.of("keepalive_requests", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_NUMBER, false, false, "长连接最多处理多少请求后关闭", "1000"));
        put(NginxDirectiveMeta.of("keepalive_time", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_DURATION, false, false, "长连接最长存活时间（1.19.10+）", "1h"));
        put(NginxDirectiveMeta.of("keepalive_disable", Set.of(SCOPE_HTTP, SCOPE_SERVER), TYPE_STRING, false,
                false, "对指定 User-Agent 禁用长连接，如 Safari 用 none", "msie6"));
        put(NginxDirectiveMeta.of("client_max_body_size", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_SIZE, false, false, "允许上传的最大请求体大小。默认 1m，传大文件必须调大", "20m"));
        put(NginxDirectiveMeta.of("client_body_buffer_size", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_SIZE, false, false, "请求体缓存大小，超出则写入临时文件", "8k"));
        put(NginxDirectiveMeta.of("client_body_timeout", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_DURATION, false, false, "读取请求体内容的超时", "60s"));
        put(NginxDirectiveMeta.of("client_header_timeout", Set.of(SCOPE_HTTP, SCOPE_SERVER), TYPE_DURATION,
                false, false, "读取请求头的超时。防慢速攻击的关键", "60s"));
        put(NginxDirectiveMeta.of("client_header_buffer_size", Set.of(SCOPE_HTTP, SCOPE_SERVER), TYPE_SIZE,
                false, false, "请求头缓冲区大小", "1k"));
        put(NginxDirectiveMeta.of("large_client_header_buffers", Set.of(SCOPE_HTTP, SCOPE_SERVER), TYPE_SIZE,
                false, false, "单个大请求头缓冲区大小与个数，如 4 8k", "4 8k"));
        put(NginxDirectiveMeta.of("client_body_in_file_only", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "无论大小都把请求体先落盘", "on"));
        put(NginxDirectiveMeta.of("client_body_temp_path", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_PATH, false, false, "请求体临时文件目录", "/var/cache/nginx/client_temp"));
        put(NginxDirectiveMeta.of("client_body_in_single_buffer", Set.of(SCOPE_HTTP, SCOPE_SERVER,
                SCOPE_LOCATION), TYPE_FLAG, false, false, "把整个请求体缓存在一个缓冲区", "on"));
        put(NginxDirectiveMeta.of("lingering_close", Set.of(SCOPE_HTTP, SCOPE_SERVER), TYPE_STRING, false, false,
                "请求结束后连接关闭策略：always / on / off", "off"));
        put(NginxDirectiveMeta.of("lingering_time", Set.of(SCOPE_HTTP, SCOPE_SERVER), TYPE_DURATION, false, false,
                "延迟关闭连接前的等待时间", "30s"));
        put(NginxDirectiveMeta.of("lingering_timeout", Set.of(SCOPE_HTTP, SCOPE_SERVER), TYPE_DURATION, false, false,
                "延迟关闭连接的超时上限", "5s"));
        put(NginxDirectiveMeta.of("reset_timedout_connection", Set.of(SCOPE_HTTP, SCOPE_SERVER), TYPE_FLAG,
                false, false, "用 RST 包重置超时连接", "on"));
        put(NginxDirectiveMeta.enumOf("server_tokens", HTTP_SRV_LOC, false, false,
                "错误页与响应头是否暴露 nginx 版本号。建议 off 以减少信息泄露", "off",
                "on", "off", "build", "string"));
        put(NginxDirectiveMeta.enumOf("absolute_redirect", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                false, false, "是否总是用绝对地址做重定向", "on", "on", "off"));
        put(NginxDirectiveMeta.of("port_in_redirect", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "重定向时是否带上监听端口", "on"));
        put(NginxDirectiveMeta.of("msie_padding", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "是否为 IE6/7 的错误页补空白对齐", "on"));
        put(NginxDirectiveMeta.of("log_not_found", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "访问不存在的文件时是否写访问日志", "on"));
        put(NginxDirectiveMeta.of("log_subrequest", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "是否记录子请求日志", "off"));
        put(NginxDirectiveMeta.of("recursive_error_pages", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "是否用本地错误页替换上游返回的错误页", "off"));
        put(NginxDirectiveMeta.of("error_page", HTTP_SRV_LOC, TYPE_LIST, true, false,
                "错误码到页面的重定向，如 404 /404.html；也支持 =响应码 形式用于覆盖上游状态", "404 /404.html"));
        put(NginxDirectiveMeta.of("access_log", HTTP_SRV_LOC, TYPE_PATH, true, false,
                "访问日志路径；off 表示关闭该层访问日志", "/var/log/nginx/access.log"));
        put(NginxDirectiveMeta.of("open_log_file_cache", HTTP_SRV_LOC, TYPE_STRING, false, false,
                "日志文件描述符缓存参数", "max=1000 inactive=20s"));
        put(NginxDirectiveMeta.of("gzip", HTTP_SRV_LOC, TYPE_FLAG, false, false, "是否启用 gzip 压缩", "on"));
        put(NginxDirectiveMeta.of("gzip_types", HTTP_SRV_LOC, TYPE_STRING, false, false,
                "需要压缩的 MIME 类型", "text/plain text/css application/javascript"));
        put(NginxDirectiveMeta.of("gzip_min_length", HTTP_SRV_LOC, TYPE_SIZE, false, false,
                "小于该大小的响应不压缩", "1k"));
        put(NginxDirectiveMeta.of("gzip_comp_level", HTTP_SRV_LOC, TYPE_NUMBER, false, false,
                "压缩级别 1-9，越高越省流量但越耗 CPU", "6"));
        put(NginxDirectiveMeta.of("gzip_proxied", HTTP_SRV_LOC, TYPE_STRING, false, false,
                "对哪些类型的代理响应启用压缩", "any"));
        put(NginxDirectiveMeta.of("gzip_vary", HTTP_SRV_LOC, TYPE_FLAG, false, false,
                "是否添加 Vary: Accept-Encoding 响应头", "on"));
        put(NginxDirectiveMeta.of("gzip_disable", HTTP_SRV_LOC, TYPE_STRING, true, false,
                "对指定 User-Agent 禁用压缩", "msie6"));
        put(NginxDirectiveMeta.of("gzip_http_version", HTTP_SRV_LOC, TYPE_STRING, false, false,
                "只对指定 HTTP 版本压缩", "1.1"));
        put(NginxDirectiveMeta.of("gzip_static", HTTP_SRV_LOC, TYPE_FLAG, false, false,
                "优先读取同名的 .gz 预压缩文件", "on"));
        put(NginxDirectiveMeta.of("send_timeout", HTTP_SRV_LOC, TYPE_DURATION, false, false,
                "向客户端发送响应的超时", "60s"));
    }

    /**
     * server 层指令。
     */
    private static void buildHttpServer() {
        put(NginxDirectiveMeta.of("server_name", Set.of(SCOPE_SERVER), TYPE_LIST, false, false,
                "本站点匹配的域名或 IP，多个用空格分隔。支持通配符与正则",
                "www.example.com example.com"));
        put(NginxDirectiveMeta.of("listen", SRV_LOC, TYPE_STRING, true, false,
                "监听地址与端口。80 为 HTTP，443 为 HTTPS；可追加参数 ssl / http2 / reuseport / backlog。"
                        + "同一 server 写多条 listen 表示同时监听多个端口",
                "443 ssl http2"));
        put(NginxDirectiveMeta.of("server_name_in_redirect", Set.of(SCOPE_SERVER), TYPE_FLAG, false, false,
                "重定向时是否使用 server_name 里的域名", "off"));
        put(NginxDirectiveMeta.of("root", SRV_LOC, TYPE_PATH, false, false,
                "站点根目录，静态文件从这里取", "/var/www/html"));
        put(NginxDirectiveMeta.of("alias", Set.of(SCOPE_LOCATION), TYPE_PATH, false, false,
                "路径别名。用法与 root 不同：location /a/ 配 alias /data/ 时 /a/x 映射到 /data/x", "/data/"));
        put(NginxDirectiveMeta.of("index", SRV_LOC, TYPE_LIST, true, false,
                "访问目录时的默认文件，按顺序尝试", "index.html index.htm"));
        put(NginxDirectiveMeta.of("try_files", Set.of(SCOPE_SERVER, SCOPE_LOCATION), TYPE_LIST, false, false,
                "按顺序尝试文件/路径，都失败则用最后一个兜底。$uri 是当前 URI，$uri/ 视为目录",
                "$uri $uri/ /index.html"));
        put(NginxDirectiveMeta.of("autoindex", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "是否开启目录列表。生产环境建议关闭", "off"));
        put(NginxDirectiveMeta.of("autoindex_exact_size", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "目录列表是否显示精确字节数", "off"));
        put(NginxDirectiveMeta.of("autoindex_localtime", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "目录列表是否显示本地时间", "on"));
        put(NginxDirectiveMeta.of("charset", HTTP_SRV_LOC, TYPE_STRING, false, false,
                "追加响应头字符集，如 utf-8", "utf-8"));
        put(NginxDirectiveMeta.of("source_charset", HTTP_SRV_LOC, TYPE_STRING, false, false,
                "声明源文件字符集，用于转码", "utf-8"));
        put(NginxDirectiveMeta.of("override_charset", HTTP_SRV_LOC, TYPE_STRING, false, false,
                "覆盖响应中的字符集", "utf-8"));
        put(NginxDirectiveMeta.of("types", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG, false, false,
                "是否引入 types 映射表", ""));
        put(NginxDirectiveMeta.of("default_type", HTTP_SRV_LOC, TYPE_STRING, false, false,
                "未识别扩展名时的默认类型", "application/octet-stream"));
        put(NginxDirectiveMeta.of("if_modified_since", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_STRING,
                false, false, "按 Last-Modified 判断是否过期（exact / before）", "exact"));
        put(NginxDirectiveMeta.of("max_ranges", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "是否允许 Range 请求（断点续传、大文件分段下载）", "on"));
        put(NginxDirectiveMeta.of("expires", SRV_LOC, TYPE_DURATION, false, false,
                "给响应添加 Expires 头与 Cache-Control", "30d"));
        put(NginxDirectiveMeta.of("add_header", HTTP_SRV_LOC, TYPE_STRING, true, false,
                "向响应追加自定义响应头。带 always 才对所有状态码生效", "X-Frame-Options SAMEORIGIN always"));
        put(NginxDirectiveMeta.of("add_trailer", HTTP_SRV_LOC, TYPE_STRING, true, false,
                "在响应末尾追加 trailer 字段", "Expires"));
        put(NginxDirectiveMeta.of("etag", HTTP_SRV_LOC, TYPE_FLAG, false, false,
                "是否生成 ETag 头", "on"));
        put(NginxDirectiveMeta.of("limit_rate", SRV_LOC, TYPE_SIZE, false, false,
                "限制单个连接的读写速率。0 表示不限制", "0"));
        put(NginxDirectiveMeta.of("limit_rate_after", SRV_LOC, TYPE_SIZE, false, false,
                "超过该数据量后才启用限速", "0"));
        put(NginxDirectiveMeta.enumOf("satisfy", Set.of(SCOPE_LOCATION), false, false,
                "access 模块与 auth 模块的配合方式：all 需同时满足，any 满足其一即可", "all",
                "all", "any"));
        put(NginxDirectiveMeta.of("internal", Set.of(SCOPE_LOCATION), TYPE_FLAG, false, false,
                "标记为内部 location，只能被内部重定向访问，不能被外部直接请求", ""));
        put(NginxDirectiveMeta.of("return", SRV_LOC, TYPE_STRING, false, false,
                "直接返回状态码或重定向，如 301 https://$host$request_uri", "301 https://$host$request_uri"));
        put(NginxDirectiveMeta.of("rewrite", SRV_LOC, TYPE_REGEX, false, false,
                "正则重写。规则里可用 $1..$9 捕获组，末尾加 flag 如 last / break / redirect",
                "^/old/(.*)$ /new/$1 last"));
        put(NginxDirectiveMeta.of("rewrite_log", HTTP_SRV_LOC, TYPE_FLAG, false, false,
                "是否把重写过程写进错误日志（用于排查）", "off"));
        put(NginxDirectiveMeta.of("set", HTTP_SRV_LOC, TYPE_STRING, true, false,
                "设置一个可在后续指令里引用的变量", "$scheme http"));
        put(NginxDirectiveMeta.of("break", HTTP_SRV_LOC, TYPE_FLAG, false, false,
                "跳出当前 rewrite 指令序列", ""));
        put(NginxDirectiveMeta.of("msie_refresh", Set.of(SCOPE_LOCATION), TYPE_FLAG, false, false,
                "对 IE6/7 用 meta 刷新重定向而非 302", "off"));
        put(NginxDirectiveMeta.of("connection_pool_size", Set.of(SCOPE_HTTP, SCOPE_SERVER), TYPE_SIZE, false,
                false, "每个上游连接池保留的空闲连接数", "64k"));
        put(NginxDirectiveMeta.of("lingering", Set.of(SCOPE_SERVER), TYPE_FLAG, false, false,
                "是否启用延迟关闭", "on"));
        put(NginxDirectiveMeta.enumOf("aio", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), false,
                false, "异步 I/O 模型（需编译时带 --with-aio）", "off",
                "off", "on", "threads"));
        put(NginxDirectiveMeta.of("directio", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_SIZE, false,
                false, "大于该大小时用 direct I/O 绕过页缓存", "512k"));
        put(NginxDirectiveMeta.of("open_file_cache", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "是否缓存文件元信息以减少系统调用", "on"));
        put(NginxDirectiveMeta.of("open_file_cache_valid", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_DURATION, false, false, "文件元信息缓存有效期", "60s"));
        put(NginxDirectiveMeta.of("open_file_cache_min_uses", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_NUMBER, false, false, "文件被访问多少次后才开始缓存", "1"));
        put(NginxDirectiveMeta.of("open_file_cache_errors", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "是否缓存文件错误（404 等）", "on"));
        put(NginxDirectiveMeta.of("output_buffers", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_STRING,
                false, false, "用于输出响应的缓冲区数量与大小（语法：<number> <size>，如 2 32k）", "2 32k"));
        put(NginxDirectiveMeta.of("postpone_output", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_SIZE,
                false, false, "数据累积到该大小才真正发给客户端", "1460"));
        put(NginxDirectiveMeta.of("read_ahead", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_SIZE, false,
                false, "Linux 下的预读大小", "64k"));
    }

    /**
     * location 层指令。
     */
    private static void buildHttpLocation() {
        put(NginxDirectiveMeta.of("limit_except", Set.of(SCOPE_LOCATION), TYPE_LIST, false, false,
                "限制该 location 下允许的 HTTP 方法，如 GET POST", "GET POST"));
        put(NginxDirectiveMeta.of("allow", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION, SCOPE_LIMIT_EXCEPT),
                TYPE_STRING, true, false, "允许的来源：IP、CIDR、unix:、all", "10.0.0.0/8"));
        put(NginxDirectiveMeta.of("deny", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION, SCOPE_LIMIT_EXCEPT),
                TYPE_STRING, true, false, "拒绝的来源，规则按顺序命中即生效", "all"));
    }

    /**
     * 反向代理与负载均衡相关指令。
     */
    private static void buildProxy() {
        put(NginxDirectiveMeta.of("proxy_pass", Set.of(SCOPE_LOCATION, SCOPE_IF, SCOPE_LIMIT_EXCEPT), TYPE_URL,
                false, false,
                "把请求转发到后端。末尾带 / 会剥离 location 前缀，不带 / 则原样透传路径",
                "http://backend"));
        put(NginxDirectiveMeta.of("proxy_set_header", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_STRING,
                true, false, "设置转发给后端的请求头。Host 默认被改写，常需显式指定",
                "Host $host"));
        put(NginxDirectiveMeta.of("proxy_http_version", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_STRING, false, false, "转发使用的 HTTP 版本", "1.1"));
        put(NginxDirectiveMeta.of("proxy_connect_timeout", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_DURATION, false, false, "连接后端建立连接的超时", "60s"));
        put(NginxDirectiveMeta.of("proxy_send_timeout", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_DURATION, false, false, "向后端发送请求的超时", "60s"));
        put(NginxDirectiveMeta.of("proxy_read_timeout", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_DURATION, false, false,
                "等待后端返回响应的超时。跑批/长任务要调大", "60s"));
        put(NginxDirectiveMeta.of("proxy_send_lowat", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_SIZE,
                false, false, "低水位：缓冲区低于该值时停止向下游发送", "0"));
        put(NginxDirectiveMeta.of("proxy_buffering", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "是否缓冲后端响应。关闭可让响应更快流出", "on"));
        put(NginxDirectiveMeta.of("proxy_request_buffering", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "是否缓冲转发给后端的请求体", "on"));
        put(NginxDirectiveMeta.of("proxy_buffer_size", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_SIZE,
                false, false, "缓冲区大小，需容纳单个响应头", "4k"));
        put(NginxDirectiveMeta.of("proxy_buffers", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_STRING,
                false, false, "缓冲区数量与单块大小（语法：<number> <size>，如 8 4k）", "8 4k"));
        put(NginxDirectiveMeta.of("proxy_busy_buffers_size", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_SIZE, false, false, "上游正在发数据时允许转发的缓冲区总量上限", "8k"));
        put(NginxDirectiveMeta.of("proxy_max_temp_file_size", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_SIZE, false, false, "临时文件最大大小，0 表示禁用临时文件", "1024m"));
        put(NginxDirectiveMeta.of("proxy_temp_file_write_size", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_SIZE, false, false, "一次写入临时文件的大小", "8k"));
        put(NginxDirectiveMeta.of("proxy_temp_path", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_PATH,
                false, false, "临时文件目录", "/var/cache/nginx/proxy_temp"));
        put(NginxDirectiveMeta.of("proxy_next_upstream", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_STRING, false, false, "哪些情况重试下一个上游（error / timeout / http_502 等）",
                "error timeout http_502 http_503"));
        put(NginxDirectiveMeta.of("proxy_next_upstream_tries", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_NUMBER, false, false, "重试下一个上游的次数上限", "0"));
        put(NginxDirectiveMeta.of("proxy_next_upstream_timeout", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_DURATION, false, false, "重试下一个上游的超时", "0"));
        put(NginxDirectiveMeta.of("proxy_intercept_errors", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "是否拦截上游错误并交给 error_page 处理", "off"));
        put(NginxDirectiveMeta.of("proxy_redirect", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_STRING,
                false, false, "改写上游返回的 Location 头", "off"));
        put(NginxDirectiveMeta.of("proxy_method", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_STRING,
                false, false, "把请求方法改写为指定方法", "GET"));
        put(NginxDirectiveMeta.of("proxy_bind", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_STRING,
                false, false, "向后端发起连接时使用的本地地址", ""));
        put(NginxDirectiveMeta.of("proxy_pass_request_body", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "是否转发请求体", "on"));
        put(NginxDirectiveMeta.of("proxy_pass_request_headers", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "是否转发原请求头", "on"));
        put(NginxDirectiveMeta.of("proxy_ssl_verify", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "是否校验后端 HTTPS 证书。自签名证书需关闭", "off"));
        put(NginxDirectiveMeta.of("proxy_ssl_server_name", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "是否向后端发送 SNI 主机名", "off"));
        put(NginxDirectiveMeta.of("proxy_ssl_session_reuse", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_FLAG, false, false, "是否复用后端 SSL 会话", "on"));
    }

    /**
     * upstream 层指令。
     */
    private static void buildUpstream() {
        put(NginxDirectiveMeta.of("zone", Set.of(SCOPE_UPSTREAM), TYPE_SIZE, false, false,
                "共享内存区大小，用于跨 worker 共享运行时状态", "64k"));
        put(NginxDirectiveMeta.of("ip_hash", Set.of(SCOPE_UPSTREAM), TYPE_FLAG, false, false,
                "按客户端 IP 做一致性哈希，保证同一 IP 固定到同一后端。开启后不能同时用 least_conn / hash", ""));
        put(NginxDirectiveMeta.of("hash", Set.of(SCOPE_UPSTREAM), TYPE_STRING, false, false,
                "按指定键做哈希，格式 hash $key consistent。开启后不能同时用 ip_hash / least_conn",
                "$request_uri consistent"));
        put(NginxDirectiveMeta.of("least_conn", Set.of(SCOPE_UPSTREAM), TYPE_FLAG, false, false,
                "把请求分给当前连接数最少的后端。适合后端处理耗时不均的场景", ""));
        put(NginxDirectiveMeta.of("random", Set.of(SCOPE_UPSTREAM), TYPE_STRING, false, false,
                "随机选择后端，可选参数 two / four 表示权重模式", "two"));
        put(NginxDirectiveMeta.of("keepalive", Set.of(SCOPE_UPSTREAM), TYPE_NUMBER, false, false,
                "与后端保持的空闲长连接数。配合 proxy_http_version 1.1 才有意义", "32"));
        put(NginxDirectiveMeta.of("server", Set.of(SCOPE_UPSTREAM), TYPE_STRING, true, false,
                "一个后端地址。参数可含 weight / max_conns / down / backup / slow_start",
                "10.0.0.11:8080 weight=2"));
    }

    /**
     * stream（TCP 代理）层指令。
     */
    private static void buildStream() {
        put(NginxDirectiveMeta.of("proxy_pass", Set.of(SCOPE_STREAM), TYPE_STRING, false, false,
                "TCP 转发目标，如 10.0.0.11:3306", "10.0.0.11:3306"));
        put(NginxDirectiveMeta.of("proxy_timeout", Set.of(SCOPE_STREAM), TYPE_DURATION, false, false,
                "TCP 代理的超时", "10m"));
        put(NginxDirectiveMeta.of("proxy_connect_timeout", Set.of(SCOPE_STREAM), TYPE_DURATION, false, false,
                "TCP 连接超时", "60s"));
        put(NginxDirectiveMeta.of("proxy_responses", Set.of(SCOPE_STREAM), TYPE_NUMBER, false, false,
                "期望的后端响应次数（无协议时用 0）", "0"));
        put(NginxDirectiveMeta.of("upstream", Set.of(SCOPE_STREAM), TYPE_LIST, false, false,
                "stream 层的上游名", "db_cluster"));
        put(NginxDirectiveMeta.of("listen", Set.of(SCOPE_STREAM), TYPE_STRING, true, false,
                "stream 层监听地址与端口", "3306"));
    }

    /**
     * types 层指令。
     */
    private static void buildTypes() {
        put(NginxDirectiveMeta.of("types", Set.of(SCOPE_TYPES), TYPE_FLAG, false, false,
                "在 types 块内无参数使用", ""));
        put(NginxDirectiveMeta.of("default_type", Set.of(SCOPE_TYPES), TYPE_STRING, false, false,
                "types 块内的默认类型", "application/octet-stream"));
    }

    /**
     * map / geo 层指令。
     */
    private static void buildMap() {
        put(NginxDirectiveMeta.of("map", Set.of(SCOPE_HTTP, SCOPE_STREAM), TYPE_STRING, false, false,
                "建立变量映射表。块内每行是 匹配值 结果", "$http_host $backend"));
        put(NginxDirectiveMeta.of("map_hash_bucket_size", Set.of(SCOPE_HTTP), TYPE_SIZE, false, false,
                "map 哈希表桶大小", "32k"));
        put(NginxDirectiveMeta.of("map_hash_max_size", Set.of(SCOPE_HTTP), TYPE_SIZE, false, false,
                "map 哈希表最大大小", "2048k"));
        put(NginxDirectiveMeta.of("geo", Set.of(SCOPE_HTTP, SCOPE_STREAM), TYPE_STRING, false, false,
                "按来源 IP 归属地做变量映射", "$remote_addr $country"));
        put(NginxDirectiveMeta.of("split_clients", Set.of(SCOPE_HTTP, SCOPE_STREAM), TYPE_STRING, false, false,
                "按权重把流量切分成多个分组，如 50% \"a\" 50% \"b\"", "$request $backend"));
    }

    /**
     * 限流与连接控制指令。
     */
    private static void buildLimit() {
        put(NginxDirectiveMeta.of("limit_req_zone", Set.of(SCOPE_HTTP), TYPE_STRING, false, false,
                "定义请求限流维度。rate 以「每 IP 每秒请求数」为单位", "$binary_remote_addr zone=one:10m rate=1r/s"));
        put(NginxDirectiveMeta.of("limit_req", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_STRING,
                true, false, "启用限流。burst 为突发排队量，nodelay 表示不排队直接拒绝",
                "zone=one burst=5 nodelay"));
        put(NginxDirectiveMeta.of("limit_req_status", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_NUMBER,
                false, false, "触发限流时返回的状态码，默认 503", "429"));
        put(NginxDirectiveMeta.of("limit_req_log_level", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_STRING, false, false, "限流事件的日志级别", "error"));
        put(NginxDirectiveMeta.of("limit_conn_zone", Set.of(SCOPE_HTTP), TYPE_STRING, false, false,
                "定义连接数限流维度", "$binary_remote_addr zone=conn:10m"));
        put(NginxDirectiveMeta.of("limit_conn", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_STRING,
                false, false, "每个 key 允许的最大并发连接数（语法：<zone> <数字>，如 conn 100）", "conn 100"));
        put(NginxDirectiveMeta.of("limit_conn_status", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_NUMBER, false, false, "触发连接限流时返回的状态码", "503"));
        put(NginxDirectiveMeta.of("limit_conn_log_level", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION),
                TYPE_STRING, false, false, "连接限流事件的日志级别", "warn"));
        put(NginxDirectiveMeta.of("limit_req_logoff", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "是否关闭限流日志", "off"));
        put(NginxDirectiveMeta.of("limit_conn_logoff", Set.of(SCOPE_HTTP, SCOPE_SERVER, SCOPE_LOCATION), TYPE_FLAG,
                false, false, "是否关闭连接限流日志", "off"));
    }
}
