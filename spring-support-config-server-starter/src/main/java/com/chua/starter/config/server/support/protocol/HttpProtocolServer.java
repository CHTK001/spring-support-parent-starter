package com.chua.starter.config.server.support.protocol;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.constant.ConfigConstant;
import com.chua.starter.config.server.support.command.CommandProvider;
import com.chua.starter.config.server.support.config.NotifyConfig;
import com.chua.starter.config.server.support.manager.DataManager;
import com.chua.starter.config.server.support.properties.ConfigServerProperties;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * http
 *
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/config")
@Spi("http")
public class HttpProtocolServer implements ProtocolServer, ApplicationContextAware {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ConfigServerProperties configServerProperties;

    private static final String LISTENER = "/config/listener";

    private final ExecutorService executorService = ThreadUtils.newProcessorThreadExecutor();
    private DataManager dataManager;

    @Override
    public String[] named() {
        return new String[]{"http"};
    }

    /**
     * 注册地址
     *
     * @param command         命令
     * @param data            数据
     * @param dataType        数据类型
     * @param applicationName 当前数据的应用
     * @param subscribe       订阅那些数据
     * @param request         请求
     * @param response        响应
     * @return 结果
     */
    @PostMapping("/{command}")
    public ReturnResult<String> command(
            @PathVariable("command") String command,
            @RequestParam(ConfigConstant.APPLICATION_DATA_TYPE) String dataType,
            @RequestParam(ConfigConstant.APPLICATION_DATA) String data,
            @RequestParam(ConfigConstant.APPLICATION_NAME) String applicationName,
            @RequestParam(ConfigConstant.APPLICATION_SUBSCRIBE) String subscribe,
            HttpServletRequest request, HttpServletResponse response) {
        ServiceProvider<CommandProvider> serviceProvider = ServiceProvider.of(CommandProvider.class);
        CommandProvider commandProvider = serviceProvider.getExtension(command);
        if (null == commandProvider) {
            return ReturnResult.illegal(null, "命令不存在");
        }

        return commandProvider.command(subscribe, applicationName, data, dataType, dataManager, request);
    }

    @Override
    public void destroy() throws Exception {
        ThreadUtils.shutdownNow(executorService);
    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }

    @Override
    public void notifyClient(NotifyConfig config, String keyValue) {
        Map<String, String> headers = new HashMap<>();
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
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.configServerProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(ConfigServerProperties.PRE, ConfigServerProperties.class);
        log.info(">>>>>>> 配置中心启动[Http]");
        this.dataManager = ServiceProvider.of(DataManager.class)
                .getExtension(configServerProperties.getDataManager());
        try {
            applicationContext.getAutowireCapableBeanFactory().autowireBean(dataManager);
            dataManager.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}