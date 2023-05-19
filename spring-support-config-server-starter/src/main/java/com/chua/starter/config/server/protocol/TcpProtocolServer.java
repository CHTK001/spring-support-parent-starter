package com.chua.starter.config.server.protocol;//package com.chua.starter.config.server.protocol;
//
//import com.chua.common.support.result.ReturnResult;
//import com.chua.common.support.spi.ServiceProvider;
//import com.chua.common.support.spi.Spi;
//import com.chua.common.support.utils.MapUtils;
//import com.chua.starter.config.server.cammand.CommandProvider;
//import com.chua.starter.config.server.entity.NotifyConfig;
//import com.chua.starter.config.server.manager.ConfigurationManager;
//import com.chua.starter.config.server.pojo.TConfigurationCenterInfo;
//import com.chua.starter.config.server.properties.ConfigServerProperties;
//import com.google.common.base.Strings;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.domain.Page;
//
//import javax.annotation.Resource;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.atomic.AtomicBoolean;
//
///**
// * tcp
// * @author CH
// * @since 2022/8/3 9:28
// */
//@Slf4j
//@Spi("tcp")
//public class TcpProtocolServer implements ProtocolServer{
//    private Vertx vertx;
//    private NetServer netServer;
//    @Resource
//    private ConfigServerProperties configServerProperties;
//
//    @Override
//    public String[] named() {
//        return new String[]{"tcp"};
//    }
//
//
//    @Override
//    public void destroy() throws Exception {
//        netServer.close();
//        vertx.close();
//    }
//
//    @Override
//    public void afterPropertiesSet() throws Exception {
//        this.vertx = Vertx.vertx();
//        this.netServer = vertx.createNetServer();
//        AtomicBoolean isFinal = new AtomicBoolean(false);
//        StringBuilder stringBuilder = new StringBuilder();
//        this.netServer.connectHandler(event -> event.handler(buffer -> {
//            String data = buffer.toString(StandardCharsets.UTF_8);
//            if(data.endsWith("}")) {
//                isFinal.set(true);
//                data = stringBuilder.append(data).toString();
//                stringBuilder.delete(0, stringBuilder.length());
//                Map<String, Object> objectMap = null;
//                if(data.startsWith("}{")) {
//                    data = data.substring(1);
//                }
//                try {
//                    objectMap = JsonUtils.toMapStringObject(data);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                String command = MapUtils.getString(objectMap, "command");
//                ServiceProvider<CommandProvider> serviceProvider = ServiceProvider.of(CommandProvider.class);
//                CommandProvider commandProvider = serviceProvider.getExtension(command);
//                if(null == commandProvider) {
//                    event.write(ReturnResult.illegal(null, "命令不存在").toString());
//                    return;
//                }
//
//                event.write(JsonUtils.toJson(commandProvider.command(MapUtils.getString(objectMap, "binder"), MapUtils.getString(objectMap, "data"), configServerProperties, null)));
//            }
//
//            if(data.startsWith("{")) {
//                isFinal.set(false);
//                if(stringBuilder.length() != 0) {
//                    stringBuilder.delete(0, stringBuilder.length() - 1);
//                }
//            }
//            stringBuilder.append(data);
//
//        }));
//        ConfigServerProperties.NetProperties netConfig = configServerProperties.getNetConfig();
//        try {
//            if(Strings.isNullOrEmpty(netConfig.getIp())) {
//                this.netServer.listen(netConfig.getPort());
//                return;
//            }
//
//            this.netServer.listen(netConfig.getPort(), netConfig.getIp());
//        } finally {
//            log.info(">>>>>>> 配置中心启动[Tcp:({})]", netConfig.getPort());
//        }
//    }
//
//
//    @Override
//    public void notifyConfig(List<NotifyConfig> notifyConfig, ProtocolServer protocolServer) {
//        ConfigurationManager configurationManager = ServiceProvider.of(ConfigurationManager.class).getExtension(configServerProperties.getConfigManager());
//        configurationManager.notifyConfig(notifyConfig, this);
//    }
//
//    @Override
//    public void notifyConfig(Integer configId, String configValue, Integer disable, ProtocolServer protocolServer) {
//        ConfigurationManager configurationManager = ServiceProvider.of(ConfigurationManager.class).getExtension(configServerProperties.getConfigManager());
//        configurationManager.notifyConfig(configId, configValue, disable, this);
//    }
//
//    @Override
//    public Page<TConfigurationCenterInfo> findAll(Integer page, Integer pageSize, String profile) {
//        ConfigurationManager configurationManager = ServiceProvider.of(ConfigurationManager.class).getExtension(configServerProperties.getConfigManager());
//        return configurationManager.findAll(page, pageSize, profile);
//    }
//
//    @Override
//    public void distributeUpdate(List<Integer> configId, String configItem) {
//        ConfigurationManager configurationManager = ServiceProvider.of(ConfigurationManager.class).getExtension(configServerProperties.getConfigManager());
//        configurationManager.distributeUpdate(configId, configItem);
//    }
//
//    @Override
//    public void notifyClient(NotifyConfig config, String keyValue) {
//        Vertx vertx = Vertx.vertx();
//        NetSocket netSocket = null;
//        try {
//            netSocket = vertx.createNetClient()
//                    .connect(Integer.parseInt(config.getBinderPort()), config.getBinderIp())
//                    .onSuccess(netSocket1 -> {
//                        netSocket1.write(keyValue);
//                        netSocket1.close();
//                    }).result();
//        } catch (Exception ignored) {
//        }
//
//        if(null != netSocket) {
//            netSocket.close();
//        }
//        try {
//            vertx.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//}
