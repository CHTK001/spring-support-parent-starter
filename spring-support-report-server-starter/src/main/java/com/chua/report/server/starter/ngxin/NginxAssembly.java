package com.chua.report.server.starter.ngxin;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.report.server.starter.entity.*;
import com.chua.report.server.starter.mapper.*;
import com.chua.socketio.support.MsgStep;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.github.odiszapc.nginxparser.*;
import com.google.common.base.Joiner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * nginx组装
 *
 * @author CH
 * @since 2024/12/29
 */
@RequiredArgsConstructor
public class NginxAssembly {
    private final String eventName;
    private final MonitorNginxConfig monitorNginxConfig;
    @Autowired
    private MonitorNginxConfigMapper baseMapper;
    @Autowired
    private MonitorNginxHttpMapper monitorNginxHttpMapper;
    @Autowired
    private MonitorNginxHttpServerMapper monitorNginxHttpServerMapper;
    @Autowired
    private MonitorNginxHttpServerLocationMapper monitorNginxHttpServerLocationMapper;
    @Autowired
    private MonitorNginxHttpServerLocationHeaderMapper monitorNginxHttpServerLocationHeaderMapper;
    @Autowired
    private MonitorNginxUpstreamMapper monitorNginxUpstreamMapper;
    @Autowired
    private MonitorNginxEventMapper monitorNginxEventMapper;
    @Autowired
    private SocketSessionTemplate socketSessionTemplate;

    int index = 0;

    public NginxAssembly(MonitorNginxConfig monitorNginxConfig) {
        this.eventName = getEventName(monitorNginxConfig.getMonitorNginxConfigId());
        this.monitorNginxConfig = monitorNginxConfig;
    }

    public String getEventName(Integer monitorNginxConfigId) {
        return "nginx-create-" + monitorNginxConfigId;
    }

    public int createIndex(int step) {
        return index += step;
    }

    public Boolean handle(Integer nginxConfigId) {
        socketSessionTemplate.send(eventName, new MsgStep("获取事件", createIndex(1)));
        MonitorNginxEvent monitorNginxEvent = monitorNginxEventMapper.selectOne(Wrappers.<MonitorNginxEvent>lambdaQuery()
                .eq(MonitorNginxEvent::getMonitorNginxConfigId, nginxConfigId));
        socketSessionTemplate.send(eventName, new MsgStep("获取Http", createIndex(1)));
        MonitorNginxHttp monitorNginxHttp = monitorNginxHttpMapper.selectOne(Wrappers.<MonitorNginxHttp>lambdaQuery()
                .eq(MonitorNginxHttp::getMonitorNginxConfigId, nginxConfigId));

        List<MonitorNginxHttpServer> monitorNginxHttpServers = new LinkedList<>();
        monitorNginxHttpServers = monitorNginxHttpServerMapper.selectList(Wrappers.<MonitorNginxHttpServer>lambdaQuery()
                .eq(MonitorNginxHttpServer::getMonitorNginxHttpId, monitorNginxHttp.getMonitorNginxHttpId()));
        Map<Integer, List<MonitorNginxHttpServer>> httpServer = new LinkedHashMap<>();
        if (!monitorNginxHttpServers.isEmpty()) {
            httpServer = monitorNginxHttpServers.stream().collect(Collectors.groupingBy(MonitorNginxHttpServer::getMonitorNginxHttpId));
        }

        socketSessionTemplate.send(eventName, new MsgStep("获取Server", createIndex(1)));
        List<Integer> httpServerIds = httpServer.values().stream()
                .flatMap(List::stream)
                .map(MonitorNginxHttpServer::getMonitorNginxHttpServerId).toList();

        List<MonitorNginxUpstream> monitorNginxUpstreams = new LinkedList<>();
        Map<Integer, List<MonitorNginxHttpServerLocation>> location = new LinkedHashMap<>();
        Map<Integer, List<MonitorNginxHttpServerLocationHeader>> locationHeader = new LinkedHashMap<>();
        if (!httpServerIds.isEmpty()) {
            location = monitorNginxHttpServerLocationMapper.selectList(Wrappers.<MonitorNginxHttpServerLocation>lambdaQuery()
                            .in(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerId, httpServerIds))
                    .stream().collect(Collectors.groupingBy(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerId));
            locationHeader = monitorNginxHttpServerLocationHeaderMapper.selectList(Wrappers.<MonitorNginxHttpServerLocationHeader>lambdaQuery()
                            .in(MonitorNginxHttpServerLocationHeader::getMonitorNginxHttpServerLocationId, location.values().stream()
                                    .flatMap(List::stream)
                                    .map(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerLocationId).toList()))
                    .stream().collect(Collectors.groupingBy(MonitorNginxHttpServerLocationHeader::getMonitorNginxHttpServerLocationId));
            monitorNginxUpstreams = monitorNginxUpstreamMapper.selectList(Wrappers.<MonitorNginxUpstream>lambdaQuery()
                    .in(MonitorNginxUpstream::getMonitorNginxServerId, httpServerIds));
        }


        socketSessionTemplate.send(eventName, new MsgStep("获取配置", createIndex(1)));
        MonitorNginxConfig monitorNginxConfig = baseMapper.selectById(nginxConfigId);

        try {
            NginxAnalyze analyze;
            if (monitorNginxConfig.getMonitorNginxConfigMultipart() == 1) {
                analyze = new MultiNginxAnalyze(monitorNginxEvent, monitorNginxHttp, monitorNginxHttpServers, monitorNginxUpstreams, location, locationHeader);
            } else {
                analyze = new SingleNginxAnalyze(monitorNginxEvent, monitorNginxHttp, monitorNginxHttpServers, monitorNginxUpstreams, location, locationHeader);
            }
            return analyze.analyze(monitorNginxConfig);
        } finally {
            socketSessionTemplate.send(eventName, new MsgStep("生成完成", 100));
        }
    }

    static class MultiNginxAnalyze implements NginxAnalyze {
        private final MonitorNginxEvent monitorNginxEvent;
        private final MonitorNginxHttp monitorNginxHttp;
        private final List<MonitorNginxHttpServer> monitorNginxHttpServers;
        private final List<MonitorNginxUpstream> monitorNginxUpstreams;
        private final Map<Integer, List<MonitorNginxHttpServerLocation>> location;
        private final Map<Integer, List<MonitorNginxHttpServerLocationHeader>> locationHeader;

        public MultiNginxAnalyze(MonitorNginxEvent monitorNginxEvent, MonitorNginxHttp monitorNginxHttp, List<MonitorNginxHttpServer> monitorNginxHttpServers, List<MonitorNginxUpstream> monitorNginxUpstreams, Map<Integer, List<MonitorNginxHttpServerLocation>> location, Map<Integer, List<MonitorNginxHttpServerLocationHeader>> locationHeader) {
            this.monitorNginxEvent = monitorNginxEvent;
            this.monitorNginxHttp = monitorNginxHttp;
            this.monitorNginxHttpServers = monitorNginxHttpServers;
            this.monitorNginxUpstreams = monitorNginxUpstreams;
            this.location = location;
            this.locationHeader = locationHeader;
        }

        @Override
        public Boolean analyze(MonitorNginxConfig monitorNginxConfig) {
            NgxConfig nginxConfig = createNginxConfig(monitorNginxConfig);
            createNginxEvent(monitorNginxEvent, nginxConfig);
            NgxBlock ngxHttp = createNginxHttp(monitorNginxHttp);
            for (MonitorNginxHttpServer monitorNginxHttpServer : monitorNginxHttpServers) {
                NgxBlock server = createServer(ngxHttp, monitorNginxHttpServer, location.get(monitorNginxHttpServer.getMonitorNginxHttpServerId()), locationHeader);
                createServerFile(monitorNginxConfig, monitorNginxHttp, monitorNginxHttpServer, server);
            }
            nginxConfig.addEntry(ngxHttp);
            createNginxConf(monitorNginxConfig, nginxConfig);
            return null;
        }


    }
    static class SingleNginxAnalyze implements NginxAnalyze {
        private final MonitorNginxEvent monitorNginxEvent;

        private final MonitorNginxHttp monitorNginxHttp;
        private final List<MonitorNginxHttpServer> monitorNginxHttpServers;
        private final List<MonitorNginxUpstream> monitorNginxUpstreams;
        private final Map<Integer, List<MonitorNginxHttpServerLocation>> location;
        private final Map<Integer, List<MonitorNginxHttpServerLocationHeader>> locationHeader;
        public SingleNginxAnalyze(MonitorNginxEvent monitorNginxEvent, MonitorNginxHttp monitorNginxHttp, List<MonitorNginxHttpServer> monitorNginxHttpServers, List<MonitorNginxUpstream> monitorNginxUpstreams, Map<Integer, List<MonitorNginxHttpServerLocation>> location, Map<Integer, List<MonitorNginxHttpServerLocationHeader>> locationHeader) {
            this.monitorNginxEvent = monitorNginxEvent;
            this.monitorNginxHttp = monitorNginxHttp;
            this.monitorNginxHttpServers = monitorNginxHttpServers;
            this.monitorNginxUpstreams = monitorNginxUpstreams;
            this.location = location;
            this.locationHeader = locationHeader;
        }
        @Override
        public Boolean analyze(MonitorNginxConfig monitorNginxConfig) {
            NgxConfig nginxConfig = createNginxConfig(monitorNginxConfig);
            createNginxEvent(monitorNginxEvent, nginxConfig);
            NgxBlock ngxHttp = createNginxHttp(monitorNginxHttp);
            for (MonitorNginxHttpServer monitorNginxHttpServer : monitorNginxHttpServers) {
                NgxBlock server = createServer(ngxHttp, monitorNginxHttpServer, location.get(monitorNginxHttpServer.getMonitorNginxHttpServerId()), locationHeader);
                ngxHttp.addEntry(server);
            }
            nginxConfig.addEntry(ngxHttp);
            createNginxConf(monitorNginxConfig, nginxConfig);
            return null;
        }




    }
    private static NgxBlock createServer(NgxBlock ngxHttp, MonitorNginxHttpServer monitorNginxHttpServer, List<MonitorNginxHttpServerLocation> monitorNginxHttpServerLocations, Map<Integer, List<MonitorNginxHttpServerLocationHeader>> locationHeader) {
        NgxBlock server = new NgxBlock();
        server.addEntry(new NgxComment("#HTTP 服务器模块配置"));
        server.addValue("server");
        createParam(server, "listen", "监听端口", monitorNginxHttpServer.getMonitorNginxHttpServerPort(), monitorNginxHttpServer.getMonitorNginxHttpServerSsl());
        createParam(server, "server_name", "服务器名称", monitorNginxHttpServer.getMonitorNginxHttpServerName());
        createParam(server, "charset", "编码", monitorNginxHttpServer.getMonitorNginxHttpServerCharset());
        createParam(server, "access_log", "访问日志", monitorNginxHttpServer.getMonitorNginxHttpServerAccessLog());
        createParam(server, "error_page", "错误页面", monitorNginxHttpServer.getMonitorNginxHttpServerErrorPage());

        // 添加其他配置项
        createParam(server, "return", "重定向", monitorNginxHttpServer.getMonitorNginxHttpServerReturn());
        createParam(server, "udp", "UDP配置", monitorNginxHttpServer.getMonitorNginxHttpServerUdp());
        createParam(server, "ssl_certificate", "SSL证书路径", monitorNginxHttpServer.getMonitorNginxHttpServerSslCertificate());
        createParam(server, "ssl_certificate_key", "SSL证书密钥路径", monitorNginxHttpServer.getMonitorNginxHttpServerSslCertificateKey());
        createParam(server, "ssl_protocols", "SSL协议", monitorNginxHttpServer.getMonitorNginxHttpServerSslProtocols());
        createParam(server, "ssl_session_timeout", "SSL会话超时", monitorNginxHttpServer.getMonitorNginxHttpServerSslSessionTimeout());
        createParam(server, "ssl_ciphers", "SSL加密算法", monitorNginxHttpServer.getMonitorNginxHttpServerSslCiphers());
        createParam(server, "ssl_prefer_server_ciphers", "SSL优先服务器加密算法", monitorNginxHttpServer.getMonitorNginxHttpServerSslPreferServerCiphers());

        registerLocation(server, monitorNginxHttpServerLocations, locationHeader);
        ngxHttp.addEntry(server);
        return server;

    }
    private static void registerLocation(NgxBlock server, List<MonitorNginxHttpServerLocation> monitorNginxHttpServerLocations, Map<Integer, List<MonitorNginxHttpServerLocationHeader>> locationHeader) {
        for (MonitorNginxHttpServerLocation monitorNginxHttpServerLocation : monitorNginxHttpServerLocations) {
            NgxBlock location = new NgxBlock();
            location.addEntry(new NgxComment("#HTTP 服务器模块配置"));
            location.addValue("location " + monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationName());
            createParam(location, "alias", "别名", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationAlias());
            createParam(location, "root", "根目录", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationRoot());
            createParam(location, "index", "默认文件", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationIndex());
            createParam(location, "try_files", "尝试文件", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationTryFiles());
            createParam(location, "limit_req", "限制请求", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationLimitReq() );

            // 添加其他配置项
            createParam(location, "proxy_pass", "代理地址", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationProxyPass());
            createParam(location, "proxy_cache", "代理缓存", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationProxyCache());
            createParam(location, "proxy_cache_valid", "代理缓存有效时间", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationProxyCacheValid());
            createParam(location, "proxy_cache_methods", "代理缓存方法", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationProxyCacheMethods());
            createParam(location, "proxy_connect_timeout", "代理链接超时时间", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationProxyConnectTimeout());
            createParam(location, "proxy_read_timeout", "代理读取超时时间", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationProxyReadTimeout());
            createParam(location, "proxy_send_timeout", "代理发送超时时间", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationProxySendTimeout());
            createParam(location, "client_max_body_size", "允许客户端请求的最大单文件字节", monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationClientMaxBodySize());
            createParam(location, "client_body_buffer_size", "客户端请求体缓冲区大小",  monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationClientBodyBufferSize());

            createHeader(location, locationHeader.get(monitorNginxHttpServerLocation.getMonitorNginxHttpServerLocationId()));

            server.addEntry(location);
        }
    }

    private static void createHeader(NgxBlock location, List<MonitorNginxHttpServerLocationHeader> monitorNginxHttpServerLocationHeaders) {
        if(null == monitorNginxHttpServerLocationHeaders) {
            return;
        }
        for (MonitorNginxHttpServerLocationHeader monitorNginxHttpServerLocationHeader : monitorNginxHttpServerLocationHeaders) {
            createParam(location, monitorNginxHttpServerLocationHeader.getMonitorNginxHttpServerLocationHeaderType(), "添加头信息", monitorNginxHttpServerLocationHeader.getMonitorNginxHttpServerLocationHeaderName() + " " + monitorNginxHttpServerLocationHeader.getMonitorNginxHttpServerLocationHeaderValue());
        }
    }

    private static NgxBlock createNginxHttp(MonitorNginxHttp monitorNginxHttp) {
        NgxBlock ngxHttp = new NgxBlock();
        ngxHttp.addEntry(new NgxComment("#HTTP 模块配置"));
        ngxHttp.addValue("http");
        createParam(ngxHttp, "server_names_hash_bucket_size", "设置服务器名称的哈希表大小", monitorNginxHttp.getMonitorNginxHttpServerNamesHashBucketSize());
        createParam(ngxHttp, "server_tokens", "设置nginx返回给用户的服务器标识", monitorNginxHttp.getMonitorNginxHttpServerTokens());
        createParam(ngxHttp, "include", "包含其它配置文件", monitorNginxHttp.getMonitorNginxHttpInclude());
        createParam(ngxHttp, "log_format", "自定义日志格式",
                com.chua.common.support.utils.StringUtils.defaultString(monitorNginxHttp.getMonitorNginxHttpLogName(), "main"), com.chua.common.support.utils.StringUtils.defaultString(monitorNginxHttp.getMonitorNginxHttpLogFormat(),
                        """
                                '$remote_addr - $remote_user [$time_local] "$request" '
                                                    '$status $body_bytes_sent "$http_referer" '
                                                    '"$http_user_agent" "$http_x_forwarded_for"'"""));
        createParam(ngxHttp, "error_log", "错误日志", monitorNginxHttp.getMonitorNginxHttpErrorLog());
        createParam(ngxHttp, "access_log", "访问日志", monitorNginxHttp.getMonitorNginxHttpAccessLog());
        createParam(ngxHttp, "sendfile", "是否开启sendfile", monitorNginxHttp.getMonitorNginxHttpSendfile());
        createParam(ngxHttp, "tcp_nopush", "是否开启tcp_nopush", monitorNginxHttp.getMonitorNginxHttpTcpNopush());
        createParam(ngxHttp, "tcp_nodelay", "是否开启tcp_nodelay", monitorNginxHttp.getMonitorNginxHttpTcpNodelay());
        createParam(ngxHttp, "keepalive_timeout", "设置连接超时时间", monitorNginxHttp.getMonitorNginxHttpKeepaliveTimeout());
        createParam(ngxHttp, "charset", "默认字符集", monitorNginxHttp.getMonitorNginxHttpCharset());
        createParam(ngxHttp, "gzip", "Gzip 压缩", monitorNginxHttp.getMonitorNginxHttpGzip());
        createParam(ngxHttp, "gzip_comp_level", "设置gzip压缩等级", monitorNginxHttp.getMonitorNginxHttpGzipCompLevel());
        createParam(ngxHttp, "gzip_types", "设置哪些文件类型需要压缩", monitorNginxHttp.getMonitorNginxHttpGzipTypes());
        createParam(ngxHttp, "gzip_vary", "设置gzip压缩", monitorNginxHttp.getMonitorNginxHttpGzipVary());
        createParam(ngxHttp, "gzip_min_length", "设置gzip压缩的最小长度", monitorNginxHttp.getMonitorNginxHttpGzipMinLength());
        createParam(ngxHttp, "gzip_buffers", "设置gzip压缩缓冲区大小", monitorNginxHttp.getMonitorNginxHttpGzipBuffers());
        createParam(ngxHttp, "gzip_disable", "设置哪些浏览器不进行gzip压缩", monitorNginxHttp.getMonitorNginxHttpGzipDisable());
        createParam(ngxHttp, "gzip_http_version", "设置gzip压缩的http版本", monitorNginxHttp.getMonitorNginxHttpGzipHttpVersion());

        return ngxHttp;
    }


    private static void createNginxEvent(MonitorNginxEvent monitorNginxEvent, NgxBlock nginxConfig) {
        NgxBlock events = new NgxBlock();
        events.addEntry(new NgxComment("#events模块：影响nginx服务器与用户的网络连接"));
        events.addValue("events");
        createParam(events, "worker_connections", "设置单个工作进程最大并发连接数", monitorNginxEvent.getMonitorNginxEventWorkerConnections());
        createParam(events, "multi_accept", "允许一个进程接受多个连接", monitorNginxEvent.getMonitorNginxEventMultiAccept());
        createParam(events, "use", "使用 epoll 模型（Linux 系统推荐）", monitorNginxEvent.getMonitorNginxEventUse());
        createParam(events, "accept_mutex", "在某些情况下，可以设置为 on 来允许多个工作进程同时监听相同的端口。默认情况下，它是关闭的，以避免多个进程间的端口竞争", monitorNginxEvent.getMonitorNginxEventAcceptMutex());
        nginxConfig.addEntry(events);
    }

    private static NgxConfig createNginxConfig(MonitorNginxConfig monitorNginxConfig) {
        NgxConfig ngxConfig = new NgxConfig();
//        createParam1(ngxConfig, "user", com.chua.common.support.utils.StringUtils.defaultString(monitorNginxConfig.getMonitorNginxConfigRunUser(), System.getProperty("user.name")));
        createParam1(ngxConfig, "worker_processes", monitorNginxConfig.getMonitorNginxConfigWorkerProcesses());
        createParam1(ngxConfig, "pid", FileUtils.normalize(monitorNginxConfig.getMonitorNginxConfigPid(), "/nginx.pid"));
        createParam1(ngxConfig, "error_log", FileUtils.normalize(
                monitorNginxConfig.getMonitorNginxConfigErrorLog().contains(".log") ?
                        monitorNginxConfig.getMonitorNginxConfigErrorLog() :
                        monitorNginxConfig.getMonitorNginxConfigErrorLog() + "/error.log"
        ));
//        createParam1(ngxConfig, "access_log", FileUtils.normalize(
//                monitorNginxConfig.getMonitorNginxConfigAccessLog().contains(".log") ?
//                        monitorNginxConfig.getMonitorNginxConfigAccessLog() :
//                        monitorNginxConfig.getMonitorNginxConfigAccessLog() + "/access.log"
//        ));
        return ngxConfig;
    }

    private static void createParam1(NgxBlock ngxBlock, String name, Object... value) {
        createParam(ngxBlock, name, null, value);

    }

    private static void createParam(NgxBlock ngxBlock, String name, String comment, Object... value) {
        if (null == value) {
            return;
        }
        if (value.length == 1 && ObjectUtils.isEmpty(value[0])) {
            return;
        }
        if (!StringUtils.isBlank(comment)) {
            NgxComment ngxComment = new NgxComment("#" + comment);
            ngxBlock.addEntry(ngxComment);
        }
        NgxParam ngxParam = new NgxParam();
        ngxParam.addValue(name + " " + Joiner.on(" ").skipNulls().join(value));
        ngxBlock.addEntry(ngxParam);
    }

    private static void createNginxConf(MonitorNginxConfig monitorNginxConfig, NgxConfig nginxConfig) {
        NgxDumper nodeDumper = new NgxDumper(nginxConfig);
        try (FileOutputStream outputStream = new FileOutputStream(new File(monitorNginxConfig.getMonitorNginxConfigPath()))){
            nodeDumper.dump(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static void createServerFile(MonitorNginxConfig monitorNginxConfig, MonitorNginxHttp monitorNginxHttp, MonitorNginxHttpServer monitorNginxHttpServer, NgxBlock server) {
        NgxConfig ngxConfig = new NgxConfig();
        ngxConfig.addEntry(server);
        NgxDumper nodeDumper = new NgxDumper(ngxConfig);
        File file = new File(monitorNginxHttp.getIncludeParentPath(monitorNginxConfig), monitorNginxHttpServer.getMonitorNginxHttpServerName() + ".conf");
        FileUtils.forceMkdirParent(file);
        try {
            FileUtils.forceDelete(file);
        } catch (IOException ignored) {
        }
        try (FileOutputStream outputStream = new FileOutputStream(file)){
            nodeDumper.dump(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    interface NginxAnalyze {

        Boolean analyze(MonitorNginxConfig monitorNginxConfig);
    }
}
