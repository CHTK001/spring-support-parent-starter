package com.chua.starter.config.entity;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.NetUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.properties.ConfigProperties;
import com.google.common.base.Strings;
import lombok.Data;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 基础信息
 *
 * @author CH
 */
@Data
public class PluginMeta {

    private ApplicationContext applicationContext;
    private String ck;

    protected int port;
    protected String host;

    protected String applicationName;
    public static final String ORDER = "config.order";
    private Integer reconnectLimit;
    protected ConfigProperties configProperties;
    private final ConfigurableEnvironment environment;
    protected String dataType;

    public PluginMeta(ConfigProperties configProperties, ConfigurableEnvironment environment) {
        this.configProperties = configProperties;
        this.environment = environment;
        this.applicationName = environment.getProperty("spring.application.name");
    }

    public int getPort() {
        if (this.port > 0) {
            return this.port;
        }

        if (configProperties.getBindPort() > -1) {
            return (this.port = configProperties.getBindPort());
        }
        return (this.port = NetUtils.getAvailablePort());
    }

    public String getHost() {
        if (StringUtils.isNotBlank(this.host)) {
            return this.host;
        }

        if (StringUtils.isNotBlank(configProperties.getBindIp())) {
            return (this.host = configProperties.getBindIp());
        }
        return (this.host = NetUtils.getLocalIpv4());
    }

    public boolean isLimit() {
        if (!configProperties.isOpen()) {
            return true;
        }

        Boolean property = environment.getProperty(ConfigProperties.PRE + ".is-open", Boolean.class);
        if (null != property && !property) {
            return true;
        }
        if (Strings.isNullOrEmpty(applicationName)) {
            return true;

        }

        if (Strings.isNullOrEmpty(configProperties.getConfigAddress())) {
            return true;
        }


        if (ClassUtils.isPresent("com.chua.starter.config.server.support.configuration.ConfigServerConfiguration")) {
            return true;
        }
        return false;
    }

    /**
     * 获取该主机上所有网卡的ip
     *
     * @return ip
     */
    public static String getHostIp() {
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress ip = addresses.nextElement();
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

    public String getSubscribeName(String config) {
        List<ConfigProperties.Subscribe> subscribe = configProperties.getSubscribe();
        Set<ConfigProperties.Subscribe> collect = subscribe.stream().filter(it -> config.equals(it.getDataType())).collect(Collectors.toSet());

        ConfigProperties.Subscribe subscribe1 = CollectionUtils.findFirst(collect);
        if (ConfigConstant.CONFIG.equalsIgnoreCase(config)) {
            return null == subscribe1 ? applicationName : subscribe1.getSubscribe();
        }

        return null == subscribe1 ? null : subscribe1.getSubscribe();
    }

    public String getDateType(String config) {
        List<ConfigProperties.Subscribe> subscribe = configProperties.getSubscribe();
        Set<ConfigProperties.Subscribe> collect = subscribe.stream().filter(it -> config.equals(it.getDataType())).collect(Collectors.toSet());

        ConfigProperties.Subscribe subscribe1 = CollectionUtils.findFirst(collect);
        if (ConfigConstant.CONFIG.equalsIgnoreCase(config)) {
            return null == subscribe1 ? ConfigConstant.CONFIG : subscribe1.getDataType();
        }

        return null == subscribe1 ? null : subscribe1.getDataType();
    }

    /**
     * 獲取令牌
     *
     * @return 令牌
     */
    public Object createKey() {
        return (ck = UUID.randomUUID().toString());
    }
}
