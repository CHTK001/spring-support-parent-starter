package com.chua.starter.config.server.protocol;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.server.cammand.CommandProvider;
import com.chua.starter.config.server.entity.NotifyConfig;
import com.chua.starter.config.server.manager.ConfigurationManager;
import com.chua.starter.config.server.pojo.ConfigurationCenterInfo;
import com.chua.starter.config.server.pojo.ConfigurationCenterInfoRepository;
import com.chua.starter.config.server.pojo.ConfigurationDistributeInfoRepository;
import com.chua.starter.config.server.properties.ConfigServerProperties;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * http
 * @author CH
 * @since 2022/8/1 8:59
 */
@Slf4j
@RestController
@RequestMapping("/config")
@Spi("http")
public class HttpProtocolServer implements ProtocolServer, ProtocolResolver, ApplicationContextAware {

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

    private final ExecutorService executorService = ThreadUtils.newProcessorThreadExecutor();

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
            @RequestParam("subscribe") String subscribe, //订阅的配置
            HttpServletRequest request, HttpServletResponse response) {
        ServiceProvider<CommandProvider> serviceProvider = ServiceProvider.of(CommandProvider.class);
        CommandProvider commandProvider = serviceProvider.getExtension(command);
        if(null == commandProvider) {
            return ReturnResult.illegal(null, "命令不存在");
        }

        return commandProvider.command(subscribe, binder, data, configServerProperties, request);
    }

    @Override
    public void destroy() throws Exception {
        ThreadUtils.shutdownNow(executorService);
    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }

    @Override
    public void notifyConfig(List<NotifyConfig> notifyConfig, ProtocolServer protocolServer) {
        executorService.execute(() -> {
            configurationManager.notifyConfig(notifyConfig, this);
        });
    }

    @Override
    public void notifyConfig(ConfigurationCenterInfo configurationCenterInfo, ProtocolServer protocolServer) {
        executorService.execute(() -> {
            configurationManager.notifyConfig(configurationCenterInfo, this);
        });
    }


    @Override
    public void notifyConfig(Integer configId, String configValue, Integer disable, ProtocolServer protocolServer) {
        executorService.execute(() -> {
            configurationManager.notifyConfig(configId, configValue, disable, this);
        });
    }

    @Override
    public Page<ConfigurationCenterInfo> findAll(Integer page, Integer pageSize, String profile) {
        return configurationManager.findAll(page, pageSize, profile);
    }

    @Override
    public void distributeUpdate(List<Integer> configId, String configItem) {
        configurationManager.distributeUpdate(configId, configItem);
    }

    @Override
    public void notifyClient(NotifyConfig config, String keyValue) {
//        if(null ==  RequestContextHolder.getRequestAttributes()) {
//            return;
//        }
//        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        Map<String, String> headers = new HashMap<>();
//        Enumeration<String> headerNames = request.getHeaderNames();
//        while (headerNames.hasMoreElements()) {
//            String element = headerNames.nextElement();
//            if("content-type".equalsIgnoreCase(element)) {
//                continue;
//            }
//
//            if("Content-Length".equalsIgnoreCase(element)) {
//                continue;
//            }
//            headers.put(element, request.getHeader(element));
//        }
//        Cookie[] cookies = request.getCookies();
//        if(null != cookies) {
//            CookieStore cookieStore = new org.apache.http.impl.client.BasicCookieStore();
//            for (Cookie cookie : cookies) {
//                cookieStore.addCookie(new BasicClientCookie(cookie.getName(), cookie.getValue()));
//            }
//            Unirest.config().httpClient(org.apache.http.impl.client.HttpClients.custom().setDefaultCookieStore(cookieStore).build());
//        }

        try {
            Unirest.post("http://" + config.getBinderIp() + ":" + config.getBinderPort() + LISTENER)
                    .headers(headers)
                    .field("data", keyValue)
                    .asString();
        } catch (UnirestException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void save(ConfigurationCenterInfo configValue) {
        configurationCenterInfoRepository.save(configValue);
    }

    @Override
    public void deleteById(String configId) {
        configurationCenterInfoRepository.deleteById(Integer.valueOf(configId));
    }

    @Override
    public Set<String> profile() {
        return configurationCenterInfoRepository.profile();
    }

    @Override
    public Set<String> applications() {
        return configurationCenterInfoRepository.applications();
    }

    @Override
    public void updateById(ConfigurationCenterInfo configValue) {
        if (null != configValue.getDisable()) {
            configurationCenterInfoRepository.update(configValue.getConfigValue(), configValue.getDisable(), configValue.getConfigId());
        } else {
            configurationCenterInfoRepository.update(configValue.getConfigName(), configValue.getConfigValue(), configValue.getConfigProfile(), configValue.getConfigItem());
        }
        notifyConfig(configValue, this);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.configServerProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ConfigServerProperties.PRE, ConfigServerProperties.class);
        log.info(">>>>>>> 配置中心启动[Http]");
        this.configurationManager = ServiceProvider.of(ConfigurationManager.class)
                .getExtension(configServerProperties.getConfigManager());
        try {
            applicationContext.getAutowireCapableBeanFactory().autowireBean(configurationManager);
            configurationManager.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
