package com.chua.starter.config.protocol;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.function.NamedThreadFactory;
import com.chua.common.support.json.Json;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.*;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.config.annotation.ConfigValueAnnotationBeanPostProcessor;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.properties.ConfigProperties;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 协议
 *
 * @author CH
 * @since 2022/7/30 12:07
 */
@Slf4j
public abstract class AbstractProtocolProvider implements ProtocolProvider, ApplicationContextAware, Constant, AutoCloseable {

    private ConfigurableEnvironment environment;

    private String ck;

    protected int port;
    protected String host;

    protected String applicationName;
    protected String subscribeName;

    private final AtomicBoolean run = new AtomicBoolean(true);
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicBoolean connect = new AtomicBoolean(false);

    protected ConfigValueAnnotationBeanPostProcessor configValueAnnotationBeanPostProcessor;

    private final ExecutorService beat = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("config-beat"));

    private final ExecutorService reconnect = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("config-reconnect"));

    @Resource
    private ApplicationContext applicationContext;

    public static final String ORDER = "config.order";
    private Integer reconnectLimit;
    protected ConfigProperties configProperties;
    protected String dataType;


    @Override
    public List<PropertiesPropertySource> register(ConfigurableEnvironment environment) {
        if (!configProperties.isOpen()) {
            return Collections.emptyList();
        }

        this.applicationName = environment.getProperty("spring.application.name");
        if (Strings.isNullOrEmpty(applicationName)) {
            log.warn("spring.application.name不能为空, 当前不订阅数据");
            return Collections.emptyList();

        }

        if (Strings.isNullOrEmpty(configProperties.getConfigAddress())) {
            log.warn("plugin.configuration.config-address 注冊的中心地址不能为空, 当前不订阅数据");
            return Collections.emptyList();
        }

        List<ConfigProperties.Subscribe> subscribe = configProperties.getSubscribe();
        Set<ConfigProperties.Subscribe> collect = subscribe.stream().filter(it -> ConfigConstant.CONFIG.equals(it.getDataType())).collect(Collectors.toSet());
//        if(collect.isEmpty()) {
//            log.warn("未订阅数据");
//            return Collections.emptyList();
//        }


        ConfigProperties.Subscribe subscribe1 = CollectionUtils.findFirst(collect);
        this.subscribeName = null == subscribe1 ? applicationName: subscribe1.getSubscribe();
        this.dataType = null == subscribe1 ? ConfigConstant.CONFIG : subscribe1.getDataType();
        this.environment = environment;
        this.reconnectLimit = configProperties.getReconnectLimit();

        Codec encrypt = ServiceProvider.of(Codec.class).getExtension(configProperties.getEncrypt());
        Map<String, Object> req = new HashMap<>(12);

        renderData(req);
        renderI18n(req);
        renderBase(req);

        //注册配置到配置中心
        String encode = encrypt.encodeHex(Json.toJson(req), StringUtils.defaultString(configProperties.getKey(), DEFAULT_SER));

        String body = null;
        try {
            body = send(encode);
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }

        run.set(true);
        if (Strings.isNullOrEmpty(body)) {
            log.info("注册中心连接异常");
            connect.set(false);
            return Collections.emptyList();
        }

        connect.set(true);
        log.info("注册中心连接成功");

        //注册成功获取可以读取的配置文件
        try {
            Map<String, Object> stringObjectMap = Json.toMapStringObject(body);
            String decode = encrypt.decodeHex(MapUtils.getString(stringObjectMap, "data"), ck);
            //beat();
            List<PropertiesPropertySource> rs = new ArrayList<>();
            Map<String, Object> stringObjectMap1 = Json.toMapStringObject(decode);
            for (Map.Entry<String, Object> entry : stringObjectMap1.entrySet()) {
                String key = entry.getKey();
                if (!key.endsWith(".data")) {
                    continue;
                }

                Map<String, Object> value = (Map<String, Object>) entry.getValue();
                value.forEach((k, v) -> {
                    PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource(k, MapUtils.asProp(v));
                    rs.add(propertiesPropertySource);
                });
            }
            rs.sort((o1, o2) -> {
                int intValue1 = MapUtils.getIntValue(o1.getSource(), ORDER, 0);
                int intValue2 = MapUtils.getIntValue(o2.getSource(), ORDER, 0);
                return intValue2 - intValue1;
            });
            return rs;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 渲染数据
     *
     * @param req 请求
     */
    private void renderData(Map<String, Object> req) {
        if (configProperties.isOpenRegister()) {
            MutablePropertySources propertySources = environment.getPropertySources();
            Map<String, Map<String, Object>> rs = new HashMap<>(propertySources.size());
            propertySources.iterator().forEachRemaining(it -> {
                if (
                        !it.getName().contains("application")
                ) {
                    return;
                }
                Object source = it.getSource();
                if (source instanceof Map) {
                    Map<String, Object> stringObjectMap = rs.computeIfAbsent(it.getName(), new Function<String, Map<String, Object>>() {
                        @Override
                        public @Nullable Map<String, Object> apply(@Nullable String input) {
                            Map<String, Object> rs = new HashMap<>();
                            return rs;
                        }
                    });

                    ((Map<?, ?>) source).forEach((k, v) -> {
                        Object value = null;
                        if (v instanceof OriginTrackedValue) {
                            value = ((OriginTrackedValue) v).getValue();
                        }
                        stringObjectMap.put(k.toString(), value);
                    });
                }
            });

            req.put("data", rs);
        }
    }

    /**
     * 渲染基础数据
     *
     * @param req 请求
     */
    private void renderBase(Map<String, Object> req) {
        req.put(ConfigConstant.APPLICATION_HOST, getHost());
        req.put(ConfigConstant.APPLICATION_PORT, getPort());

        req.put(ConfigConstant.PROFILE, environment.getProperty("spring.profiles.active", "dev"));
        req.put(ConfigConstant.KEY, (ck = UUID.randomUUID().toString()));
        req.put(ConfigConstant.REFRESH, configProperties.isAutoRefresh());
    }

    protected int getPort() {
        if (this.port > 0) {
            return this.port;
        }

        if (configProperties.getBindPort() > -1) {
            return (this.port = configProperties.getBindPort());
        }
        return (this.port = NetUtils.getAvailablePort());
    }

    protected String getHost() {
        if (StringUtils.isNotBlank(this.host)) {
            return this.host;
        }

        if (StringUtils.isNotBlank(configProperties.getBindIp())) {
            return (this.host = configProperties.getBindIp());
        }
        return (this.host = NetUtils.getLocalIpv4());
    }

    /**
     * 渲染数据
     *
     * @param req 请求
     */
    private void renderI18n(Map<String, Object> req) {
        String i18n = configProperties.getI18n();
        if (Strings.isNullOrEmpty(i18n)) {
            return;
        }


        Map<String, String> transfer = new HashMap<>();
        req.put("transfer", transfer);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            org.springframework.core.io.Resource[] resources = resolver.getResources("classpath:config/config-message-" + i18n + ".properties");
            for (org.springframework.core.io.Resource resource : resources) {
                try (InputStreamReader isr = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    Properties properties = new Properties();
                    properties.load(isr);

                    renderI18nEnv(properties, transfer);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * 渲染描述
     *
     * @param properties 字段
     * @param transfer   请求
     */
    private void renderI18nEnv(Properties properties, Map<String, String> transfer) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            transfer.put(entry.getKey().toString(), entry.getValue().toString());
        }
    }

    /**
     * 发送信息
     *
     * @param encode 数据
     * @return 响应
     */
    protected abstract String send(String encode);

    /**
     * 获取该主机上所有网卡的ip
     *
     * @return ip
     */
    public static String getHostIp() {
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = (NetworkInterface) allNetInterfaces.nextElement();
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ip = (InetAddress) addresses.nextElement();
                    if (ip instanceof Inet4Address
                            && !ip.isLoopbackAddress() //loopback地址即本机地址，IPv4的loopback范围是127.0.0.0 ~ 127.255.255.255
                            && !ip.getHostAddress().contains(":")) {
                        if (ip.getHostName().equalsIgnoreCase(Inet4Address.getLocalHost().getHostName())) {
                            return ip.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 心跳
     */
    private void beat() {
        String configName = applicationName;
        beat.execute(() -> {
            while (run.get()) {
                try {
                    ThreadUtils.sleepSecondsQuietly(60);
                    HttpResponse<String> response = Unirest.post(named() + "://" + configProperties.getConfigAddress().concat("/config/beat"))
                            .field(ConfigConstant.APPLICATION_DATA, "")
                            .field(ConfigConstant.APPLICATION_DATA_TYPE, ConfigConstant.CONFIG)
                            .field(ConfigConstant.APPLICATION_NAME, applicationName)
                            .asString();
                    if (null != response && response.getStatus() == 200) {
                        if (log.isDebugEnabled()) {
                            log.debug("{}心跳包正常", configName);
                        }
                        continue;
                    }
                } catch (Throwable e) {
                    log.warn(e.getMessage());
                }
                if (log.isWarnEnabled()) {
                    log.warn("{}心跳包异常开始重连", configName);
                    connect.set(false);
                }
            }
        });
    }


    @Override
    public void register(ConfigValueAnnotationBeanPostProcessor configValueAnnotationBeanPostProcessor) {
        this.configValueAnnotationBeanPostProcessor = configValueAnnotationBeanPostProcessor;
    }

    @Override
    public void destroy() throws Exception {
        close();
        connect.set(true);
        run.set(false);
        Map<String, Object> rs = new HashMap<>(3);

        Codec provider = ServiceProvider.of(Codec.class).getExtension(configProperties.getEncrypt());
        rs.put(ConfigConstant.APPLICATION_HOST, Optional.ofNullable(configProperties.getBindIp()).orElse(getHostIp()));
        rs.put(ConfigConstant.APPLICATION_PORT, getPort());
        rs.put(ConfigConstant.KEY, (ck = UUID.randomUUID().toString()));
        rs.put(ConfigConstant.APPLICATION_NAME, applicationName);
        String encode = provider.encodeHex(Json.toJson(rs), StringUtils.defaultString(configProperties.getKey(), DEFAULT_SER));
        String body = null;
        try {
            body = sendDestroy(encode);
            ThreadUtils.sleepSecondsQuietly(1);
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }

        beat.shutdownNow();
        reconnect.shutdownNow();
        if (Strings.isNullOrEmpty(body)) {
            log.error("注册中心注销失败");
            return;
        }
        log.error("注册中心注销成功");
    }

    /**
     * 注销
     *
     * @param encode 数据
     * @return 注销
     */
    protected abstract String sendDestroy(String encode);

    /**
     * 启动
     */
    protected abstract void start();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        configProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ConfigProperties.PRE, ConfigProperties.class);
        start();
        reconnect();
        beat();
    }

    protected void reconnect() {
        reconnect.execute(() -> {
            while (run.get()) {
                ThreadUtils.sleepSecondsQuietly(15);
                if( !connect.get()) {
                    log.warn("开始重连注册中心");
                    this.register(environment);
                }
            }
        });
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }
}
