package com.chua.starter.config.protocol;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.function.NamedThreadFactory;
import com.chua.common.support.json.Json;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.constant.Constant;
import com.chua.starter.config.annotation.ConfigValueAnnotationBeanPostProcessor;
import com.chua.starter.config.properties.ConfigProperties;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.context.ApplicationContext;
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

/**
 * 协议
 *
 * @author CH
 * @since 2022/7/30 12:07
 */
@Slf4j
public abstract class AbstractProtocolProvider implements ProtocolProvider, Constant {

    private ConfigurableEnvironment environment;

    private String ck;

    private final AtomicBoolean run = new AtomicBoolean(false);
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


    @Override
    public List<PropertiesPropertySource> register(ConfigurableEnvironment environment) {
        configProperties = Binder.get(environment).bindOrCreate(ConfigProperties.PRE, ConfigProperties.class);
        if(!configProperties.isOpen()) {
            return Collections.emptyList();
        }

        if(Strings.isNullOrEmpty(configProperties.getConfigAppName())) {
            throw new IllegalArgumentException("config-app-name不能为空");
        }

        this.environment = environment;
        this.reconnectLimit = configProperties.getReconnectLimit();
        String configName = configProperties.getConfigName();
        if (Strings.isNullOrEmpty(configName)) {
            log.warn("plugin.configuration.config-name 注冊中心配置名稱不能为空");
            return Collections.emptyList();
        }
        if (Strings.isNullOrEmpty(configProperties.getConfigAddress())) {
            log.warn("plugin.configuration.config-address 注冊的中心地址不能为空");
            return Collections.emptyList();
        }

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
            reconnect();
            log.info("注册中心连接异常");
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
     * @param req 请求
     */
    private void renderData(Map<String, Object> req) {
        if (configProperties.isOpenRegister()) {
            MutablePropertySources propertySources = environment.getPropertySources();
            Map<String, Map<String, Object>> rs = new HashMap<>(propertySources.size());
            propertySources.iterator().forEachRemaining(it -> {
                if (
                        "systemProperties".equalsIgnoreCase(it.getName()) ||
                                "systemEnvironment".equalsIgnoreCase(it.getName())
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
     * @param req 请求
     */
    private void renderBase(Map<String, Object> req) {
        req.put("binder-client", Optional.ofNullable(configProperties.getBindIp()).orElse(getHostIp()));
        if(!"http".equalsIgnoreCase(configProperties.getProtocol()) && null != configProperties.getNetPort()) {
            req.put("binder-port", configProperties.getNetPort());
        } else {
            req.put("binder-port", Optional.ofNullable(configProperties.getBindPort()).orElse(environment.resolvePlaceholders(environment.getProperty("server.port"))));
        }

        req.put("binder-profile", environment.getProperty("spring.profiles.active", "dev"));
        req.put("binder-key", (ck = UUID.randomUUID().toString()));
        req.put("binder-name", configProperties.getConfigName());
        req.put("binder-app-name", configProperties.getConfigAppName());
        req.put("binder-auto-refresh", configProperties.isAutoRefresh());
    }

    /**
     * 渲染数据
     * @param req 请求
     */
    private void renderI18n(Map<String, Object> req) {
        String i18n = configProperties.getI18n();
        if(Strings.isNullOrEmpty(i18n)) {
            return;
        }


        Map<String, String> transfer = new HashMap<>();
        req.put("transfer", transfer);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            org.springframework.core.io.Resource[] resources = resolver.getResources("classpath:config/config-message-" + i18n + ".properties");
            for (org.springframework.core.io.Resource resource : resources) {
                try (InputStreamReader isr = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)){
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
     * @param properties 字段
     * @param transfer 请求
     */
    private void renderI18nEnv(Properties properties, Map<String, String> transfer) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            transfer.put(entry.getKey().toString(), entry.getValue().toString());
        }
    }

    /**
     * 发送信息
     * @param encode 数据
     * @return 响应
     */
    protected abstract String send(String encode);

    /**
     * 获取该主机上所有网卡的ip
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
                        if(ip.getHostName().equalsIgnoreCase(Inet4Address.getLocalHost().getHostName())) {
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
     * 重连
     */
    private void reconnect() {
        int andIncrement = count.getAndIncrement();
        if (reconnectLimit != -1 || reconnectLimit < andIncrement) {
            connect.set(true);
            return;
        }

        reconnect.execute(() -> {
            while (run.get() && !connect.get()) {
                ThreadUtils.sleepSecondsQuietly(5);
                log.warn("开始重连注册中心");
                register(environment);
            }
        });
    }

    /**
     * 心跳
     */
    private void beat() {
        String configName = configProperties.getConfigName();
        beat.execute(() -> {
            while (run.get()) {
                try {
                    ThreadUtils.sleepSecondsQuietly(60);
                    HttpResponse<String> response = Unirest.post(named() + "://" + configProperties.getConfigAddress().concat("/config/beat"))
                            .field("data", "")
                            .field("binder", configProperties.getConfigName())
                            .asString();
                    if (null != response && response.getStatus() == 200) {
                        if (log.isDebugEnabled()) {
                            log.debug("{}心跳包正常", configName);
                        }
                    }
                } catch (Throwable e) {
                    log.warn(e.getMessage());
                }
                if (log.isWarnEnabled()) {
                    log.warn("{}心跳包异常开始重连", configName);
                    reconnect();
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
        run.set(false);
        Map<String, Object> rs = new HashMap<>(3);

        Codec provider = ServiceProvider.of(Codec.class).getExtension(configProperties.getEncrypt());
        rs.put("binder-client", Optional.ofNullable(configProperties.getBindIp()).orElse(getHostIp()));
        if(null != configProperties.getNetPort()) {
            rs.put("binder-port", configProperties.getNetPort());
        } else {
            rs.put("binder-port", Optional.ofNullable(configProperties.getBindPort()).orElse(environment.resolvePlaceholders(environment.getProperty("server.port"))));
        }
        rs.put("binder-key", (ck = UUID.randomUUID().toString()));
        rs.put("binder-name", configProperties.getConfigName());
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
     * @param encode 数据
     * @return 注销
     */
    protected abstract String sendDestroy(String encode);

    @Override
    public void afterPropertiesSet() throws Exception {
    }
}
