package com.chua.report.server.starter.ngxin;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.converter.Converter;
import com.chua.common.support.function.Joiner;
import com.chua.common.support.function.Splitter;
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
        socketSessionTemplate.send(eventName, new MsgStep("开始解析配置", createIndex(1)));
        NgxConfig ngxConfig = NgxConfig.read(inputStream);
        socketSessionTemplate.send(eventName, new MsgStep("开始事件配置", createIndex(1)));
        registerEvent(ngxConfig);
        socketSessionTemplate.send(eventName, new MsgStep("开始Http配置", createIndex(1)));
        registerHttp(ngxConfig);
        return false;
    }


    private void registerHttp(NgxConfig ngxConfig) {
        NgxBlock http = ngxConfig.findBlock("http");
        MonitorNginxHttp monitorNginxHttp = monitorNginxHttpMapper.selectOne(Wrappers.<MonitorNginxHttp>lambdaQuery().eq(MonitorNginxHttp::getMonitorNginxConfigId, monitorNginxConfig.getMonitorNginxConfigId()));
        if(null == monitorNginxHttp) {
            monitorNginxHttp = new MonitorNginxHttp();
        }
        monitorNginxHttp.setMonitorNginxConfigId(monitorNginxConfig.getMonitorNginxConfigId());
        monitorNginxHttp.setMonitorNginxHttpServerNamesHashBucketSize(Converter.createInteger(http.findParam("server_names_hash_bucket_size").getValue()));
        monitorNginxHttp.setMonitorNginxHttpClientBodyBufferSize(http.findParam("client_body_buffer_size").getValue());
        monitorNginxHttp.setMonitorNginxHttpGzip(http.findParam("gzip").getValue());
        monitorNginxHttp.setMonitorNginxHttpGzipCompLevel(Converter.createInteger(http.findParam("gzip_comp_level").getValue()));
        monitorNginxHttp.setMonitorNginxHttpGzipDisable(http.findParam("gzip_disable").getValue());
        monitorNginxHttp.setMonitorNginxHttpGzipHttpVersion(http.findParam("gzip_http_version").getValue());
        monitorNginxHttp.setMonitorNginxHttpGzipMinLength(http.findParam("gzip_min_length").getValue());
        monitorNginxHttp.setMonitorNginxHttpGzipBuffers(http.findParam("gzip_buffers").getValue());
        monitorNginxHttp.setMonitorNginxHttpGzipTypes(http.findParam("gzip_types").getValue());
        monitorNginxHttp.setMonitorNginxHttpGzipVary(http.findParam("gzip_vary").getValue());
        monitorNginxHttp.setMonitorNginxHttpAccessLog(http.findParam("access_log").getValue());
        monitorNginxHttp.setMonitorNginxHttpErrorLog(http.findParam("error_log").getValue());
        monitorNginxHttp.setMonitorNginxHttpLogFormat(http.findParam("log_format").getValue());
        monitorNginxHttp.setMonitorNginxHttpKeepaliveTimeout(Converter.createInteger(http.findParam("keepalive_timeout").getValue()));
        monitorNginxHttp.setMonitorNginxHttpTcpNopush(http.findParam("tcp_nopush").getValue());
        monitorNginxHttp.setMonitorNginxHttpTcpNodelay(http.findParam("tcp_nodelay").getValue());
        monitorNginxHttp.setMonitorNginxHttpSendfile(http.findParam("sendfile").getValue());
        monitorNginxHttp.setMonitorNginxHttpCharset(http.findParam("charset").getValue());
        monitorNginxHttp.setMonitorNginxHttpIgnoreInvalidHeaders(http.findParam("ignore_invalid_headers").getValue());
        monitorNginxHttp.setMonitorNginxHttpInclude(http.findParam("include").getValue());
        monitorNginxHttp.setMonitorNginxHttpProxyCachePath(http.findParam("proxy_cache_path").getValue());
        monitorNginxHttp.setMonitorNginxHttpLimitReqZone(http.findParam("limit_req_zone").getValue());
        monitorNginxHttp.setMonitorNginxHttpServerNameInRedirect(http.findParam("server_name_in_redirect").getValue());
        monitorNginxHttp.setMonitorNginxHttpSslPreferServerCiphers(http.findParam("ssl_prefer_server_ciphers").getValue());
        monitorNginxHttp.setMonitorNginxHttpSslCertificateKey(http.findParam("ssl_certificate_key").getValue());
        monitorNginxHttp.setMonitorNginxHttpSslCertificate(http.findParam("ssl_certificate").getValue());
        monitorNginxHttp.setMonitorNginxHttpSslProtocols(http.findParam("ssl_protocols").getValue());
        monitorNginxHttp.setMonitorNginxHttpSslCiphers(http.findParam("ssl_ciphers").getValue());
        monitorNginxHttp.setMonitorNginxHttpSslSessionTimeout(http.findParam("ssl_session_timeout").getValue());

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

        String listen = serverBlock.findParam("listen").getValue();
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
        monitorNginxHttpServer.setMonitorNginxHttpServerName(serverBlock.findParam("server_name").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerCharset(serverBlock.findParam("charset").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerAccessLog(serverBlock.findParam("access_log").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerReturn(serverBlock.findParam("return").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerSsl(isSsl ? "ssl" : "");
        monitorNginxHttpServer.setMonitorNginxHttpServerUdp(serverBlock.findParam("listen").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerErrorPage(serverBlock.findParam("error_page").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerSslCertificate(serverBlock.findParam("ssl_certificate").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerSslCertificateKey(serverBlock.findParam("ssl_certificate_key").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerSslProtocols(serverBlock.findParam("ssl_protocols").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerSslCiphers(serverBlock.findParam("ssl_ciphers").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerSslPreferServerCiphers(serverBlock.findParam("ssl_prefer_server_ciphers").getValue());
        monitorNginxHttpServer.setMonitorNginxHttpServerSslSessionTimeout(serverBlock.findParam("ssl_session_timeout").getValue());
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
        String location = locationBlock.findParam("location").getValue();
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
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationRoot(locationBlock.findParam("root").getValue());
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationIndex(locationBlock.findParam("index").getValue());
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationClientBodyBufferSize(Converter.convertIfNecessary(locationBlock.findParam("client_body_buffer_size").getValue(), Double.class));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationLimitReq(locationBlock.findParam("limit_req").getValue());
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyCache(locationBlock.findParam("proxy_cache").getValue());
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyCacheValid(locationBlock.findParam("proxy_cache_valid").getValue());
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyCacheMethods(locationBlock.findParam("proxy_cache_methods").getValue());
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyConnectTimeout(Integer.valueOf(locationBlock.findParam("proxy_connect_timeout").getValue()));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxySendTimeout(Integer.valueOf(locationBlock.findParam("proxy_send_timeout").getValue()));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyReadTimeout(Integer.valueOf(locationBlock.findParam("proxy_read_timeout").getValue()));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyConnectTimeout(Integer.valueOf(locationBlock.findParam("proxy_connect_timeout").getValue()));
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationProxyPass(locationBlock.findParam("proxy_pass").getValue());
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationTryFiles(locationBlock.findParam("try_files").getValue());
        monitorNginxHttpLocation.setMonitorNginxHttpServerLocationAlias(locationBlock.findParam("alias").getValue());
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
        monitorNginxEvent.setMonitorNginxEventWorkerConnections(Converter.createInteger(events.findParam("worker_connections").getValue()));
        monitorNginxEvent.setMonitorNginxEventMultiAccept(events.findParam("multi_accept").getValue());
        monitorNginxEvent.setMonitorNginxEventAcceptMutex(events.findParam("accept_mutex").getValue());
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
