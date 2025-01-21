package com.chua.report.server.starter.ngxin;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.FileUtils;
import com.chua.report.server.starter.entity.*;
import com.chua.report.server.starter.mapper.*;
import com.chua.socketio.support.MsgStep;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.github.odiszapc.nginxparser.NgxBlock;
import com.github.odiszapc.nginxparser.NgxComment;
import com.github.odiszapc.nginxparser.NgxConfig;
import com.github.odiszapc.nginxparser.NgxParam;
import com.google.common.base.Joiner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

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

        List<MonitorNginxHttpServer> monitorNginxHttpServers = monitorNginxHttpServerMapper.selectList(Wrappers.<MonitorNginxHttpServer>lambdaQuery()
                .eq(MonitorNginxHttpServer::getMonitorNginxHttpId, monitorNginxHttp.getMonitorNginxHttpId()));
        Map<Integer, List<MonitorNginxHttpServer>> httpServer =
                monitorNginxHttpServers.stream().collect(Collectors.groupingBy(MonitorNginxHttpServer::getMonitorNginxHttpId));

        socketSessionTemplate.send(eventName, new MsgStep("获取Server", createIndex(1)));
        List<Integer> httpServerIds = httpServer.values().stream()
                .flatMap(List::stream)
                .map(MonitorNginxHttpServer::getMonitorNginxHttpServerId).toList();
        Map<Integer, List<MonitorNginxHttpServerLocation>> location = monitorNginxHttpServerLocationMapper.selectList(Wrappers.<MonitorNginxHttpServerLocation>lambdaQuery()
                        .in(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerId, httpServerIds))
                .stream().collect(Collectors.groupingBy(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerId));

        Map<Integer, List<MonitorNginxHttpServerLocationHeader>> locationHeader = monitorNginxHttpServerLocationHeaderMapper.selectList(Wrappers.<MonitorNginxHttpServerLocationHeader>lambdaQuery()
                        .in(MonitorNginxHttpServerLocationHeader::getMonitorNginxHttpServerLocationId, location.values().stream()
                                .flatMap(List::stream)
                                .map(MonitorNginxHttpServerLocation::getMonitorNginxHttpServerLocationId).toList()))
                .stream().collect(Collectors.groupingBy(MonitorNginxHttpServerLocationHeader::getMonitorNginxHttpServerLocationId));

        List<MonitorNginxUpstream> monitorNginxUpstreams = monitorNginxUpstreamMapper.selectList(Wrappers.<MonitorNginxUpstream>lambdaQuery()
                .in(MonitorNginxUpstream::getMonitorNginxServerId, httpServerIds));
        socketSessionTemplate.send(eventName, new MsgStep("获取配置", createIndex(1)));
        MonitorNginxConfig monitorNginxConfig = baseMapper.selectById(nginxConfigId);

        NginxAnalyze analyze;
        if (monitorNginxConfig.getMonitorNginxConfigMultipart() == 1) {
            analyze = new MultiNginxAnalyze(monitorNginxEvent, monitorNginxHttp, monitorNginxHttpServers, monitorNginxUpstreams, location, locationHeader);
        } else {
            analyze = new SingleNginxAnalyze(monitorNginxEvent, monitorNginxHttp, monitorNginxHttpServers, monitorNginxUpstreams, location, locationHeader);
        }
        return analyze.analyze(monitorNginxConfig);
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
            NgxBlock nginxConfig = createNginxConfig(monitorNginxConfig);
            createNginxEvent(monitorNginxEvent, nginxConfig);
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
            NgxBlock nginxConfig = createNginxConfig(monitorNginxConfig);
            createNginxEvent(monitorNginxEvent, nginxConfig);
            NgxBlock ngxHttp = createNginxHttp(monitorNginxHttp);
            return null;
        }



    }
    private static NgxBlock createNginxHttp(MonitorNginxHttp monitorNginxHttp) {
        NgxBlock ngxHttp = new NgxBlock();
        ngxHttp.addEntry(new NgxComment("#HTTP 模块配置"));
        ngxHttp.addValue("http");
        createParam(ngxHttp, "server_names_hash_bucket_size", "设置服务器名称的哈希表大小", monitorNginxHttp.getMonitorNginxHttpServerNamesHashBucketSize());
        createParam(ngxHttp, "server_tokens", "设置nginx返回给用户的服务器标识", monitorNginxHttp.getMonitorNginxHttpServerTokens());
        createParam(ngxHttp, "include", "包含其它配置文件", monitorNginxHttp.getMonitorNginxHttpInclude());
        createParam(ngxHttp, "error_log", "错误日志", monitorNginxHttp.getMonitorNginxHttpErrorLog());
        createParam(ngxHttp, "access_log", "访问日志", monitorNginxHttp.getMonitorNginxHttpAccessLog());
        createParam(ngxHttp, "log_format", "自定义日志格式", monitorNginxHttp.getMonitorNginxHttpLogFormat());
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
        nginxConfig.addEntry(new NgxComment("#events模块：影响nginx服务器与用户的网络连接"));
        nginxConfig.addEntry(events);
        createParam(events, "worker_connections", "设置单个工作进程最大并发连接数", monitorNginxEvent.getMonitorNginxEventWorkerConnections());
        createParam(events, "multi_accept", "允许一个进程接受多个连接", monitorNginxEvent.getMonitorNginxEventMultiAccept());
        createParam(events, "use", "使用 epoll 模型（Linux 系统推荐）", monitorNginxEvent.getMonitorNginxEventUse());
        createParam(events, "accept_mutex", "在某些情况下，可以设置为 on 来允许多个工作进程同时监听相同的端口。默认情况下，它是关闭的，以避免多个进程间的端口竞争", monitorNginxEvent.getMonitorNginxEventAcceptMutex());
    }

    private static NgxBlock createNginxConfig(MonitorNginxConfig monitorNginxConfig) {
        NgxConfig ngxConfig = new NgxConfig();
        createParam(ngxConfig, "worker_processes", monitorNginxConfig.getMonitorNginxConfigWorkerProcesses());
        createParam(ngxConfig, "pid", FileUtils.normalize(monitorNginxConfig.getMonitorNginxConfigPid(), "/nginx.pid"));
        createParam(ngxConfig, "error_log", monitorNginxConfig.getMonitorNginxConfigErrorLog());
        return ngxConfig;
    }

    private static void createParam(NgxBlock ngxBlock, String name, Object... value) {
        createParam(ngxBlock, name, null, value);

    }

    private static void createParam(NgxBlock ngxBlock, String name, String comment, Object... value) {
        if (null == value) {
            return;
        }
        if (value.length == 1 && value[0] == null) {
            return;
        }
        if (!StringUtils.isBlank(comment)) {
            NgxComment ngxComment = new NgxComment("#" + comment);
            ngxBlock.addEntry(ngxComment);
        }
        NgxParam ngxParam = new NgxParam();
        ngxParam.addValue(Joiner.on(" ").join(name, value));
        ngxBlock.addEntry(ngxParam);
    }

    interface NginxAnalyze {
        Boolean analyze(MonitorNginxConfig monitorNginxConfig);
    }
}
