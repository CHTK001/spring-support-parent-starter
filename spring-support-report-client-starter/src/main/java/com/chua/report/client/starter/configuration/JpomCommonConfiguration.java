package com.chua.report.client.starter.configuration;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.chua.common.support.net.NetAddress;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.report.client.starter.jpom.agent.ClientJpomApplication;
import com.chua.report.client.starter.jpom.agent.configuration.ServerConfig;
import com.chua.report.client.starter.jpom.agent.system.AgentStartInit;
import com.chua.report.client.starter.jpom.common.common.ServerOpenApi;
import com.chua.report.client.starter.jpom.common.common.i18n.I18nMessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author CH
 * @since 2025/6/3 8:52
 */
@Slf4j
@ComponentScan(value = {"com.chua.report.client.starter.jpom.common"})
@Import({ClientJpomApplication.class})
public class JpomCommonConfiguration implements EnvironmentAware, DisposableBean, InitializingBean {
    private static final ScheduledExecutorService PUSH_SERVER = ThreadUtils.newScheduledThreadPoolExecutor(1);
    private static final ScheduledExecutorService REMOTE_SERVER = ThreadUtils.newScheduledThreadPoolExecutor(1);
    private static final AtomicBoolean CONNECT = new AtomicBoolean(false);
    private static final AtomicBoolean SERVER_STATUS = new AtomicBoolean(false);
    private Environment environment;
    @Autowired
    private ApplicationArguments applicationArguments;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startChildApp() throws IOException {
        if (ClassUtils.isPresent("com.chua.starter.monitor.jpom.JpomApplication", JpomCommonConfiguration.class.getClassLoader())) {
            return;
        }
        log.info("当前是否存在: com.chua.starter.monitor.jpom.JpomApplication => {}", ClassUtils.isPresent("com.chua.starter.monitor.jpom.JpomApplication", JpomCommonConfiguration.class.getClassLoader()));
        log.info("开始上报节点");
        List<String> from = new ArrayList<>(applicationArguments.getNonOptionArgs());
        int i = from.indexOf(ServerOpenApi.PUSH_NODE_KEY);
        if (i == ArrayUtil.INDEX_NOT_FOUND) {
            String property = Binder.get(environment).bindOrCreate("plugin.maintenance.server.config", ServerConfig.class).getPushUrl();
            if (StringUtils.isNotEmpty(property)) {
                from.add(ServerOpenApi.PUSH_NODE_KEY);
                from.add(property);
                i = from.indexOf(ServerOpenApi.PUSH_NODE_KEY);
            }
        }
        if (i == ArrayUtil.INDEX_NOT_FOUND) {
            return;
        }
        String arg = from.get(i + 1);
        if (StrUtil.isEmpty(arg)) {
            log.error(I18nMessageUtil.get("i18n.auto_push_url_not_found.88a7"));
            return;
        }
        regiserHeartbeat(arg);
        authPushToServer(arg);
    }

    private void regiserHeartbeat(String arg) {
        NetAddress netAddress = NetAddress.of(arg);
        REMOTE_SERVER.scheduleAtFixedRate(() -> {
            if (!NetUtils.isConnectable(netAddress.getHost(), netAddress.getPort())) {
                CONNECT.set(false);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    private void authPushToServer(String arg) {
        try {
            AgentStartInit autoRegSeverNode = SpringUtil.getBean(AgentStartInit.class);
            autoRegSeverNode.autoPushToServer(arg);
            CONNECT.set(true);
        } catch (Exception e) {
            log.error(I18nMessageUtil.get("i18n.push_registration_to_server_failed.5949"), arg);
            PUSH_SERVER.scheduleAtFixedRate(() -> {
                if (CONNECT.get()) {
                    return;
                }
                try {
                    AgentStartInit autoRegSeverNode = SpringUtil.getBean(AgentStartInit.class);
                    autoRegSeverNode.autoPushToServer(arg);
                    CONNECT.set(true);
                } catch (Exception ex) {
                    log.error(I18nMessageUtil.get("i18n.push_registration_to_server_failed.5949"), arg);
                }
            }, 0, 10, TimeUnit.SECONDS);
        }
    }

    @Override
    public void destroy() throws Exception {
        ThreadUtils.shutdownNow(PUSH_SERVER);
        ThreadUtils.shutdownNow(REMOTE_SERVER);
    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }
}
