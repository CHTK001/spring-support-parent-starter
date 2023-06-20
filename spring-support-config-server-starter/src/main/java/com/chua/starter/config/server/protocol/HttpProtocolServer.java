package com.chua.starter.config.server.protocol;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.server.cammand.CommandProvider;
import com.chua.starter.config.server.entity.NotifyConfig;
import com.chua.starter.config.server.manager.ConfigurationManager;
import com.chua.starter.config.server.pojo.ConfigurationCenterInfoRepository;
import com.chua.starter.config.server.pojo.ConfigurationDistributeInfoRepository;
import com.chua.starter.config.server.pojo.TConfigurationCenterInfo;
import com.chua.starter.config.server.properties.ConfigServerProperties;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.CookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * http
 * @author CH
 * @since 2022/8/1 8:59
 */
@Slf4j
@RestController
@RequestMapping("/config")
@Spi("http")
public class HttpProtocolServer implements ProtocolServer, ProtocolResolver{

    @Autowired(required = false)
    private ConfigurationCenterInfoRepository configurationCenterInfoRepository;

    @Autowired(required = false)
    private ConfigurationDistributeInfoRepository configurationDistributeInfoRepository;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ConfigServerProperties configServerProperties;

    private static final String LISTENER = "/config/listener";
    private ConfigurationManager configurationManager;

    @Override
    public String[] named() {
        return new String[]{"http"};
    }

    /**
     * 注册地址
     * @param command 命令
     * @param data 数据
     * @param request 请求
     * @param response 响应
     * @return 结果
     */
    @PostMapping("/{command}")
    public ReturnResult<String> command(
            @PathVariable("command") String command,
            @RequestParam("data") String data,
            @RequestParam("binder") String binder,
            HttpServletRequest request, HttpServletResponse response) {
        ServiceProvider<CommandProvider> serviceProvider = ServiceProvider.of(CommandProvider.class);
        CommandProvider commandProvider = serviceProvider.getExtension(command);
        if(null == commandProvider) {
            return ReturnResult.illegal(null, "命令不存在");
        }

        return commandProvider.command(binder, data, configServerProperties, request);
    }

    @Override
    public void destroy() throws Exception {

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info(">>>>>>> 配置中心启动[Http]");
        this.configurationManager = ServiceProvider.of(ConfigurationManager.class).getExtension(configServerProperties.getConfigManager());
        try {
            applicationContext.getAutowireCapableBeanFactory().autowireBean(configurationManager);
            configurationManager.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void notifyConfig(List<NotifyConfig> notifyConfig, ProtocolServer protocolServer) {
        configurationManager.notifyConfig(notifyConfig, this);
    }


    @Override
    public void notifyConfig(Integer configId, String configValue, Integer disable, ProtocolServer protocolServer) {
        configurationManager.notifyConfig(configId, configValue, disable, this);
    }

    @Override
    public Page<TConfigurationCenterInfo> findAll(Integer page, Integer pageSize, String profile) {
        return configurationManager.findAll(page, pageSize, profile);
    }

    @Override
    public void distributeUpdate(List<Integer> configId, String configItem) {
        configurationManager.distributeUpdate(configId, configItem);
    }

    @Override
    public void notifyClient(NotifyConfig config, String keyValue) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String element = headerNames.nextElement();
            if("content-type".equalsIgnoreCase(element)) {
                continue;
            }

            if("Content-Length".equalsIgnoreCase(element)) {
                continue;
            }
            headers.put(element, request.getHeader(element));
        }
        Cookie[] cookies = request.getCookies();
        if(null != cookies) {
            CookieStore cookieStore = new org.apache.http.impl.client.BasicCookieStore();
            for (Cookie cookie : cookies) {
                cookieStore.addCookie(new BasicClientCookie(cookie.getName(), cookie.getValue()));
            }
            Unirest.config().httpClient(org.apache.http.impl.client.HttpClients.custom().setDefaultCookieStore(cookieStore).build());
        }

        try {
            Unirest.post("http://" + config.getBinderIp() + ":" + config.getBinderPort() + LISTENER)
                    .headers(headers)
                    .field("data", keyValue)
                    .asString();
        } catch (UnirestException e) {
            e.printStackTrace();
        }
    }
}
