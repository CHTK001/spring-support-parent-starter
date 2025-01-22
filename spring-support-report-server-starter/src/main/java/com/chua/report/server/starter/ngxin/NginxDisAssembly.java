package com.chua.report.server.starter.ngxin;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.function.Joiner;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.report.server.starter.entity.*;
import com.chua.report.server.starter.mapper.*;
import com.chua.report.server.starter.service.MonitorNginxConfigService;
import com.chua.socketio.support.MsgStep;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.github.odiszapc.nginxparser.NgxBlock;
import com.github.odiszapc.nginxparser.NgxConfig;
import com.github.odiszapc.nginxparser.NgxEntry;
import com.github.odiszapc.nginxparser.NgxParam;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

/**
 * nginx拆解
 * @author CH
 * @since 2024/12/29
 */
public class NginxDisAssembly {
    private final String eventName;
    @org.jetbrains.annotations.NotNull
    private final MonitorNginxConfig monitorNginxConfig;
    @Autowired
    private MonitorNginxConfigService monitorNginxConfigService;
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

    private int index = 0;
    public NginxDisAssembly(MonitorNginxConfig monitorNginxConfig) {
        this.eventName = getEventName(monitorNginxConfig.getMonitorNginxConfigId());
        this.monitorNginxConfig = monitorNginxConfig;
    }

    public String getEventName(Integer monitorNginxConfigId) {
        return "nginx-analysis-" + monitorNginxConfigId;
    }

    public int createIndex(int step) {
        return index += step;
    }
    public Boolean handle(InputStream inputStream) throws IOException {
        try {
            socketSessionTemplate.send(eventName, new MsgStep("开始解析配置", createIndex(1)));
            NgxConfig ngxConfig = NgxConfig.read(inputStream);
            socketSessionTemplate.send(eventName, new MsgStep("开始事件配置", createIndex(1)));
            registerEvent(ngxConfig);
            socketSessionTemplate.send(eventName, new MsgStep("开始Http配置", createIndex(1)));
            registerHttp(ngxConfig);
            return true;
        } finally {
            socketSessionTemplate.send(eventName, new MsgStep("完成解析", 100));

        }
    }


    private void registerHttp(NgxConfig ngxConfig) {
        NgxBlock http = ngxConfig.findBlock("http");
        MonitorNginxHttp monitorNginxHttp = monitorNginxHttpMapper.selectOne(Wrappers.<MonitorNginxHttp>lambdaQuery().eq(MonitorNginxHttp::getMonitorNginxConfigId, monitorNginxConfig.getMonitorNginxConfigId()));
        if(null == monitorNginxHttp) {
            monitorNginxHttp = new MonitorNginxHttp();
        }
        monitorNginxHttp.setMonitorNginxConfigId(monitorNginxConfig.getMonitorNginxConfigId());
        monitorNginxHttp.setMonitorNginxHttpServerNamesHashBucketSize(Converter.createInteger(findParam(http, "server_names_hash_bucket_size")));
        monitorNginxHttp.setMonitorNginxHttpClientBodyBufferSize(findParam(http, "client_body_buffer_size"));
        monitorNginxHttp.setMonitorNginxHttpGzip(findParam(http, "gzip"));
        monitorNginxHttp.setMonitorNginxHttpGzipCompLevel(Converter.createInteger(findParam(http, "gzip_comp_level")));
        monitorNginxHttp.setMonitorNginxHttpGzipDisable(findParam(http, "gzip_disable"));
        monitorNginxHttp.setMonitorNginxHttpGzipHttpVersion(findParam(http, "gzip_http_version"));
        monitorNginxHttp.setMonitorNginxHttpGzipMinLength(findParam(http, "gzip_min_length"));
        monitorNginxHttp.setMonitorNginxHttpGzipBuffers(findParam(http, "gzip_buffers"));
        monitorNginxHttp.setMonitorNginxHttpGzipTypes(findParam(http, "gzip_types"));
        monitorNginxHttp.setMonitorNginxHttpGzipVary(findParam(http, "gzip_vary"));
        monitorNginxHttp.setMonitorNginxHttpAccessLog(findParam(http, "access_log"));
        monitorNginxHttp.setMonitorNginxHttpErrorLog(findParam(http, "error_log"));
        monitorNginxHttp.setMonitorNginxHttpLogFormat(findParam(http, "log_format"));
        monitorNginxHttp.setMonitorNginxHttpKeepaliveTimeout(Converter.createInteger(findParam(http, "keepalive_timeout")));
        monitorNginxHttp.setMonitorNginxHttpTcpNopush(findParam(http, "tcp_nopush"));
        monitorNginxHttp.setMonitorNginxHttpTcpNodelay(findParam(http, "tcp_nodelay"));
        monitorNginxHttp.setMonitorNginxHttpSendfile(findParam(http, "sendfile"));
        monitorNginxHttp.setMonitorNginxHttpCharset(findParam(http, "charset"));
        monitorNginxHttp.setMonitorNginxHttpIgnoreInvalidHeaders(findParam(http, "ignore_invalid_headers"));
        monitorNginxHttp.setMonitorNginxHttpInclude(findParam(http, "include"));
        monitorNginxHttp.setMonitorNginxHttpProxyCachePath(findParam(http, "proxy_cache_path"));
        monitorNginxHttp.setMonitorNginxHttpLimitReqZone(findParam(http, "limit_req_zone"));
        monitorNginxHttp.setMonitorNginxHttpServerNameInRedirect(findParam(http, "server_name_in_redirect"));
        monitorNginxHttp.setMonitorNginxHttpSslPreferServerCiphers(findParam(http, "ssl_prefer_server_ciphers"));
        monitorNginxHttp.setMonitorNginxHttpSslCertificateKey(findParam(http, "ssl_certificate_key"));
        monitorNginxHttp.setMonitorNginxHttpSslCertificate(findParam(http, "ssl_certificate"));
        monitorNginxHttp.setMonitorNginxHttpSslProtocols(findParam(http, "ssl_protocols"));
        monitorNginxHttp.setMonitorNginxHttpSslCiphers(findParam(http, "ssl_ciphers"));
        monitorNginxHttp.setMonitorNginxHttpSslSessionTimeout(findParam(http, "ssl_session_timeout"));

        if(monitorNginxHttp.getMonitorNginxHttpId() == null) {
            monitorNginxHttpMapper.insert(monitorNginxHttp);
        } else {
            monitorNginxHttpMapper.updateById(monitorNginxHttp);
        }
        socketSessionTemplate.send(eventName, new MsgStep("开始Server配置", createIndex(1)));
        registerServers(http, monitorNginxHttp);
        socketSessionTemplate.send(eventName, new MsgStep("开始include配置", createIndex(1)));
        analysisInclude( monitorNginxHttp);
    }

    /**
     * 获取参数
     * @param http
     * @param name
     * @return
     */
    private String findParam(NgxBlock http, String name) {
        return ObjectUtils.optional(http.findParam(name), NgxParam::getValue, null);
    }

    private void analysisInclude(MonitorNginxHttp monitorNginxHttp) {
        String monitorNginxHttpInclude = monitorNginxHttp.getMonitorNginxHttpInclude();
        if(StringUtils.isBlank(monitorNginxHttpInclude)) {
            return;
        }
        String[] split = Splitter.on(";").split(monitorNginxHttpInclude);
        int max = 99 - index;
        int step = max / split.length;

        for (String s : split) {
            if(!s.endsWith(".conf")) {
                continue;
            }
            registerInclude(s, monitorNginxHttp, step);
        }
    }

    private void registerInclude(String s, MonitorNginxHttp monitorNginxHttp, int max) {
        String fullPath = getFullPath(s);
        File file = new File(fullPath);
        if(!file.isDirectory() || null == file.listFiles()) {
            return;
        }
        File[] files = file.listFiles();
        int step = max / files.length;
        for (File file1 : files) {
            socketSessionTemplate.send(eventName, new MsgStep("开始["+ file1.getName() +"]配置", createIndex(step)));
            registerInclude(file1, monitorNginxHttp);
        }
    }

    private void registerInclude(File file1, MonitorNginxHttp monitorNginxHttp) {
        try (FileInputStream fis = new FileInputStream(file1)) {
            NgxConfig ngxConfig = NgxConfig.read(fis);
            List<NgxEntry> server = ngxConfig.findAll(NgxBlock.class, "server");
            for (NgxEntry ngxEntry : server) {
                registerServer( (NgxBlock) ngxEntry, monitorNginxHttp);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void registerServers(NgxBlock http, MonitorNginxHttp monitorNginxHttp) {
        List<NgxEntry> server = http.findAll(NgxBlock.class, "server");
        for (NgxEntry ngxEntry : server) {
            NgxBlock serverBlock = (NgxBlock) ngxEntry;
            socketSessionTemplate.send(eventName, new MsgStep("开始Server配置", createIndex(1)));
            registerServer(serverBlock, monitorNginxHttp);
        }
    }

    private void registerServer(NgxBlock serverBlock, MonitorNginxHttp monitorNginxHttp) {

        String listen = findParam(serverBlock, "listen");
        boolean isSsl = listen.contains("ssl");
        Integer port;
        if(isSsl) {
            port = Converter.createInteger(listen.replace("ssl", ""));
        } else {
            port = Converter.createInteger(listen);
        }
        MonitorNginxHttpServer monitorNginxHttpServer = monitorNginxHttpServerMapper
                .selectOne(Wrappers.<MonitorNginxHttpServer>lambdaQuery()
                        .eq(MonitorNginxHttpServer::getMonitorNginxHttpId, monitorNginxHttp.getMonitorNginxHttpId())
                        .eq(MonitorNginxHttpServer::getMonitorNginxHttpServerPort, port)
                );

        if(null == monitorNginxHttpServer) {
            monitorNginxHttpServer = new MonitorNginxHttpServer();
        }
        monitorNginxHttpServer.setMonitorNginxHttpId(monitorNginxHttp.getMonitorNginxHttpId());
        monitorNginxHttpServer.setMonitorNginxHttpServerPort(port);
        monitorNginxHttpServer.setMonitorNginxHttpServerName(findParam(serverBlock, "server_name"));
        monitorNginxHttpServer.setMonitorNginxHttpServerCharset(findParam(serverBlock, "charset"));
        monitorNginxHttpServer.setMonitorNginxHttpServerAccessLog(findParam(serverBlock, "access_log"));
        monitorNginxHttpServer.setMonitorNginxHttpServerReturn(findParam(serverBlock, "return"));
        monitorNginxHttpServer.setMonitorNginxHttpServerSsl(isSsl ? "ssl" : "");
        monitorNginxHttpServer.setMonitorNginxHttpServerUdp(findParam(serverBlock, "listen"));
        monitorNginxHttpServer.setMonitorNginxHttpServerErrorPage(findParam(serverBlock, "error_page"));
        monitorNginxHttpServer.setMonitorNginxHttpServerSslCertificate(findParam(serverBlock, "ssl_certificate"));
        monitorNginxHttpServer.setMonitorNginxHttpServerSslCertificateKey(findParam(serverBlock, "ssl_certificate_key"));
        monitorNginxHttpServer.setMonitorNginxHttpServerSslProtocols(findParam(serverBlock, "ssl_protocols"));
        monitorNginxHttpServer.setMonitorNginxHttpServerSslCiphers(findParam(serverBlock, "ssl_ciphers"));
        monitorNginxHttpServer.setMonitorNginxHttpServerSslPreferServerCiphers(findParam(serverBlock, "ssl_prefer_server_ciphers"));
        monitorNginxHttpServer.setMonitorNginxHttpServerSslSessionTimeout(findParam(serverBlock, "ssl_session_timeout"));
        if(monitorNginxHttpServer.getMonitorNginxHttpServerId() == null) {
            monitorNginxHttpServerMapper.insert(monitorNginxHttpServer);
        } else {
            monitorNginxHttpServerMapper.updateById(monitorNginxHttpServer);
        }
        socketSessionTemplate.send(eventName, new MsgStep("开始location配置", createIndex(1)));
        registerServerLocations(serverBlock, monitorNginxHttpServer);
    }

    private void registerServerLocations(NgxBlock serverBlock, MonitorNginxHttpServer monitorNginxHttpServer) {
        List<NgxEntry> location = serverBlock.findAll(NgxBlock.class, "location");
        for (NgxEntry ngxEntry : location) {
            NgxBlock locationBlock = (NgxBlock) ngxEntry;
            registerServerLocation(locationBlock, monitorNginxHttpServer);
        }
    }

    private void registerServerLocation(NgxBlock locationBlock, MonitorNginxHttpServer monitorNginxHttpServer) {
        String location = findParam(locationBlock, "location");
        MonitorNginxHttpServerLocation monitorNginxHttpLocation = monitorNginxHttpServerLocationMapper
                .selectOne(Wrappers.<MonitorNginxHttpServerLocation>lambdaQuery()
                        .eq(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerId, monitorNginxHttpServer.getMonitorNginxHttpServerId())
                        .eq(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerLocationName, location)
                );
        if(null == monitorNginxHttpLocation) {
            monitorNginxHttpLocation = new MonitorNginxHttpServerLocation();
        }
        monitorNginxHttpLocation.setMonitorNginxHttpServerId(monitorNginxHttpServer.getMonitorNginxHttpServerId());
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationName(location);
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationRoot(findParam(locationBlock, "root"));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationIndex(findParam(locationBlock, "index"));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationClientBodyBufferSize(Converter.convertIfNecessary(findParam(locationBlock, "client_body_buffer_size"), Double.class));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationLimitReq(findParam(locationBlock, "limit_req"));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyCache(findParam(locationBlock, "proxy_cache"));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyCacheValid(findParam(locationBlock, "proxy_cache_valid"));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyCacheMethods(findParam(locationBlock, "proxy_cache_methods"));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyConnectTimeout(Converter.createInteger(findParam(locationBlock, "proxy_connect_timeout")));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxySendTimeout(Converter.createInteger(findParam(locationBlock, "proxy_send_timeout")));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyReadTimeout(Converter.createInteger(findParam(locationBlock, "proxy_read_timeout")));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyConnectTimeout(Converter.createInteger(findParam(locationBlock, "proxy_connect_timeout")));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyPass(findParam(locationBlock, "proxy_pass"));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationTryFiles(findParam(locationBlock, "try_files"));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationAlias(findParam(locationBlock, "alias"));
        if(monitorNginxHttpLocation.getMonitorNginxHttpServerLocationId() == null) {
            monitorNginxHttpServerLocationMapper.insert(monitorNginxHttpLocation);
        } else {
            monitorNginxHttpServerLocationMapper.updateById(monitorNginxHttpLocation);
        }

        registerServerLocationHeader(locationBlock, monitorNginxHttpLocation);
    }

    private void registerServerLocationHeader(NgxBlock locationBlock, MonitorNginxHttpServerLocation monitorNginxHttpLocation) {
        monitorNginxHttpServerLocationHeaderMapper.delete(Wrappers.<MonitorNginxHttpServerLocationHeader>lambdaQuery().eq(MonitorNginxHttpServerLocationHeader::getMonitorNginxHttpServerLocationId, monitorNginxHttpLocation.getMonitorNginxHttpServerLocationId()));
        for (NgxEntry proxySetHeader : locationBlock.findAll(NgxParam.class, "proxy_set_header")) {
            if(proxySetHeader instanceof NgxParam ngxParam) {
                String[] s = ngxParam.getValue().split(" ");
                MonitorNginxHttpServerLocationHeader monitorNginxHttpServerLocationHeader = new MonitorNginxHttpServerLocationHeader();
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationId(monitorNginxHttpLocation.getMonitorNginxHttpServerLocationId());
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationHeaderName(s[0]);
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationHeaderValue(s[1]);
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationHeaderType("proxy_set_header");
                monitorNginxHttpServerLocationHeaderMapper.insert(monitorNginxHttpServerLocationHeader);
            }
        }

        for (NgxEntry proxySetHeader : locationBlock.findAll(NgxParam.class, "add_header")) {
            if(proxySetHeader instanceof NgxParam ngxParam) {
                String[] s = ngxParam.getValue().split(" ");
                MonitorNginxHttpServerLocationHeader monitorNginxHttpServerLocationHeader = new MonitorNginxHttpServerLocationHeader();
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationId(monitorNginxHttpLocation.getMonitorNginxHttpServerLocationId());
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationHeaderName(s[0]);
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationHeaderValue(s[1]);
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationHeaderType("add_header");
                monitorNginxHttpServerLocationHeaderMapper.insert(monitorNginxHttpServerLocationHeader);
            }
        }

        for (NgxEntry proxySetHeader : locationBlock.findAll(NgxParam.class, "set_header")) {
            if(proxySetHeader instanceof NgxParam ngxParam) {
                String[] s = ngxParam.getValue().split(" ");
                MonitorNginxHttpServerLocationHeader monitorNginxHttpServerLocationHeader = new MonitorNginxHttpServerLocationHeader();
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationId(monitorNginxHttpLocation.getMonitorNginxHttpServerLocationId());
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationHeaderName(s[0]);
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationHeaderValue(s[1]);
                monitorNginxHttpServerLocationHeader.setMonitorNginxHttpServerLocationHeaderType("set_header");
                monitorNginxHttpServerLocationHeaderMapper.insert(monitorNginxHttpServerLocationHeader);
            }
        }

    }

    private void registerEvent(NgxConfig ngxConfig) {
        NgxBlock events = ngxConfig.findBlock("events");
        MonitorNginxEvent monitorNginxEvent = monitorNginxEventMapper.selectOne(Wrappers.<MonitorNginxEvent>lambdaQuery().eq(MonitorNginxEvent::getMonitorNginxConfigId, monitorNginxConfig.getMonitorNginxConfigId()));
        if(null == monitorNginxEvent) {
            monitorNginxEvent = new MonitorNginxEvent();
        }
        monitorNginxEvent.setMonitorNginxEventWorkerConnections(Converter.createInteger(
                ObjectUtils.optional(events.findParam("worker_connections"), NgxParam::getValue, "1024")));
        monitorNginxEvent.setMonitorNginxEventMultiAccept(ObjectUtils.optional(events.findParam("multi_accept"), NgxParam::getValue, null));
        monitorNginxEvent.setMonitorNginxEventAcceptMutex(ObjectUtils.optional(events.findParam("accept_mutex"), NgxParam::getValue, null));
        monitorNginxEvent.setMonitorNginxConfigId(monitorNginxConfig.getMonitorNginxConfigId());
        if(monitorNginxEvent.getMonitorNginxEventId() == null) {
            monitorNginxEventMapper.insert(monitorNginxEvent);
            return;
        }
        monitorNginxEventMapper.updateById(monitorNginxEvent);
    }


    public static String getFullPath(String path) {
        path = path.replace("\\", "/");
        List<String> sep = new LinkedList();

        for(String item : path.split("/")) {
            if (item.contains("*") || item.contains("?")) {
                break;
            }

            sep.add(item);
        }

        return Joiner.on("/").join(sep);
    }

    public static String getMatchPath(String path) {
        path = path.replace("\\", "/");
        List<String> sep = new LinkedList();

        for(String item : path.split("/")) {
            if (item.contains("*") || item.contains("?")) {
                sep.add(item);
            }
        }

        return Joiner.on("/").join(sep);
    }
}
